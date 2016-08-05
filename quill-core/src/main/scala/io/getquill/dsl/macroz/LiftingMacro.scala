package io.getquill.dsl.macroz
import io.getquill.util.Messages._
import scala.reflect.macros.whitebox.{ Context => MacroContext }
import scala.reflect.macros.whitebox.{ Context => MacroContext }
import scala.reflect.macros.whitebox.{ Context => MacroContext }

trait LiftingMacro {
  val c: MacroContext
  import c.universe._

  def lift[T](v: Tree)(implicit t: WeakTypeTag[T]): Tree =
    inferEncoder(t.tpe) match {
      case Some(enc) => q"${c.prefix}.lift($v, $enc)"
      case None =>
        t.tpe.baseType(c.symbolOf[Product]) match {
          case NoType => failEncoder(t.tpe)
          case _      => q"${c.prefix}.liftCaseClass($v)"
        }
    }

  protected def inferRequiredEncoder(tpe: Type) =
    inferEncoder(tpe) match {
      case None      => failEncoder(tpe)
      case Some(enc) => enc
    }

  private def inferEncoder(tpe: Type) =
    regularEncoder(tpe)
      .orElse(anyValEncoder(tpe))

  private def failEncoder(t: Type) =
    c.fail(s"Can't find encoder for type '$t'")

  private def regularEncoder(tpe: Type): Option[Tree] =
    c.typecheck(
      q"implicitly[${c.prefix}.Encoder[$tpe]]",
      silent = true) match {
        case EmptyTree => None
        case tree      => Some(tree)
      }

  private def anyValEncoder(tpe: Type): Option[Tree] =
    tpe.baseType(c.symbolOf[AnyVal]) match {
      case NoType => None
      case _ =>
        caseClassConstructor(tpe).map(_.paramLists.flatten).collect {
          case param :: Nil =>
            regularEncoder(param.typeSignature) match {
              case Some(encoder) =>
                c.typecheck(q"""
                  ${c.prefix}.mappedEncoder(
                    ${c.prefix}.MappedEncoding((v: $tpe) => v.${param.name.toTermName}), 
                    $encoder
                  )
                """)
              case None =>
                c.fail(s"Can't encode the '$tpe' because there's no encoder for '$param'.")
            }
        }
    }

  private def caseClassConstructor(t: Type) =
    t.members.collect {
      case m: MethodSymbol if (m.isPrimaryConstructor) => m
    }.headOption
}
