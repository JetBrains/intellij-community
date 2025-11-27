@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package noria.plugin

import org.jetbrains.kotlin.backend.common.ModuleLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isFunctionOrKFunction
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.isSuspendFunctionOrKFunction
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.utils.exceptions.rethrowIntellijPlatformExceptionIfNeeded

val IrAnnotationContainer.hasComposableAnnotation: Boolean
  get() = hasAnnotation(NoriaRuntime.ComposableClassId)

val IrAnnotationContainer.hasValueLambdaAnnotation: Boolean
  get() = hasAnnotation(NoriaRuntime.ValueLambdaFqName)

private class CaptureCollector {
  val captures = mutableSetOf<IrValueDeclaration>()
  fun recordCapture(local: IrValueDeclaration) {
    captures.add(local)
  }
}

private abstract class DeclarationContext {
  val localDeclarationCaptures = mutableMapOf<IrSymbolOwner, Set<IrValueDeclaration>>()
  fun recordLocalDeclaration(local: DeclarationContext) {
    localDeclarationCaptures[local.declaration] = local.captures
  }

  abstract val composable: Boolean
  abstract val declaration: IrSymbolOwner
  abstract val captures: Set<IrValueDeclaration>
  abstract fun declareLocal(local: IrValueDeclaration)
  abstract fun recordCapture(local: IrValueDeclaration): Boolean
  abstract fun recordCapture(local: IrSymbolOwner)
  abstract fun pushCollector(collector: CaptureCollector)
  abstract fun popCollector(collector: CaptureCollector)
}

private fun List<DeclarationContext>.recordCapture(value: IrValueDeclaration) {
  for (dec in reversed()) {
    val shouldBreak = dec.recordCapture(value)
    if (shouldBreak) break
  }
}

private fun List<DeclarationContext>.recordLocalDeclaration(local: DeclarationContext) {
  for (dec in reversed()) {
    dec.recordLocalDeclaration(local)
  }
}

private fun List<DeclarationContext>.recordLocalCapture(
  local: IrSymbolOwner,
): Set<IrValueDeclaration>? {
  val capturesForLocal = reversed().firstNotNullOfOrNull { it.localDeclarationCaptures[local] }
  if (capturesForLocal != null) {
    capturesForLocal.forEach { recordCapture(it) }
    for (dec in reversed()) {
      dec.recordCapture(local)
      if (dec.localDeclarationCaptures.containsKey(local)) {
        // this is the scope that the class was defined in, so above this we don't need
        // to do anything
        break
      }
    }
  }
  return capturesForLocal
}

private class SymbolOwnerContext(override val declaration: IrSymbolOwner) : DeclarationContext() {
  override val composable get() = false
  override val captures: Set<IrValueDeclaration> get() = emptySet()
  override fun declareLocal(local: IrValueDeclaration) {}
  override fun recordCapture(local: IrValueDeclaration): Boolean {
    return false
  }

  override fun recordCapture(local: IrSymbolOwner) {}
  override fun pushCollector(collector: CaptureCollector) {}
  override fun popCollector(collector: CaptureCollector) {}
}

private class FunctionContext(
  override val declaration: IrFunction,
  override val composable: Boolean,
) : DeclarationContext() {
  val locals = mutableSetOf<IrValueDeclaration>()
  override val captures: MutableSet<IrValueDeclaration> = mutableSetOf()
  var collectors = mutableListOf<CaptureCollector>()

  init {
    declaration.parameters.forEach {
      declareLocal(it)
    }
  }

  override fun declareLocal(local: IrValueDeclaration) {
    locals.add(local)
  }

  override fun recordCapture(local: IrValueDeclaration): Boolean {
    val containsLocal = locals.contains(local)
    if (collectors.isNotEmpty() && containsLocal) {
      for (collector in collectors) {
        collector.recordCapture(local)
      }
    }
    if (declaration.isLocal && !containsLocal) {
      captures.add(local)
    }
    return containsLocal
  }

  override fun recordCapture(local: IrSymbolOwner) {
    localDeclarationCaptures[local]?.let { captures ->
      for (collector in collectors) {
        for (capture in captures) {
          collector.recordCapture(capture)
        }
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

private class AnonymousInitializerContext(override val declaration: IrAnonymousInitializer) : DeclarationContext() {
  override val composable: Boolean = false
  override val captures: MutableSet<IrValueDeclaration> = mutableSetOf()

  val locals = mutableSetOf<IrValueDeclaration>()
  var collectors = mutableListOf<CaptureCollector>()

  override fun declareLocal(local: IrValueDeclaration) {
    locals.add(local)
  }

  override fun recordCapture(local: IrValueDeclaration): Boolean {
    val containsLocal = locals.contains(local)
    if (collectors.isNotEmpty() && containsLocal) {
      for (collector in collectors) {
        collector.recordCapture(local)
      }
    }
    if (!containsLocal) {
      captures.add(local)
    }
    return containsLocal
  }

  override fun recordCapture(local: IrSymbolOwner) {
    localDeclarationCaptures[local]?.let { captures ->
      for (collector in collectors) {
        for (capture in captures) {
          collector.recordCapture(capture)
        }
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

private class ClassContext(override val declaration: IrClass) : DeclarationContext() {
  override val composable: Boolean = false
  override val captures: MutableSet<IrValueDeclaration> = mutableSetOf()
  val thisParam: IrValueDeclaration = declaration.thisReceiver!!
  var collectors = mutableListOf<CaptureCollector>()
  override fun declareLocal(local: IrValueDeclaration) {}
  override fun recordCapture(local: IrValueDeclaration): Boolean {
    val isThis = local == thisParam
    val isConstructorParam = (local.parent as? IrConstructor)?.parent === declaration
    val isClassParam = isThis || isConstructorParam
    if (collectors.isNotEmpty() && isClassParam) {
      for (collector in collectors) {
        collector.recordCapture(local)
      }
    }
    if (declaration.isLocal && !isClassParam) {
      captures.add(local)
    }
    return isClassParam
  }

  override fun recordCapture(local: IrSymbolOwner) {}
  override fun pushCollector(collector: CaptureCollector) {
    collectors.add(collector)
  }

  override fun popCollector(collector: CaptureCollector) {
    require(collectors.lastOrNull() == collector)
    collectors.removeAt(collectors.size - 1)
  }
}

class ComposerLambdaMemoization(
  val context: IrPluginContext,
  val moduleFragment: IrModuleFragment,
) : IrElementTransformerVoid(), ModuleLoweringPass {

  private val declarationContextStack = mutableListOf<DeclarationContext>()

  private var currentFunctionContext: FunctionContext? = null

  private var composableSingletonsClass: IrClass? = null
  private var currentFile: IrFile? = null

  private val usedSingletonLambdaNames = hashSetOf<String>()

  private var inlineLambdaInfo = ComposeInlineLambdaLocator(context)

  private val rtSuspendClosure by lazy {
    funByName(
      name = NoriaRuntime.RTsuspendClosureFqn.shortName().identifier,
      prefix = NoriaRuntime.RTsuspendClosureFqn.parent(),
      pluginContext = context,
      moduleFragment = moduleFragment
    )
  }

  private val rtClosure by lazy {
    funByName(
      name = NoriaRuntime.RTclosureFqn.shortName().identifier,
      prefix = NoriaRuntime.RTclosureFqn.parent(),
      pluginContext = context,
      moduleFragment = moduleFragment
    )
  }

  override fun visitFile(declaration: IrFile): IrFile {
    includeFileNameInExceptionTrace(declaration) {
      val prevFile = currentFile
      val prevClass = composableSingletonsClass
      try {
        currentFile = declaration
        composableSingletonsClass = null
        usedSingletonLambdaNames.clear()
        val file = super.visitFile(declaration)
        // if there were no constants found in the entire file, then we don't need to
        // create this class at all
        val resultingClass = composableSingletonsClass
        if (resultingClass != null && resultingClass.declarations.isNotEmpty()) {
          file.addChild(resultingClass)
        }
        return file
      } finally {
        currentFile = prevFile
        composableSingletonsClass = prevClass
      }
    }
  }

  override fun lower(irModule: IrModuleFragment) {
    inlineLambdaInfo.scan(irModule)
    irModule.transformChildrenVoid(this)
  }

  override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
    // contexts for those declarations are already created
    if (declaration is IrClass || declaration is IrFunction || declaration is IrAnonymousInitializer) {
      return super.visitDeclaration(declaration)
    }

    declarationContextStack.push(SymbolOwnerContext(declaration))
    val result = super.visitDeclaration(declaration)
    declarationContextStack.pop()
    return result
  }

  private val IrFunction.allowsComposableCalls: Boolean
    get() = hasComposableAnnotation ||
            inlineLambdaInfo.preservesComposableScope(this) &&
            currentFunctionContext?.composable == true

  override fun visitFunction(declaration: IrFunction): IrStatement {
    val composable = declaration.allowsComposableCalls
    val context = FunctionContext(declaration, composable)
    if (declaration.isLocal) {
      declarationContextStack.recordLocalDeclaration(context)
    }
    declarationContextStack.push(context)
    val oldFunctionContext = currentFunctionContext
    currentFunctionContext = context

    val result = super.visitFunction(declaration)

    currentFunctionContext = oldFunctionContext
    declarationContextStack.pop()
    return result
  }

  override fun visitClass(declaration: IrClass): IrStatement {
    val context = ClassContext(declaration)
    if (declaration.isLocal) {
      declarationContextStack.recordLocalDeclaration(context)
    }
    declarationContextStack.push(context)
    val oldFunctionContext = currentFunctionContext
    currentFunctionContext = null

    val result = super.visitClass(declaration)

    currentFunctionContext = oldFunctionContext
    declarationContextStack.pop()
    return result
  }

  override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer): IrStatement {
    val context = AnonymousInitializerContext(declaration)
    declarationContextStack.recordLocalDeclaration(context)
    declarationContextStack.push(context)
    val result = super.visitAnonymousInitializer(declaration)
    declarationContextStack.pop()
    return result
  }

  override fun visitVariable(declaration: IrVariable): IrStatement {
    currentFunctionContext?.declareLocal(declaration)
    return super.visitVariable(declaration)
  }

  override fun visitValueAccess(expression: IrValueAccessExpression): IrExpression {
    declarationContextStack.recordCapture(expression.symbol.owner)
    return super.visitValueAccess(expression)
  }

  private fun visitNonComposableFunctionExpression(
    expression: IrFunctionExpression,
  ): IrExpression {
    val functionContext = currentFunctionContext
                          ?: return super.visitFunctionExpression(expression)

    if (
      // Only memoize non-composable lambdas in a composable context or when force wrap annotation is present
      (!functionContext.composable && !expression.function.hasValueLambdaAnnotation) ||
      // Don't memoize inlined lambdas
      inlineLambdaInfo.isInlineLambda(expression.function)
    ) {
      return super.visitFunctionExpression(expression)
    }

    // Record capture variables for this scope
    val collector = CaptureCollector()
    startCollector(collector)
    // Wrap composable functions expressions or memoize non-composable function expressions
    val result = super.visitFunctionExpression(expression)
    stopCollector(collector)

    // If the ancestor converted this then return
    val functionExpression = result as? IrFunctionExpression ?: return result

    if (collector.captures.any { it.isInlinedLambda() }) {
      return functionExpression
    }

    return wrapFunctionExpression(
      functionContext.declaration.symbol,
      functionExpression,
      collector
    )
  }

  override fun visitCall(expression: IrCall): IrExpression {
    val fn = expression.symbol.owner
    if (fn.visibility == DescriptorVisibilities.LOCAL) {
      declarationContextStack.recordLocalCapture(fn)
    }
    return super.visitCall(expression)
  }

  override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
    val fn = expression.symbol.owner
    val cls = fn.parent as? IrClass
    if (cls != null && fn.isLocal) {
      declarationContextStack.recordLocalCapture(cls)
    }
    return super.visitConstructorCall(expression)
  }

  private fun visitComposableFunctionExpression(
    expression: IrFunctionExpression,
    declarationContext: DeclarationContext,
  ): IrExpression {
    val collector = CaptureCollector()
    startCollector(collector)
    val result = super.visitFunctionExpression(expression)
    stopCollector(collector)

    // If the ancestor converted this then return
    val functionExpression = result as? IrFunctionExpression ?: return result

    // Do not wrap target of an inline function
    if (inlineLambdaInfo.isInlineLambda(expression.function)) {
      return functionExpression
    }

    return wrapFunctionExpression(declarationContext.declaration.symbol, functionExpression, collector)
  }

  override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
    val declarationContext = declarationContextStack.peek()
                             ?: return super.visitFunctionExpression(expression)
    return if (expression.function.allowsComposableCalls) {
      visitComposableFunctionExpression(expression, declarationContext)
    } else {
      visitNonComposableFunctionExpression(expression)
    }
  }

  private fun startCollector(collector: CaptureCollector) {
    for (declarationContext in declarationContextStack) {
      declarationContext.pushCollector(collector)
    }
  }

  private fun stopCollector(collector: CaptureCollector) {
    for (declarationContext in declarationContextStack) {
      declarationContext.popCollector(collector)
    }
  }

  private fun wrapFunctionExpression(
    symbol: IrSymbol,
    expression: IrFunctionExpression,
    collector: CaptureCollector,
  ): IrCall {
    val captures = collector.captures
    return with(DeclarationIrBuilder(generatorContext = context,
                              symbol = symbol,
                              startOffset = expression.startOffset,
                              endOffset = expression.endOffset)) {

      val closureFun = if (expression.function.isSuspend) rtSuspendClosure else rtClosure
      irCall(closureFun).apply {
        arguments[0] = IrVarargImpl(startOffset = UNDEFINED_OFFSET,
                                    endOffset = UNDEFINED_OFFSET,
                                    type = context.irBuiltIns.arrayClass.typeWith(context.irBuiltIns.anyNType),
                                    varargElementType = context.irBuiltIns.anyType,
                                    elements = captures.mapNotNull {
                                      if (it is IrVariable && it.isVar) {
                                        null
                                      } else {
                                        irGet(it)
                                      }
                                    })
        arguments[1] = expression
      }
    }
  }
}

inline fun <T> includeFileNameInExceptionTrace(file: IrFile, body: () -> T): T {
  try {
    return body()
  } catch (e: Exception) {
    rethrowIntellijPlatformExceptionIfNeeded(e)
    throw Exception("IR lowering failed at: ${file.name}", e)
  }
}

private fun IrValueDeclaration.isInlinedLambda(): Boolean =
  isInlineableFunction() &&
  this is IrValueParameter &&
  (parent as? IrFunction)?.isInline == true &&
  !isNoinline

private fun IrValueDeclaration.isInlineableFunction(): Boolean =
  type.isFunctionOrKFunction() ||
  type.isSyntheticComposableFunction() ||
  type.isSuspendFunctionOrKFunction()
