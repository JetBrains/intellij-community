package com.jetbrains.fleet.rpc.plugin.ir

import com.jetbrains.fleet.rpc.plugin.PARAMETER_DESCRIPTOR_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.RPC_SIGNATURE_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.ir.remoteKind.toRemoteKind
import com.jetbrains.fleet.rpc.plugin.ir.util.*
import com.jetbrains.fleet.rpc.plugin.remoteApiDescriptorImplClassName
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.collections.plus

fun IrClass.remoteApiDescriptorClassId(): ClassId =
  requireNotNull(classId) { "No class id on $kotlinFqName" }
    .createNestedClassId(remoteApiDescriptorImplClassName)

fun getDescriptorInstance(context: FileContext, apiClass: IrClass): IrClassSymbol {
  val id = apiClass.remoteApiDescriptorClassId()
  return checkNotNull(context.referenceClass(id)) { "No class for $id"}
}

fun transferApiStatusAnnotations(from: IrClass, to: IrClass) {
  to.annotations += from.extractApiStatusAnnotations()
}

@UnsafeDuringIrConstructionAPI
fun implementRemoteDescriptorInterface(
  context: FileContext,
  target: IrClass,
  rpcInterface: IrClass,
  clientStub: IrClass,
  rpcMethods: List<IrSimpleFunction>,
) {
  generateGetSignatureFunction(
    context = context,
    target = target,
    rpcInterface = rpcInterface,
    rpcMethods = rpcMethods
  )
  generateCallFunction(
    context = context,
    target = target,
    rpcInterface = rpcInterface,
    rpcMethods = rpcMethods
  )
  generateClientStubFunction(
    context = context,
    target = target,
    clientStub = clientStub
  )
  generateGetApiFqnFunction(
    context = context,
    target = target,
    rpcInterface = rpcInterface
  )
}

@UnsafeDuringIrConstructionAPI
private fun generateGetSignatureFunction(
  context: FileContext,
  target: IrClass,
  rpcInterface: IrClass,
  rpcMethods: List<IrSimpleFunction>,
) { // fun getSignature(methodName: String): RpcSignature
  replaceFakeOverride(context, target, "getSignature") { function ->
    val methodName = irTemporary(irGet(function.parameters[1]))
    whenBlockForMethods(methodName, rpcMethods, rpcInterface, context) { remoteApiFunction ->
      val parameterDescriptorClass = context.referenceClass(PARAMETER_DESCRIPTOR_CLASS_ID)!!
      val remoteKindClass = context.referenceClass(REMOTE_KIND_CLASS_ID)!!
      val errorMessage = "${rpcInterface.kotlinFqName}/${remoteApiFunction.name}"
      val parameters = createArrayOfExpression(
        arrayElementType = parameterDescriptorClass.defaultType,
        arrayElements = remoteApiFunction.parameters.filter { it.kind == IrParameterKind.Regular }.map { parameter ->
          irCallConstructor(
            callee = parameterDescriptorClass.constructors.single(),
            typeArguments = listOf(context.irBuiltIns.stringType, remoteKindClass.defaultType)
          ).apply {
            arguments[0] = irString(parameter.name.identifier) // parameterName
            arguments[1] = toRemoteKind(parameter.type, context, errorMessage) // parameterKind
          }
        },
        irBuiltIns = context.irBuiltIns,
      )
      val rpcSignature = irCallConstructor(
        callee = context.referenceClass(RPC_SIGNATURE_CLASS_ID)!!.constructors.single(),
        typeArguments = listOf(context.irBuiltIns.stringType, parameters.type, remoteKindClass.defaultType)
      ).apply {
        arguments[0] = irString(remoteApiFunction.name.identifier) // methodName
        arguments[1] = parameters // parameters
        arguments[2] = toRemoteKind(remoteApiFunction.returnType, context, errorMessage) // returnType
      }
      irReturn(rpcSignature)
    }
  }
}

@UnsafeDuringIrConstructionAPI
private fun generateClientStubFunction(
  context: FileContext,
  target: IrClass,
  clientStub: IrClass,
) { // fun clientStub(proxy: Proxy): T
  replaceFakeOverride(context, target, "clientStub") { function ->
    val clientStubConstructor = clientStub.primaryConstructor?.symbol!!

    val proxyType = getProxyType(context.irBuiltIns)
    val clientStubInstance = irCallConstructor(clientStubConstructor, listOf(proxyType)).apply {
      arguments[0] = irGet(function.parameters[1])
    }
    +irReturn(clientStubInstance)
  }
}

@UnsafeDuringIrConstructionAPI
private fun generateGetApiFqnFunction(
  context: FileContext,
  target: IrClass,
  rpcInterface: IrClass,
) { // fun getApiFqn(): String
  replaceFakeOverride(context, target, "getApiFqn") {
    +irReturn(irString(rpcInterface.kotlinFqName.asString()))
  }
}

@UnsafeDuringIrConstructionAPI
private fun generateCallFunction(
  context: FileContext,
  target: IrClass,
  rpcInterface: IrClass,
  rpcMethods: List<IrSimpleFunction>,
) { // suspend fun call(impl: T, methodName: String, args: Array<Any?>): Any?
  replaceFakeOverride(context, target, "call") { function ->
    val methodName = irTemporary(irGet(function.parameters[2]))
    whenBlockForMethods(methodName, rpcMethods, rpcInterface, context) { remoteApiFunction ->
      val call = callRemoteApiFunction(
        remoteApiFunction = remoteApiFunction,
        dispatchReceiverExpression = irGet(function.parameters[1]),
        argsExpression = irGet(function.parameters[3]),
        irBuiltIns = context.irBuiltIns,
      )
      irReturn(call)
    }
  }
}

@UnsafeDuringIrConstructionAPI
private fun replaceFakeOverride(
  context: FileContext,
  target: IrClass,
  functionName: String,
  block: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
) {
  checkNotNull(target.getSimpleFunction(functionName)).owner.let { fn ->
    fn.origin = RPC_PLUGIN_ORIGIN
    fn.modality = Modality.FINAL
    fn.isFakeOverride = false
    fn.generateBody(context) { block(fn) }
  }
}

@UnsafeDuringIrConstructionAPI
private fun IrBlockBodyBuilder.whenBlockForMethods(
  methodName: IrVariable,
  rpcMethods: List<IrSimpleFunction>,
  interfaceClass: IrClass,
  context: FileContext,
  block: IrBlockBodyBuilder.(IrSimpleFunction) -> IrExpression,
) {
  +irBlock(
    origin = IrStatementOrigin.WHEN
  ) {
    +irWhen(
      context.irBuiltIns.unitType,
      rpcMethods.map { whenBranch(it, methodName, context, block) } + irElseBranch(
        generateErrorFunctionCall(
          irString("${interfaceClass.kotlinFqName} does not have method ").plusString(irGet(methodName), context),
          context
        )
      )
    )
  }
}

@UnsafeDuringIrConstructionAPI
private fun IrConst.plusString(otherString: IrExpression, context: FileContext): IrExpression {
  return irCall(stringPlusFunction(context)).apply {
    arguments[0] = this@plusString
    arguments[1] = otherString
  }
}

@UnsafeDuringIrConstructionAPI
private fun stringPlusFunction(context: FileContext): IrSimpleFunctionSymbol =
  context.referenceFunctions(CallableId(
    classId = ClassId(FqName.fromSegments(listOf("kotlin")), "String".name),
    callableName = "plus".name
  )).first().owner.symbol

@UnsafeDuringIrConstructionAPI
private fun generateErrorFunctionCall(argument: IrExpression, context: FileContext): IrExpression {
  return irCall(errorFunction(context)).apply {
    arguments[0] = argument
  }
}

@UnsafeDuringIrConstructionAPI
private fun errorFunction(context: FileContext): IrSimpleFunctionSymbol =
  context.referenceFunctions(CallableId(
    packageName = FqName.fromSegments(listOf("kotlin")),
    className = null,
    callableName = "error".name
  )).first().owner.symbol


private fun IrBlockBodyBuilder.whenBranch(
  remoteApiFunction: IrSimpleFunction,
  methodName: IrVariable,
  context: FileContext,
  block: IrBlockBodyBuilder.(IrSimpleFunction) -> IrExpression,
): IrBranch =
  irBranch(
    irEquals(
      irGet(methodName),
      context.irBuiltIns.irString(remoteApiFunction.name.identifier),
      IrStatementOrigin.EQEQ
    ),
    block(remoteApiFunction)
  )

private fun IrBuiltIns.irString(value: String): IrConst = IrConstImpl(
  UNDEFINED_OFFSET,
  UNDEFINED_OFFSET,
  stringType,
  IrConstKind.String,
  value
)

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrBlockBodyBuilder.callRemoteApiFunction(
  remoteApiFunction: IrSimpleFunction,
  dispatchReceiverExpression: IrExpression,
  argsExpression: IrExpression,
  irBuiltIns: IrBuiltIns,
): IrExpression {
  val getFunction = irBuiltIns.arrayClass.owner.declarations
    .filterIsInstance<IrSimpleFunction>()
    .single { it.name.asString() == "get" }

  return irCall(
    remoteApiFunction.symbol,
  ).also { call ->
    call.arguments[0] = dispatchReceiverExpression

    // Parameters without dispatcher
    val valueParameters = remoteApiFunction.parameters.drop(1)
    val args = irTemporary(argsExpression) // vararg with args

    for (index in valueParameters.indices) {
      val argument = irCall(getFunction.symbol).also { getCall ->
        getCall.arguments[0] = irGet(args)
        getCall.arguments[1] = irInt(index)
      }
      call.arguments[index + 1] = irCastIfNeeded(argument, valueParameters[index].type)
    }
  }
}
