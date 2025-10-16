package noria.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.irCall

object WrapWithScopesPass : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transform(object : IrElementTransformerVoidWithContext() {

      var caller: IrFunction? = null
      private var nextId = 0
      private var explicitGroupsAnnotationCount = 0

      override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val oldCaller = caller
        caller = declaration
        val hasExplicitGroupsAnnotation = declaration.hasExplicitGroupsComposableAnnotation
        if (hasExplicitGroupsAnnotation) {
          explicitGroupsAnnotationCount++
        }
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
        if (hasExplicitGroupsAnnotation) {
          explicitGroupsAnnotationCount--
        }
        caller = oldCaller
        return declarationPrime
      }

      override fun visitCall(expression: IrCall): IrExpression {
        return super.visitCall(expression).let { expressionPrime ->
          if (explicitGroupsAnnotationCount == 0) {
            (expressionPrime as? IrCall)?.let { call ->
              val calledFunction = call.symbol.owner
              val shouldWrapWithScope = if (calledFunction.name.identifierOrNullIfSpecial == "invoke") {
                calledFunction
                  .parameters
                  .any {
                    it is IrValueParameter &&
                    (it.kind == IrParameterKind.DispatchReceiver || it.kind == IrParameterKind.ExtensionReceiver) &&
                    call.arguments[it.indexInParameters]!!.type.shouldBeWrappedWithScopes
                  } ||
                calledFunction.shouldBeWrappedWithScopes
              }
              else {
                calledFunction.shouldBeWrappedWithScopes
              }
              if (shouldWrapWithScope) {
                caller?.let { caller ->
                  val noriaContextParameter = calledFunction.parameters.find {
                    it.name == NoriaParamTransformer.NoriaContextParameterName
                  }
                  checkNotNull(noriaContextParameter) {
                    "Function ${calledFunction.name} has @Composable annotation, but doesn't have a NoriaContext parameter:\n${expression.dumpKotlinLike()}"
                  }

                  val noriaContextArgument = checkNotNull(call.arguments[noriaContextParameter.indexInParameters]) {
                    "Function ${calledFunction.name} has @Composable annotation, but NoriaContext parameter is missing from the call"
                  }

                  return DeclarationIrBuilder(
                    generatorContext = IrGeneratorContextBase(pluginContext.irBuiltIns),
                    symbol = caller.symbol
                  ).irBlock(resultType = call.type) {
                    val contextTmp = irTemporary(noriaContextArgument)

                    +irCall(rtEnterScope).apply {
                      arguments[0] = irGet(contextTmp)
                      arguments[1] = irInt(++nextId)
                    }

                    val resultTmp = irTemporary(
                      irCall(call, call.symbol).apply {
                        arguments[noriaContextParameter.indexInParameters] = irGet(contextTmp)
                      }) //irTemporary(call)

                    +irCall(rtExitScope).apply {
                      arguments[0] = irGet(contextTmp)
                    }

                    +irGet(resultTmp)
                  }

                }
              }
              else {
                call
              }
            } ?: expressionPrime
          }
          else {
            expressionPrime
          }
        }
      }

      override fun visitFileNew(declaration: IrFile): IrFile {
        return includeFileNameInExceptionTrace(declaration) {
          super.visitFileNew(declaration)
        }
      }

      private val rtEnterScope by lazy {
        funByName("RTenterScope", NoriaRuntime.NoriaRuntimeFqn, pluginContext, moduleFragment)
      }

      private val rtExitScope by lazy {
        funByName("RTexitScope", NoriaRuntime.NoriaRuntimeFqn, pluginContext, moduleFragment)
      }
    }, null)
  }
}

private val IrAnnotationContainer.shouldBeWrappedWithScopes: Boolean
  get() = hasComposableAnnotation && !hasReadonlyComposableAnnotation

val IrAnnotationContainer.hasExplicitGroupsComposableAnnotation: Boolean
  get() = hasAnnotation(NoriaRuntime.ExplicitGroupsComposable)

val IrAnnotationContainer.hasReadonlyComposableAnnotation: Boolean
  get() = hasAnnotation(NoriaRuntime.ReadOnlyComposable)
