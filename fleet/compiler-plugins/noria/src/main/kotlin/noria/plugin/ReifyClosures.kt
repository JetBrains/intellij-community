package noria.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isClassWithFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.superTypes

fun IrType.isClosureReceiver(): Boolean {
  return classOrNull?.isClassWithFqName(NoriaRuntime.ClosureContextFqn.toUnsafe()) == true
         || classOrNull?.superTypes()?.any { it.isClosureReceiver() } == true
}


private class CaptureCollector {
  val captures = mutableSetOf<IrValueDeclaration>()
  fun recordCapture(local: IrValueDeclaration) {
    captures.add(local)
  }
}

private abstract class DeclarationContext {
  abstract val composable: Boolean
  abstract val symbol: IrSymbol
  abstract val functionContext: FunctionContext?
  abstract fun declareLocal(local: IrValueDeclaration?)
  abstract fun recordCapture(local: IrValueDeclaration?)
  abstract fun pushCollector(collector: CaptureCollector)
  abstract fun popCollector(collector: CaptureCollector)
}

private class SymbolOwnerContext(val declaration: IrSymbolOwner) : DeclarationContext() {
  override val composable get() = false
  override val functionContext: FunctionContext? get() = null
  override val symbol get() = declaration.symbol
  override fun declareLocal(local: IrValueDeclaration?) {}
  override fun recordCapture(local: IrValueDeclaration?) {}
  override fun pushCollector(collector: CaptureCollector) {}
  override fun popCollector(collector: CaptureCollector) {}
}

private class FunctionLocalSymbol(val declaration: IrSymbolOwner,
                                  override val functionContext: FunctionContext) : DeclarationContext() {
  override val composable: Boolean get() = functionContext.composable
  override val symbol: IrSymbol get() = declaration.symbol
  override fun declareLocal(local: IrValueDeclaration?) = functionContext.declareLocal(local)
  override fun recordCapture(local: IrValueDeclaration?) = functionContext.recordCapture(local)
  override fun pushCollector(collector: CaptureCollector) =
    functionContext.pushCollector(collector)
  override fun popCollector(collector: CaptureCollector) =
    functionContext.popCollector(collector)
}

private class FunctionContext(val declaration: IrFunction,
                              override val composable: Boolean) : DeclarationContext() {
  override val symbol get() = declaration.symbol
  override val functionContext: FunctionContext get() = this
  val locals = mutableSetOf<IrValueDeclaration>()
  val captures = mutableSetOf<IrValueDeclaration>()
  var collectors = mutableListOf<CaptureCollector>()

  init {
    declaration.parameters.forEach {
      declareLocal(it)
    }
  }

  override fun declareLocal(local: IrValueDeclaration?) {
    if (local != null) {
      locals.add(local)
    }
  }

  override fun recordCapture(local: IrValueDeclaration?) {
    if (local != null && collectors.isNotEmpty() && locals.contains(local)) {
      for (collector in collectors) {
        collector.recordCapture(local)
      }
    }
    if (local != null && declaration.isLocal && !locals.contains(local)) {
      captures.add(local)
    }
  }

  override fun pushCollector(collector: CaptureCollector) {
    collectors.add(collector)
  }

  override fun popCollector(collector: CaptureCollector) {
    require(collectors.lastOrNull() == collector)
    collectors.removeAt(collectors.size - 1)
  }
}

private class ClassContext(val declaration: IrClass) : DeclarationContext() {
  override val composable: Boolean = false
  override val symbol get() = declaration.symbol
  override val functionContext: FunctionContext? = null
  val thisParam: IrValueDeclaration? = declaration.thisReceiver!!
  var collectors = mutableListOf<CaptureCollector>()
  override fun declareLocal(local: IrValueDeclaration?) {}
  override fun recordCapture(local: IrValueDeclaration?) {
    if (local != null && collectors.isNotEmpty() && local == thisParam) {
      for (collector in collectors) {
        collector.recordCapture(local)
      }
    }
  }

  override fun pushCollector(collector: CaptureCollector) {
    collectors.add(collector)
  }

  override fun popCollector(collector: CaptureCollector) {
    require(collectors.lastOrNull() == collector)
    collectors.removeAt(collectors.size - 1)
  }
}

object ReifyClosures : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val declarationContextStack = mutableListOf<DeclarationContext>()
    val inlinedFunctions = IrInlineReferenceLocator.scan(pluginContext, moduleFragment)
    moduleFragment.transform(object : IrElementTransformerVoidWithContext() {
      val currentFunctionContext: FunctionContext? get() = declarationContextStack.peek()?.functionContext

      override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        if (declaration is IrFunction)
          return super.visitDeclaration(declaration)
        val functionContext = currentFunctionContext
        if (functionContext != null) {
          declarationContextStack.push(FunctionLocalSymbol(declaration, functionContext))
        }
        else {
          declarationContextStack.push(SymbolOwnerContext(declaration))
        }
        val result = super.visitDeclaration(declaration)
        declarationContextStack.pop()
        return result
      }

      override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val composable = declaration.parameters
          .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.isNoriaReceiver() == true
        val context = FunctionContext(declaration, composable)
        declarationContextStack.push(context)
        val result = super.visitFunctionNew(declaration)
        declarationContextStack.pop()
        return result
      }

      override fun visitClassNew(declaration: IrClass): IrStatement {
        val context = ClassContext(declaration)
        declarationContextStack.push(context)
        val result = super.visitClassNew(declaration)
        declarationContextStack.pop()
        return result
      }

      override fun visitVariable(declaration: IrVariable): IrStatement {
        if (!declaration.isVar) declarationContextStack.peek()?.declareLocal(declaration)
        return super.visitVariable(declaration)
      }

      override fun visitValueAccess(expression: IrValueAccessExpression): IrExpression {
        if (!expression.type.isClosureReceiver()) {
          declarationContextStack.forEach {
            it.recordCapture(expression.symbol.owner)
          }
        }
        return super.visitValueAccess(expression)
      }

      fun <T> collectClosure(block: () -> T): Pair<T, Set<IrValueDeclaration>> {
        val collector = CaptureCollector()
        for (declarationContext in declarationContextStack) {
          declarationContext.pushCollector(collector)
        }
        val res = block()
        for (declarationContext in declarationContextStack) {
          declarationContext.popCollector(collector)
        }
        return res to collector.captures
      }

      fun IrFunction.isInlinedLambda(): Boolean {
        for (element in inlinedFunctions) {
          if (element.argument.function == this) return true
        }
        return false
      }

      override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
        val functionContext = currentFunctionContext
        return when {
          functionContext == null -> super.visitFunctionExpression(expression)
          expression.function.parameters
            .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.isClosureReceiver() != true -> super.visitFunctionExpression(expression)
          expression.function.isInlinedLambda() -> super.visitFunctionExpression(expression)
          else -> {
            val (result, captures) = collectClosure { super.visitFunctionExpression(expression) }
            when (result) {
              is IrFunctionExpression -> {
                with(DeclarationIrBuilder(generatorContext = pluginContext,
                                          symbol = functionContext.symbol,
                                          startOffset = result.startOffset,
                                          endOffset = result.endOffset)) {

                  val closureFun = if (expression.function.isSuspend) rtSuspendClosure else rtClosure
                  irCall(closureFun).apply {
                    arguments[0] = IrVarargImpl(startOffset = UNDEFINED_OFFSET,
                                                     endOffset = UNDEFINED_OFFSET,
                                                     type = pluginContext.irBuiltIns.arrayClass.typeWith(pluginContext.irBuiltIns.anyNType),
                                                     varargElementType = pluginContext.irBuiltIns.anyType,
                                                     elements = captures.map {
                                                       irGet(it)
                                                })
                    arguments[1] = result
                  }
                }
              }
              else -> result
            }
          }
        }
      }

      private val rtSuspendClosure by lazy {
        funByName("RTsuspendClosure", NoriaRuntime.NoriaRuntimeFqn, pluginContext, moduleFragment)
      }

      private val rtClosure by lazy {
        funByName("RTclosure", NoriaRuntime.NoriaRuntimeFqn, pluginContext, moduleFragment)
      }
    }, null)
  }
}