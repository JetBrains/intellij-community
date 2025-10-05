package com.jetbrains.fleet.rpc.plugin.ir.util

import com.jetbrains.fleet.rpc.plugin.ir.CompilerPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isAnnotationWithEqualFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

val RPC_FQN = FqName.fromSegments(listOf("fleet", "rpc"))
val RPC_ANNOTATION_FQN = RPC_FQN.child("Rpc".name)

val RPC_CORE_FQN: FqName = RPC_FQN.child("core".name)
val REMOTE_RESOURCE_FQN: FqName = RPC_CORE_FQN.child("RemoteResource".name)

val String.name: Name
  get() = Name.identifier(this)

fun irGet(type: IrType, symbol: IrValueSymbol, origin: IrStatementOrigin?): IrExpression {
  return IrGetValueImpl(
    UNDEFINED_OFFSET,
    UNDEFINED_OFFSET,
    type,
    symbol,
    origin
  )
}

fun irGet(variable: IrValueDeclaration, origin: IrStatementOrigin? = null): IrExpression {
  return irGet(variable.type, variable.symbol, origin)
}

fun irCall(symbol: IrFunctionSymbol, origin: IrStatementOrigin? = null): IrCallImpl {
  return IrCallImpl.fromSymbolOwner(
    startOffset = UNDEFINED_OFFSET,
    endOffset = UNDEFINED_OFFSET,
    type = symbol.owner.returnType,
    symbol = symbol as IrSimpleFunctionSymbol,
    origin = origin,
  )
}

fun IrClass.hasRpcAnnotation() =
  this.annotations.any { it.isAnnotationWithEqualFqName(RPC_ANNOTATION_FQN) }


fun IrPluginContext.buildClassBase(customizer: IrClassBuilder.() -> Unit) =
  irFactory.buildClass {
    startOffset = SYNTHETIC_OFFSET
    endOffset = SYNTHETIC_OFFSET
    origin = IrDeclarationOrigin.DEFINED
    kind = ClassKind.CLASS
    modality = Modality.FINAL
    customizer()
  }.also {
    thisReceiver(it)
    constructor(it)
    initializer(it)
  }

private fun IrPluginContext.thisReceiver(irClass: IrClass): IrValueParameter =
  irFactory.createValueParameter(
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    origin = IrDeclarationOrigin.INSTANCE_RECEIVER,
    symbol = IrValueParameterSymbolImpl(),
    name = SpecialNames.THIS,
    type = IrSimpleTypeImpl(irClass.symbol, false, emptyList(), emptyList()),
    varargElementType = null,
    isCrossinline = false,
    isNoinline = false,
    isHidden = false,
    isAssignable = false,
    kind = IrParameterKind.DispatchReceiver
  ).also {
    it.parent = irClass
    irClass.thisReceiver = it
  }

private fun IrPluginContext.constructor(irClass: IrClass): IrConstructor =
  irClass.addConstructor {
    isPrimary = true
    returnType = irClass.defaultType
  }.apply {
    parent = irClass

    body = irFactory.createBlockBody(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET).apply {
      statements += IrDelegatingConstructorCallImpl.fromSymbolOwner(
        SYNTHETIC_OFFSET,
        SYNTHETIC_OFFSET,
        irBuiltIns.anyType,
        irBuiltIns.anyClass.constructors.first(),
        typeArgumentsCount = 0,
      )

      statements += IrInstanceInitializerCallImpl(
        SYNTHETIC_OFFSET,
        SYNTHETIC_OFFSET,
        irClass.symbol,
        irBuiltIns.unitType
      )
    }
  }

private fun IrPluginContext.initializer(irClass: IrClass): IrAnonymousInitializer =
  irFactory.createAnonymousInitializer(
    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
    origin = IrDeclarationOrigin.DEFINED,
    symbol = IrAnonymousInitializerSymbolImpl(),
    isStatic = false
  ).apply {
    parent = irClass
  }

fun IrPluginContext.getProxyType(): IrSimpleType { // typealias Proxy = suspend (String, Array<Any?>) -> Any?
  val stringType = irBuiltIns.stringType
  val nullableAnyType = irBuiltIns.anyNType.makeNullable()
  val anyArrayType = irBuiltIns.arrayClass.typeWith(nullableAnyType)

  return irBuiltIns.suspendFunctionN(2)
    .typeWith(/*thisType, */stringType, anyArrayType, nullableAnyType)
}

fun IrBuilderWithScope.createArrayOfExpression(
  arrayElementType: IrType,
  arrayElements: List<IrExpression>,
  pluginContext: IrPluginContext
): IrExpression {
  val arrayType = pluginContext.irBuiltIns.arrayClass.typeWith(arrayElementType)
  val varargOfElements = IrVarargImpl(startOffset, endOffset, arrayType, arrayElementType, arrayElements)
  val typeArguments = listOf(arrayElementType)

  return irCall(pluginContext.irBuiltIns.arrayOf, arrayType, typeArguments = typeArguments).apply {
    arguments[0] = varargOfElements
  }
}

fun IrPluginContext.addFunctionOverride(originalFunction: IrSimpleFunction,
                                        parentClass: IrClass,
                                        block: (IrSimpleFunction) -> Unit) {
  irFactory.buildFun {
    name = originalFunction.name
    returnType = originalFunction.returnType
    modality = Modality.FINAL
  }.also { function ->
    function.isSuspend = originalFunction.isSuspend
    function.overriddenSymbols = listOf(originalFunction.symbol)
    function.parent = parentClass

    for (typeParameter in originalFunction.typeParameters) {
      function.addTypeParameter {
        updateFrom(typeParameter)
        superTypes += typeParameter.superTypes
      }
    }

    // Prepend dispatch receiver (should be first in parameter list)
    function.parameters += function.buildReceiverParameter {
      type = parentClass.defaultType
    }

    for (parameter in originalFunction.parameters) {
      // Dispatch receiver was specified right above
      // TODO review: maybe this dispatch receiver is already present with proper type on original function? (then this can be simplified)
      if (parameter.kind != IrParameterKind.DispatchReceiver) {
        function.addValueParameter {
          name = parameter.name
          updateFrom(parameter)
        }
      }
    }

    block(function)

    parentClass.declarations += function
  }
}

fun IrFunction.generateBody(context: CompilerPluginContext, block: IrBlockBodyBuilder.() -> Unit) {
  body = DeclarationIrBuilder(context.pluginContext, symbol).irBlockBody {
    block()
  }
}

/**
 * Creates a lambda based on the provided function type.
 *
 * @param type type of the function
 */
internal fun IrPluginContext.irLambda(
  symbol: IrSymbolOwner,
  parent: IrDeclarationParent,
  type: IrSimpleType,
  // Default values assume the `type` parameter is `FunctionN` class type (arg types first, last one is return type)
  parameterTypes: List<IrType> = type.arguments.dropLast(1).map { it.typeOrFail },
  returnType: IrType = type.arguments.last().typeOrFail,
  block: (IrFunction) -> Unit,
): IrFunctionExpressionImpl {
  return IrFunctionExpressionImpl(
    startOffset = symbol.startOffset,
    endOffset = symbol.endOffset,
    type = type,
    function = irFactory.buildFun {
      origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
      name = SpecialNames.NO_NAME_PROVIDED
      visibility = DescriptorVisibilities.LOCAL
      this.returnType = returnType
      modality = Modality.FINAL
      isSuspend = true
    }.also {
      it.parent = parent

      parameterTypes.forEachIndexed { index, typeArgument ->
        it.addValueParameter("p${index}", typeArgument.typeOrFail)
      }

      block(it)
    },
    origin = IrStatementOrigin.LAMBDA
  )
}
