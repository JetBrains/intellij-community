package com.jetbrains.fleet.rpc.plugin.ir

import com.jetbrains.fleet.rpc.plugin.ir.remoteKind.REMOTE_KIND_FQN
import com.jetbrains.fleet.rpc.plugin.ir.remoteKind.toRemoteKind
import com.jetbrains.fleet.rpc.plugin.ir.util.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.collections.plus

fun buildRemoteApiDescriptorImpl(
  interfaceClass: IrClass,
  clientStub: IrClass,
  rpcMethods: List<IrSimpleFunction>,
  context: CompilerPluginContext
): IrClass {
  val generatedClass = context.pluginContext.buildClassBase {
    name = interfaceClass.name.getRemoteApiDescriptorImplName()
    visibility = interfaceClass.visibility
    kind = ClassKind.OBJECT
  }.also {
    it.parent = interfaceClass.parent
    val newTypeArgument = IrSimpleTypeImpl(
      classifier = interfaceClass.symbol,
      hasQuestionMark = false,
      arguments = emptyList(),
      annotations = emptyList()
    )
    val parametrizedClientType: IrSimpleType = IrSimpleTypeImpl(
      classifier = context.remoteApiDescriptorClass.symbol,
      hasQuestionMark = false,
      arguments = listOf(newTypeArgument),
      annotations = emptyList()
    )
    it.annotations += interfaceClass.extractApiStatusAnnotations()
    it.superTypes = listOf(parametrizedClientType)
    val container = interfaceClass.parent as? IrClass ?: interfaceClass.file
    container.addChild(it)
  }

  overrideGetSignatureFunction(rpcMethods, generatedClass, interfaceClass, context)
  overrideCallFunction(rpcMethods, generatedClass, interfaceClass, context)
  overrideClientStubFunction(generatedClass, interfaceClass, clientStub, context)
  overrideGetApiFqnFunction(generatedClass, interfaceClass, context)

  return generatedClass
}

private fun overrideGetSignatureFunction(rpcMethods: List<IrSimpleFunction>,
                                         generatedClass: IrClass,
                                         interfaceClass: IrClass,
                                         context: CompilerPluginContext) { // fun getSignature(methodName: String): RpcSignature
  overrideFunctionWithName("getSignature", generatedClass, context) { function ->
    function.generateBody(context) {
      val methodName = irTemporary(irGet(function.parameters[1]))
      whenBlockForMethods(methodName, rpcMethods, interfaceClass, context) { remoteApiFunction ->
        val errorMessage = "${interfaceClass.kotlinFqName}/${remoteApiFunction.name}"
        val parameters = createArrayOfExpression(
          arrayElementType = context.parameterDescriptorClass.defaultType,
          arrayElements = remoteApiFunction.parameters.filter { it.kind == IrParameterKind.Regular }.map { parameter ->
            irCallConstructor(
              callee = context.parameterDescriptorConstructor,
              typeArguments = listOf(context.pluginContext.irBuiltIns.stringType, context.remoteKindClass.defaultType)
            ).apply {
              arguments[0] = irString(parameter.name.identifier) // parameterName
              arguments[1] = toRemoteKind(parameter.type, context, errorMessage) // parameterKind
            }
          },
          pluginContext = context.pluginContext
        )
        val rpcSignature = irCallConstructor(
          callee = context.rpcSignatureConstructor,
          typeArguments = listOf(context.pluginContext.irBuiltIns.stringType, parameters.type, context.remoteKindClass.defaultType)
        ).apply {
          arguments[0] = irString(remoteApiFunction.name.identifier) // methodName
          arguments[1] = parameters // parameters
          arguments[2] = toRemoteKind(remoteApiFunction.returnType, context, errorMessage) // returnType
        }
        irReturn(rpcSignature)
      }
    }
  }
}

private val CompilerPluginContext.rpcSignatureConstructor
  get() = cache.remember {
    val rpcSignatureClass = pluginContext.referenceClass(ClassId.topLevel(RPC_FQN.child("RpcSignature".name)))!!
    rpcSignatureClass.constructors.single()
  }

private val CompilerPluginContext.parameterDescriptorClass
  get() = cache.remember {
    pluginContext.referenceClass(ClassId.topLevel(RPC_FQN.child("ParameterDescriptor".name)))!!
  }

private val CompilerPluginContext.parameterDescriptorConstructor
  get() = cache.remember {
    parameterDescriptorClass.constructors.single()
  }

private val CompilerPluginContext.remoteKindClass
  get() = cache.remember {
    pluginContext.referenceClass(ClassId.topLevel(REMOTE_KIND_FQN))!!
  }

private fun overrideClientStubFunction(generatedClass: IrClass,
                                       interfaceClass: IrClass,
                                       clientStub: IrClass,
                                       context: CompilerPluginContext) { // fun clientStub(proxy: Proxy): T
  overrideFunctionWithName("clientStub", generatedClass, context) { function ->
    function.returnType = interfaceClass.defaultType

    function.generateBody(context) {
      val clientStubConstructor = clientStub.primaryConstructor?.symbol!!

      val proxyType = context.pluginContext.getProxyType()
      val clientStubInstance = irCallConstructor(clientStubConstructor, listOf(proxyType)).apply {
        arguments[0] = irGet(function.parameters[1])
      }
      +irReturn(clientStubInstance)
    }
  }
}

private fun overrideGetApiFqnFunction(generatedClass: IrClass,
                                      interfaceClass: IrClass,
                                      context: CompilerPluginContext) { // fun getApiFqn(): String
  overrideFunctionWithName("getApiFqn", generatedClass, context) { function ->
    function.generateBody(context) {
      +irReturn(irString(interfaceClass.kotlinFqName.asString()))
    }
  }
}

private fun overrideCallFunction(rpcMethods: List<IrSimpleFunction>,
                                 generatedClass: IrClass,
                                 interfaceClass: IrClass,
                                 context: CompilerPluginContext) { // suspend fun call(impl: T, methodName: String, args: Array<Any?>): Any?
  val oldFunction = checkNotNull(context.remoteApiDescriptorClass.getSimpleFunction("call")).owner

  context.pluginContext.addFunctionOverride(oldFunction, generatedClass) { function ->
    // change first regular parameter type from T to actual interface type (interfaceClass.defaultType)
    function.parameters = function.parameters.mapIndexed { index, parameter ->
      if (index == 1) {
        buildValueParameter(function) {
          name = parameter.name
          updateFrom(parameter)
          type = interfaceClass.defaultType
        }
      } else {
        parameter
      }
    }

    function.generateBody(context) {
      val methodName = irTemporary(irGet(function.parameters[2]))
      whenBlockForMethods(methodName, rpcMethods, interfaceClass, context) { remoteApiFunction ->
        val call = callRemoteApiFunction(remoteApiFunction, irGet(function.parameters[1]), irGet(function.parameters[3]), context.pluginContext)
        irReturn(call)
      }
    }
  }
}

private fun overrideFunctionWithName(name: String,
                                     generatedClass: IrClass,
                                     context: CompilerPluginContext,
                                     block: (IrSimpleFunction) -> Unit) {
  val function = checkNotNull(context.remoteApiDescriptorClass.getSimpleFunction(name)).owner
  context.pluginContext.addFunctionOverride(function, generatedClass, block)
}

private fun IrBlockBodyBuilder.whenBlockForMethods(methodName: IrVariable,
                                                   rpcMethods: List<IrSimpleFunction>,
                                                   interfaceClass: IrClass,
                                                   context: CompilerPluginContext,
                                                   block: IrBlockBodyBuilder.(IrSimpleFunction) -> IrExpression) {
  +irBlock(
    origin = IrStatementOrigin.WHEN
  ) {
    +irWhen(
      context.pluginContext.irBuiltIns.unitType,
      rpcMethods.map { whenBranch(it, methodName, context, block) } + irElseBranch(
        generateErrorFunctionCall(
          irString("${interfaceClass.kotlinFqName} does not have method ").plusString(irGet(methodName), context),
          context
        )
      )
    )
  }
}

private fun IrConst.plusString(otherString: IrExpression, context: CompilerPluginContext): IrExpression {
  return irCall(stringPlusFunction(context)).apply {
    arguments[0] = this@plusString
    arguments[1] = otherString
  }
}

private fun stringPlusFunction(context: CompilerPluginContext) = context.cache.remember {
  context.pluginContext.referenceFunctions(CallableId(
    classId = ClassId(FqName.fromSegments(listOf("kotlin")), "String".name),
    callableName = "plus".name
  )).first().owner.symbol
}

private fun generateErrorFunctionCall(argument: IrExpression, context: CompilerPluginContext): IrExpression {
  return irCall(errorFunction(context)).apply {
    arguments[0] = argument
  }
}

private fun errorFunction(context: CompilerPluginContext) = context.cache.remember {
  context.pluginContext.referenceFunctions(CallableId(
    packageName = FqName.fromSegments(listOf("kotlin")),
    className = null,
    callableName = "error".name
  )).first().owner.symbol
}

private fun IrBlockBodyBuilder.whenBranch(
  remoteApiFunction: IrSimpleFunction,
  methodName: IrVariable,
  context: CompilerPluginContext,
  block: IrBlockBodyBuilder.(IrSimpleFunction) -> IrExpression,
): IrBranch =
  irBranch(
    irEquals(
      irGet(methodName),
      context.pluginContext.irBuiltIns.irString(remoteApiFunction.name.identifier),
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

private fun IrBlockBodyBuilder.callRemoteApiFunction(remoteApiFunction: IrSimpleFunction,
                                                     dispatchReceiverExpression: IrExpression,
                                                     argsExpression: IrExpression,
                                                     pluginContext: IrPluginContext): IrExpression {
  val getFunction = pluginContext.irBuiltIns.arrayClass.owner.declarations
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

private val REMOTE_API_DESCRIPTOR_INTERFACE_FQN = RPC_FQN.child("RemoteApiDescriptor".name)

private val CompilerPluginContext.remoteApiDescriptorClass
  get() = cache.remember {
    pluginContext.referenceClass(ClassId.topLevel(REMOTE_API_DESCRIPTOR_INTERFACE_FQN))?.owner!!
  }

