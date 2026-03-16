// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package noria.plugin

import org.jetbrains.kotlin.backend.common.ModuleLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.assignFrom

class NoriaParamTransformer(
  private val context: IrPluginContext,
) : IrElementTransformerVoid(),
    ModuleLoweringPass {

  private var inlineLambdaInfo = ComposeInlineLambdaLocator(context)

  override fun lower(irModule: IrModuleFragment) {
    inlineLambdaInfo.scan(irModule)

    irModule.transformChildrenVoid(this)

    val typeRemapper = ComposableTypeRemapper(
      context,
      noriaContextType
    )
    val transformer = ComposableTypeTransformer(context, typeRemapper)
    // for each declaration, we remap types to ensure that @Composable lambda types are realized
    irModule.transformChildrenVoid(transformer)

    // just go through and patch all of the parents to make sure things are properly wired
    // up.
    irModule.patchDeclarationParents()
  }

  private val transformedFunctions: MutableMap<IrSimpleFunction, IrSimpleFunction> =
    mutableMapOf()

  private val transformedFunctionSet = mutableSetOf<IrSimpleFunction>()

  private val noriaContextType =
    context.referenceClass(ClassId.topLevel(NoriaRuntime.NoriaContextFqn))?.defaultType
    ?: error("Cannot find NoriaContext class")

  private val composableIrClass =
    context.referenceClass(NoriaRuntime.ComposableClassId)?.owner ?: error("Cannot find the Composable annotation class in the classpath")

  private var currentParent: IrDeclarationParent? = null

  override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
    val parent = currentParent
    if (declaration is IrDeclarationParent) {
      currentParent = declaration
    }
    return super.visitDeclaration(declaration).also {
      currentParent = parent
    }
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement =
    super.visitSimpleFunction(declaration.withNoriaContextParamIfNeeded())

  override fun visitLocalDelegatedPropertyReference(
    expression: IrLocalDelegatedPropertyReference,
  ): IrExpression {
    expression.getter = expression.getter.owner.withNoriaContextParamIfNeeded().symbol
    expression.setter = expression.setter?.run { owner.withNoriaContextParamIfNeeded().symbol }
    return super.visitLocalDelegatedPropertyReference(expression)
  }

  override fun visitPropertyReference(expression: IrPropertyReference): IrExpression {
    expression.getter = expression.getter?.run { owner.withNoriaContextParamIfNeeded().symbol }
    expression.setter = expression.setter?.run { owner.withNoriaContextParamIfNeeded().symbol }
    return super.visitPropertyReference(expression)
  }

  override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
    if (!expression.type.isSyntheticComposableFunction()) {
      return super.visitFunctionReference(expression)
    }

    val fn = expression.symbol.owner as? IrSimpleFunction ?: return super.visitFunctionReference(expression)

    return transformComposableFunctionReference(expression, fn)
  }

  private fun transformComposableFunctionReference(
    expression: IrFunctionReference,
    fn: IrSimpleFunction,
  ): IrExpression {
    val type = expression.type as IrSimpleType
    val arity = type.arguments.size + /* composer */ 1

    val newType = IrSimpleTypeImpl(
      classifier = context.irBuiltIns.functionN(arity).symbol,
      hasQuestionMark = type.isNullable(),
      arguments = buildList {
        addAll(type.arguments.dropLast(1))
        add(noriaContextType)
        add(type.arguments.last())
      },
      annotations = type.annotations
    )

    // Transform receiver arguments
    expression.transformChildrenVoid()

    // Adapted function calls created by Kotlin compiler don't copy annotations from the original function
    if (fn.origin == IrDeclarationOrigin.ADAPTER_FOR_CALLABLE_REFERENCE && !fn.hasComposableAnnotation) {
      fn.annotations += createComposableAnnotation()
    }

    return IrFunctionReferenceImpl(
      startOffset = expression.startOffset,
      endOffset = expression.endOffset,
      type = newType,
      symbol = fn.withNoriaContextParamIfNeeded().symbol,
      typeArgumentsCount = expression.typeArguments.size,
      reflectionTarget = expression.reflectionTarget?.owner?.let {
        if (it is IrSimpleFunction) it.withNoriaContextParamIfNeeded().symbol else it.symbol
      },
      origin = expression.origin,
    ).apply {
      typeArguments.assignFrom(expression.typeArguments)
      arguments.assignFrom(expression.arguments)
      repeat(arity - expression.arguments.size) {
        arguments.add(null)
      }
    }
  }

  override fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty): IrStatement {
    if (declaration.getter.isComposableDelegatedAccessor()) {
      declaration.getter.annotations += createComposableAnnotation()
    }

    if (declaration.setter?.isComposableDelegatedAccessor() == true) {
      declaration.setter!!.annotations += createComposableAnnotation()
    }

    return super.visitLocalDelegatedProperty(declaration)
  }

  private fun createComposableAnnotation() =
    IrConstructorCallImpl(
      startOffset = SYNTHETIC_OFFSET,
      endOffset = SYNTHETIC_OFFSET,
      type = composableIrClass.defaultType,
      symbol = composableIrClass.primaryConstructor!!.symbol,
      typeArgumentsCount = 0,
      constructorTypeArgumentsCount = 0
    )

  fun IrCall.withNoriaContextParamIfNeeded(composerParam: IrValueParameter): IrCall {
    val newFn = when {
      symbol.owner.isComposableDelegatedAccessor() -> {
        if (!symbol.owner.hasComposableAnnotation) {
          symbol.owner.annotations += createComposableAnnotation()
        }
        symbol.owner.withNoriaContextParamIfNeeded()
      }
      isComposableLambdaInvoke() ->
        symbol.owner.lambdaInvokeWithComposerParam()
      symbol.owner.hasComposableAnnotation ->
        symbol.owner.withNoriaContextParamIfNeeded()
      // Not a composable call
      else -> return this
    }

    return IrCallImpl(
      startOffset,
      endOffset,
      type,
      newFn.symbol,
      typeArguments.size,
      origin,
      superQualifierSymbol
    ).also { newCall ->
      newCall.copyAttributes(this)
      newCall.copyTypeArgumentsFrom(this)

      arguments.fastForEachIndexed { i, arg ->
        val p = newFn.parameters[i]
        newCall.arguments[p.indexInParameters] = arg
      }

      val argIndex = arguments.size
      newCall.arguments[argIndex] = irGet(composerParam)
    }
  }

  // Transform `@Composable fun foo(params): RetType` into `fun foo(params, $composer: Composer): RetType`
  private fun IrSimpleFunction.withNoriaContextParamIfNeeded(): IrSimpleFunction {
    // don't transform functions that themselves were produced by this function. (ie, if we
    // call this with a function that has the synthetic composer parameter, we don't want to
    // transform it further).
    if (transformedFunctionSet.contains(this)) return this

    // if not a composable fn, nothing we need to do
    if (!this.hasComposableAnnotation) {
      return this
    }

    // we don't bother transforming expect functions. They exist only for type resolution and
    // don't need to be transformed to have a composer parameter
    if (isExpect) return this

    // cache the transformed function with composer parameter
    return transformedFunctions[this] ?: copyWithComposerParam()
  }

  private fun IrSimpleFunction.lambdaInvokeWithComposerParam(): IrSimpleFunction {
    val argCount = parameters.size
    val newFnClass = context.irBuiltIns.functionN(argCount + /* $ctx */ 1 - /* dispatch receiver */ 1)
    val newInvoke = newFnClass.functions.first {
      it.name == OperatorNameConventions.INVOKE
    }
    newInvoke.parameters.findLast { it.kind == IrParameterKind.Regular }?.name = NoriaContextParameterName
    return newInvoke
  }

  private fun jvmNameAnnotation(name: String): IrConstructorCall {
    val jvmName = context.referenceClass(JvmStandardClassIds.Annotations.JvmName)
                  ?: error("Class not found in the classpath: ${JvmStandardClassIds.Annotations.JvmName.asSingleFqName()}")
    val ctor = jvmName.constructors.first { it.owner.isPrimary }
    val type = jvmName.createType(false, emptyList())
    return IrConstructorCallImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = type,
      symbol = ctor,
      typeArgumentsCount = 0,
      constructorTypeArgumentsCount = 0,
    ).also {
      it.arguments[0] = irConst(name)
    }
  }

  private fun irConst(value: String): IrConst = IrConstImpl(
    UNDEFINED_OFFSET,
    UNDEFINED_OFFSET,
    context.irBuiltIns.stringType,
    IrConstKind.String,
    value
  )

  internal inline fun <reified T : IrElement> T.deepCopyWithSymbolsAndMetadata(
    initialParent: IrDeclarationParent? = null,
    createTypeRemapper: (SymbolRemapper) -> TypeRemapper = ::DeepCopyTypeRemapper,
  ): T {
    val symbolRemapper = DeepCopySymbolRemapper()
    acceptVoid(symbolRemapper)
    val typeRemapper = createTypeRemapper(symbolRemapper)
    return (transform(DeepCopyPreservingMetadata(symbolRemapper, typeRemapper), null) as T).patchDeclarationParents(initialParent)
  }

  private fun IrSimpleFunction.copyWithComposerParam(): IrSimpleFunction {
    assert(parameters.lastOrNull()?.name != NoriaContextParameterName) {
      "Attempted to add composer param to $this, but it has already been added."
    }
    return deepCopyWithSymbolsAndMetadata(parent).also { fn ->
      val oldFn = this

      // NOTE: it's important to add these here before we recurse into the body in
      // order to avoid an infinite loop on circular/recursive calls
      transformedFunctionSet.add(fn)
      transformedFunctions[oldFn] = fn

      fn.metadata = oldFn.metadata

      // The overridden symbols might also be composable functions, so we want to make sure
      // and transform them as well
      fn.overriddenSymbols = oldFn.overriddenSymbols.map {
        it.owner.withNoriaContextParamIfNeeded().symbol
      }

      val propertySymbol = oldFn.correspondingPropertySymbol
      if (propertySymbol != null) {
        fn.correspondingPropertySymbol = propertySymbol
        if (propertySymbol.owner.getter == oldFn) {
          propertySymbol.owner.getter = fn
        }
        if (propertySymbol.owner.setter == oldFn) {
          propertySymbol.owner.setter = fn
        }
      }
      // if we are transforming a composable property, the jvm signature of the
      // corresponding getters and setters have a composer parameter. Since Kotlin uses the
      // lack of a parameter to determine if it is a getter, this breaks inlining for
      // composable property getters since it ends up looking for the wrong jvmSignature.
      // In this case, we manually add the appropriate "@JvmName" annotation so that the
      // inliner doesn't get confused.
      fn.correspondingPropertySymbol?.let { propertySymbol ->
        if (!fn.hasAnnotation(DescriptorUtils.JVM_NAME)) {
          val propertyName = propertySymbol.owner.name.identifier
          val name = if (fn.isGetter) {
            JvmAbi.getterName(propertyName)
          }
          else {
            JvmAbi.setterName(propertyName)
          }
          fn.annotations += jvmNameAnnotation(name)
        }
      }

      fn.parameters.fastForEach { param ->
        // Composable lambdas will always have `IrGet`s of all of their parameters
        // generated, since they are passed into the restart lambda. This causes an
        // interesting corner case with "anonymous parameters" of composable functions.
        // If a parameter is anonymous (using the name `_`) in user code, you can usually
        // make the assumption that it is never used, but this is technically not the
        // case in composable lambdas. The synthetic name that kotlin generates for
        // anonymous parameters has an issue where it is not safe to dex, so we sanitize
        // the names here to ensure that dex is always safe.
        if (param.kind == IrParameterKind.Regular || param.kind == IrParameterKind.Context) {
          val newName = dexSafeName(param.name)
          param.name = newName
        }
        param.isAssignable = param.defaultValue != null
      }

      // $ctx
      val ctxParam = fn.addValueParameter {
        name = NoriaContextParameterName
        type = noriaContextType.makeNullable() // TODO: why nullable
        origin = IrDeclarationOrigin.DEFINED
        isAssignable = true
      }

      inlineLambdaInfo.scan(fn)

      fn.transformChildrenVoid(object : IrElementTransformerVoid() {
        var isNestedScope = false
        override fun visitFunction(declaration: IrFunction): IrStatement {
          val wasNested = isNestedScope
          try {
            // we don't want to pass the composer parameter in to composable calls
            // inside of nested scopes.... *unless* the scope was inlined.
            isNestedScope = wasNested ||
                            !inlineLambdaInfo.isInlineLambda(declaration) ||
                            declaration.hasComposableAnnotation
            return super.visitFunction(declaration)
          }
          finally {
            isNestedScope = wasNested
          }
        }

        override fun visitCall(expression: IrCall): IrExpression {
          val expr = if (!isNestedScope) {
            expression.withNoriaContextParamIfNeeded(ctxParam)
          }
          else
            expression
          return super.visitCall(expr)
        }
      })
    }
  }

  companion object {
    @Suppress("CanConvertToMultiDollarString")
    val NoriaContextParameterName: Name = Name.identifier("\$ctx")
  }
}

private fun irGet(type: IrType, symbol: IrValueSymbol): IrExpression {
  return IrGetValueImpl(
    UNDEFINED_OFFSET,
    UNDEFINED_OFFSET,
    type,
    symbol
  )
}

private fun irGet(variable: IrValueDeclaration): IrExpression {
  return irGet(variable.type, variable.symbol)
}

fun IrCall.isInvoke(): Boolean {
  if (origin == IrStatementOrigin.INVOKE)
    return true
  val function = symbol.owner
  return function.name == OperatorNameConventions.INVOKE &&
         function.parentClassOrNull?.defaultType?.let {
           it.isFunction() || it.isSyntheticComposableFunction()
         } ?: false
}

fun IrCall.isComposableLambdaInvoke(): Boolean {
  if (!isInvoke()) return false
  // [ComposerParamTransformer] replaces composable function types of the form
  // `@Composable Function1<T1, T2>` with ordinary functions with extra parameters, e.g.,
  // `Function3<T1, Composer, Int, T2>`. After this lowering runs we have to check the
  // `attributeOwnerId` to recover the original type.
  val receiver = dispatchReceiver?.let { it.attributeOwnerId as? IrExpression ?: it }
  return receiver?.type?.let {
    it.hasComposableAnnotation || it.isSyntheticComposableFunction()
  } ?: false
}

/*
 * Delegated accessors are generated with IrReturn(IrCall(<delegated function>)) structure.
 * To verify the delegated function is composable, this function is unpacking it and
 * checks annotation on the symbol owner of the call.
 */
private fun IrFunction.isComposableDelegatedAccessor(): Boolean =
  origin == IrDeclarationOrigin.DELEGATED_PROPERTY_ACCESSOR &&
  body?.let {
    val returnStatement = it.statements.singleOrNull() as? IrReturn
    val callStatement = returnStatement?.value as? IrCall
    val target = callStatement?.symbol?.owner
    target?.hasComposableAnnotation
  } == true

private val unsafeSymbolsRegex = "[ <>]".toRegex()

private fun dexSafeName(name: Name): Name {
  return if (
    name.isSpecial || name.asString().contains(unsafeSymbolsRegex)
  ) {
    val sanitized = name
      .asString()
      .replace(unsafeSymbolsRegex, "\\$")
    Name.identifier(sanitized)
  }
  else name
}


private fun <T> List<T>.fastForEach(action: (T) -> Unit) {
  for (i in indices) {
    val item = get(i)
    action(item)
  }
}

private fun <T> List<T>.fastForEachIndexed(action: (index: Int, T) -> Unit) {
  for (i in indices) {
    val item = get(i)
    action(i, item)
  }
}

private fun IrSimpleFunction.requiresDefaultParameter(): Boolean =
  when {
    // Same as above, but the method was generated by the old compiler, so $default parameter is needed for compatibility
    isLegacyOpenFunctionWithDefault() -> true
    // Virtual functions move default parameters into a wrapper
    isVirtualFunctionWithDefaultParam() -> false
    // Fake overrides require default parameters if the original method is not virtual
    isFakeOverride -> overriddenSymbols.any { it.owner.modality == Modality.FINAL && it.owner.requiresDefaultParameter() }
    // Regular functions also require default parameters
    else -> parameters.any { it.defaultValue != null }
  }

private fun IrSimpleFunction.isLegacyOpenFunctionWithDefault(): Boolean =
  modality == Modality.OPEN && (origin == IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB &&
                                parameters.any { it.hasDefaultValue() }) || overriddenSymbols.any { it.owner.isLegacyOpenFunctionWithDefault() }

private fun IrFunction.isVirtualFunctionWithDefaultParam(): Boolean =
  this is IrSimpleFunction && overriddenSymbols.any { it.owner.isVirtualFunctionWithDefaultParam() }
