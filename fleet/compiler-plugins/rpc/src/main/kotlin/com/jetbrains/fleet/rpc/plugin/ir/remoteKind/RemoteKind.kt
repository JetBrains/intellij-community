package com.jetbrains.fleet.rpc.plugin.ir.remoteKind

import com.jetbrains.fleet.rpc.plugin.ir.CompilerPluginContext
import com.jetbrains.fleet.rpc.plugin.ir.getRemoteApiDescriptorInstance
import com.jetbrains.fleet.rpc.plugin.ir.util.RPC_FQN
import com.jetbrains.fleet.rpc.plugin.ir.util.name
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

fun IrBuilderWithScope.toRemoteKind(irType: IrType, context: CompilerPluginContext, debugInfo: String): IrExpression {
  return handleSpecialTypes(irType, context, debugInfo)
         ?: handleRemoteObject(irType, context)
         ?: handleResource(irType, context)
         ?: run { // RemoteKind.Data
           val serializer = generateSerializerCall(irType, context, debugInfo)

           irCallConstructor(context.remoteKindDataConstructor, listOf(serializer.type)).apply {
             arguments[0] = serializer
           }
         }
}

private fun IrBuilderWithScope.handleResource(irType: IrType, context: CompilerPluginContext): IrExpression? {
  return if (irType.classFqName == RESOURCE_FQN) {
    // Get the class of the type argument
    val serviceType = ((irType as? IrSimpleType)?.arguments?.singleOrNull() as? IrTypeProjection)?.type
    val irClass = serviceType?.classOrNull?.owner

    if (irClass != null && irClass.allSuperInterfaces().any { it.kotlinFqName == REMOTE_RESOURCE_FQN }) {
      val remoteApiDescriptor = getRemoteApiDescriptorInstance(serviceType) {
        "Resource generation"
      }
      irCallConstructor(context.remoteKindResourceConstructor, listOf(remoteApiDescriptor.type)).apply {
        arguments[0] = remoteApiDescriptor
      }
    }
    else {
      null
    }
  }
  else {
    null
  }

}

private fun IrBuilderWithScope.handleRemoteObject(irType: IrType, context: CompilerPluginContext): IrExpression? {
  val irClass = irType.classOrFail.owner

  return if (irClass.allSuperInterfaces().any { it.kotlinFqName == REMOTE_OBJECT_FQN }) {
    val remoteApiDescriptor = getRemoteApiDescriptorInstance(irType) {
      "RemoteObject generation"
    }
    irCallConstructor(context.remoteKindRemoteObjectConstructor, listOf(remoteApiDescriptor.type)).apply {
      arguments[0] = remoteApiDescriptor
    }
  }
  else {
    null
  }
}

private fun IrBuilderWithScope.generateSerializerCall(irType: IrType,
                                                      context: CompilerPluginContext,
                                                      debugInfo: String,
                                                      isTypeArgument: Boolean = false): IrExpression {
  val irClass = irType.classOrFail.owner

  // fallback to the class itself, for example Unit serializer() is defined on Unit since it has no Companion
  val companionOrClass = irClass.companionObject() ?: irClass

  val serializerCall =
    // check if the class is one of the special ones that don't have serializer on Companion (List/Set/etc)
    context.getSpecialSerializer(irClass.kotlinFqName)?.let { irCall(it) }
      ?: findBuiltinSerializerExtensionFunctions(companionOrClass, context)
      // check that class has @Serializable annotation before trying to find serializer() method on it
      ?: irClass.getAnnotation(FqName.fromSegments(listOf("kotlinx", "serialization", "Serializable")))?.let {
        findSerializerAsMethod(companionOrClass)
      }

  val serializer = serializerCall?.apply {
    val valueParameters = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }

    (irType as IrSimpleType).arguments.zip(valueParameters).forEachIndexed { index, (typeArgument, parameter) ->
      arguments[parameter.indexInParameters] = generateSerializerCall(typeArgument.typeOrFail, context, debugInfo, isTypeArgument = true)
      typeArguments[index] = typeArgument.typeOrFail
    }
  }

  return if (serializer == null) {
    val message = "Couldn't find serializer function on companion object for ${irClass.dumpKotlinLike()}\nAdditional info:\n$debugInfo"
    if (isTypeArgument) {
      context.messageCollector.report(
        CompilerMessageSeverity.WARNING,
        message
      )

      irCallConstructor(context.throwingSerializerConstructor, listOf(this.context.irBuiltIns.stringType)).apply {
        val errorString = "ThrowingSerializer was used for serialization/deserialization. " +
                          "This could happen if there is a @Serializable class with non-serializable type argument in rpc interface.\n" +
                          "Additional info:\n$debugInfo"
        arguments[0] = irString(errorString)
      }
    } else {
      error(message)
    }
  } else if (irType.isNullable()) {
    irCall(context.nullableSerializerProperty.getter!!).apply {
      insertExtensionReceiver(serializer)
    }
  }
  else {
    serializer
  }
}

// find .serializer() as an extension function
private fun IrBuilderWithScope.findBuiltinSerializerExtensionFunctions(
  companionOrClass: IrClass,
  context: CompilerPluginContext,
): IrFunctionAccessExpression? {
  val serializerFunction =
    context.pluginContext.referenceFunctions(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "serializer".name
    )).singleOrNullOrThrow { fn ->
      fn.owner.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type == companionOrClass.defaultType
    }

  return serializerFunction?.let {
    irCall(serializerFunction).apply {
      insertExtensionReceiver(irGetObject(companionOrClass.symbol))
    }
  }
}

// find .serializer() as a method of irClass
private fun IrBuilderWithScope.findSerializerAsMethod(irClass: IrClass): IrFunctionAccessExpression? {
  return irClass.functions.asIterable().singleOrNullOrThrow { function ->
    function.name.identifierOrNullIfSpecial == "serializer" &&
      function.parameters.singleOrNull { it.kind == IrParameterKind.Regular }?.isVararg != true
  }?.let {
    irCall(it).apply {
      dispatchReceiver = irGetObject(irClass.symbol)
    }
  }
}

private val CompilerPluginContext.nullableSerializerProperty
  get() = cache.remember {
    pluginContext.referenceProperties(CallableId(
      packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
      className = null,
      callableName = "nullable".name
    )).first().owner
  }

private val CompilerPluginContext.remoteKindDataConstructor
  get() = cache.remember {
    pluginContext.referenceClass(
      ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Data")), false)
    )!!.constructors.first()
  }

private val CompilerPluginContext.remoteKindRemoteObjectConstructor
  get() = cache.remember {
    pluginContext.referenceClass(
      ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "RemoteObject")), false)
    )!!.constructors.first()
  }

private val CompilerPluginContext.remoteKindResourceConstructor
  get() = cache.remember {
    pluginContext.referenceClass(
      ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Resource")), false)
    )!!.constructors.first()
  }

private val CompilerPluginContext.throwingSerializerConstructor
  get() = pluginContext.referenceClass(
    ClassId.topLevel(RPC_FQN.child("core".name).child("ThrowingSerializer".name))
  )!!.constructors.first()

/**
 * Same idea as [kotlin.collections.singleOrNull] but will throw if the collection contains more than one element.
 * */
private inline fun <T> Iterable<T>.singleOrNullOrThrow(p: (T) -> Boolean = { true }): T? {
  var single: T? = null
  var found = false
  for (element in this) {
    if (p(element)) {
      if (found) {
        throw IllegalArgumentException("Collection contains more than one matching element: $single, $element")
      }
      single = element
      found = true
    }
  }
  return single
}

val REMOTE_KIND_FQN = RPC_FQN.child("RemoteKind".name)

private val REMOTE_OBJECT_FQN = FqName.fromSegments(listOf("fleet", "rpc", "core", "RemoteObject"))
private val REMOTE_RESOURCE_FQN = FqName.fromSegments(listOf("fleet", "rpc", "core", "RemoteResource"))
