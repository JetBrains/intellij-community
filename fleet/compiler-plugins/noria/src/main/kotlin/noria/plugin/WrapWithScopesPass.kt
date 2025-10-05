package noria.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.codegen.isExtensionFunctionType
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.superTypes

fun IrType.isNoriaReceiver(): Boolean {
  return classOrNull?.isClassWithFqName(NoriaRuntime.NoriaContextFqn.toUnsafe()) == true
    || classOrNull?.superTypes()?.any { it.isNoriaReceiver() } == true
}

object WrapWithScopesPass : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    //log("processing module ${moduleFragment.dump()}")
    moduleFragment.transform(object : IrElementTransformerVoidWithContext() {

      var caller: IrFunction? = null
      private var nextId = 0
      private var optOutAnnotationCount = 0

      override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val oldCaller = caller
        caller = declaration
        val hasOptOutAnnotation = declaration.hasOptOutFromInnerScopesAnnotation
        optOutAnnotationCount += hasOptOutAnnotation.toInt()
        val declarationPrime = if (oldCaller == null) {
          val oldNextId = nextId
          nextId = 0
          val declarationPrime = super.visitFunctionNew(declaration)
          nextId = oldNextId
          declarationPrime
        }
        else {
          super.visitFunctionNew(declaration)
        }
        optOutAnnotationCount -= hasOptOutAnnotation.toInt()
        caller = oldCaller
        return declarationPrime
      }

      override fun visitCall(expression: IrCall): IrExpression {
        return super.visitCall(expression).let { expressionPrime ->
          if (optOutAnnotationCount == 0) {
            (expressionPrime as? IrCall)?.let { call ->
              call.withExtensionReceiver { extensionReceiver, newCallBuilder ->
                caller?.let { caller ->
                  DeclarationIrBuilder(generatorContext = IrGeneratorContextBase(pluginContext.irBuiltIns),
                                       symbol = caller.symbol)
                    .irBlock(resultType = call.type) {
                      val receiverTmp = irTemporary(extensionReceiver)

                      +irCall(rtEnterScope)
                        .apply {
                          arguments[0] = irGet(receiverTmp)
                          arguments[1] = irInt(++nextId)
                        }

                      val resultTmp = newCallBuilder(receiverTmp)
                      +irCall(rtExitScope)
                        .apply {
                          arguments[0] = irGet(receiverTmp)
                        }

                      +irGet(resultTmp)
                    }
                } ?: call
              }
            } ?: expressionPrime
          }
          else {
            expression
          }
        }
      }

      private val rtEnterScope by lazy {
        funByName("RTenterScope", NoriaRuntime.NoriaRuntimeFqn, pluginContext, moduleFragment)
      }

      private val rtExitScope by lazy {
        funByName("RTexitScope", NoriaRuntime.NoriaRuntimeFqn, pluginContext, moduleFragment)
      }
    }, null)
    //log("after: ${moduleFragment.dump()}")
  }
}

private fun IrCall.withExtensionReceiver(block: (IrExpression?, IrStatementsBuilder<*>.(IrVariable) -> IrVariable) -> IrExpression): IrExpression {
  val extensionParameter = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
  val firstValueArgument = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
  return when {
    symbol.owner.hasOptOutFromOuterScopesAnnotation -> this
    extensionParameter?.type?.isNoriaReceiver() == true -> {
      arguments[extensionParameter]?.let { extensionReceiver ->
        block(extensionReceiver) { receiverTmp ->
          irTemporary(
            irCall(this@withExtensionReceiver, this@withExtensionReceiver.symbol)
              .apply {
                this.arguments[extensionParameter] = irGet(receiverTmp)
              })
        }
      } ?: this
    }
    firstValueArgument != null && isExtensionFunctionOnNoriaContext(firstValueArgument) -> {
      val extensionReceiver = arguments[firstValueArgument]
      block(extensionReceiver) { receiverTmp ->
        irTemporary(
          irCall(this@withExtensionReceiver, this@withExtensionReceiver.symbol)
            .apply {
              arguments[firstValueArgument] = irGet(receiverTmp)
            })
      }
    }
    else -> this
  }
}

val IrAnnotationContainer.hasOptOutFromOuterScopesAnnotation: Boolean
  get() = getAnnotation(NoriaRuntime.OptOutFromOuterScopes) != null

val IrAnnotationContainer.hasOptOutFromInnerScopesAnnotation: Boolean
  get() = getAnnotation(NoriaRuntime.OptOutFromInnerScopes) != null

private fun IrCall.isExtensionFunctionOnNoriaContext(firstValueParameter: IrValueParameter): Boolean {
  return (dispatchReceiver?.type
            ?.takeIf { it.isExtensionFunctionType } // function is an extension function
            ?.let { it as? IrSimpleType }?.arguments?.firstOrNull()?.typeOrNull?.isNoriaReceiver() == true // extension receiver is NoriaContext
          && arguments[firstValueParameter]?.type?.isNoriaReceiver() == true) // the first argument (extension receiver) is NoriaContext
}

private fun Boolean.toInt() = if (this) 1 else 0