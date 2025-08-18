package com.jetbrains.fleet.rpc.plugin.ir.remoteKind

import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_DATA_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_REMOTE_OBJECT_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_RESOURCE_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_OBJECT_FQN
import com.jetbrains.fleet.rpc.plugin.REMOTE_RESOURCE_FQN
import com.jetbrains.fleet.rpc.plugin.THROWING_SERIALIZER_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.ir.FileContext
import com.jetbrains.fleet.rpc.plugin.ir.getDescriptorInstance
import com.jetbrains.fleet.rpc.plugin.ir.singleOrNullOrThrow
import com.jetbrains.fleet.rpc.plugin.ir.util.name
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName

@UnsafeDuringIrConstructionAPI
fun IrBuilderWithScope.toRemoteKind(
  irType: IrType,
  context: FileContext,
  debugInfo: String,
): IrExpression {
  return handleSpecialTypes(irType, context, debugInfo)
    ?: handleRemoteObject(irType, context)
    ?: handleResource(irType, context)
    ?: run { // RemoteKind.Data
      val serializer = generateSerializerCall(irType, context, debugInfo)
      irCallConstructor(context.referenceClass(REMOTE_KIND_DATA_CLASS_ID)!!.constructors.single(), listOf(serializer.type)).apply {
        arguments[0] = serializer
      }
    }
}

@UnsafeDuringIrConstructionAPI
private fun IrBuilderWithScope.handleResource(
  irType: IrType,
  context: FileContext,
): IrExpression? {
  return if (irType.classFqName == RESOURCE_FQN) {
    // Get the class of the type argument
    val serviceType = ((irType as? IrSimpleType)?.arguments?.singleOrNull() as? IrTypeProjection)?.type
    val serviceIrClass = serviceType?.classOrNull?.owner

    if (serviceIrClass != null && serviceIrClass.allSuperInterfaces().any { it.kotlinFqName == REMOTE_RESOURCE_FQN }) {
      val remoteApiDescriptor = getDescriptorInstance(context, serviceType.classOrFail.owner)
      val remoteKindResourceConstructor = context.referenceClass(REMOTE_KIND_RESOURCE_CLASS_ID)!!.constructors.first()
      irCallConstructor(remoteKindResourceConstructor, listOf(remoteApiDescriptor.owner.defaultType))
        .apply {
          arguments[0] = irGetObject(remoteApiDescriptor)
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

@UnsafeDuringIrConstructionAPI
private fun IrBuilderWithScope.handleRemoteObject(irType: IrType, context: FileContext): IrExpression? {
  val serviceIrClass = irType.classOrFail.owner

  return if (serviceIrClass.allSuperInterfaces().any { it.kotlinFqName == REMOTE_OBJECT_FQN }) {
    val remoteApiDescriptor = getDescriptorInstance(context, serviceIrClass)
    val remoteKindRemoteObjectConstructor = context.referenceClass(REMOTE_KIND_REMOTE_OBJECT_CLASS_ID)!!.constructors.first()
    irCallConstructor(remoteKindRemoteObjectConstructor, listOf(remoteApiDescriptor.owner.defaultType)).apply {
      arguments[0] = irGetObject(remoteApiDescriptor)
    }
  }
  else {
    null
  }
}

@UnsafeDuringIrConstructionAPI
private fun IrBuilderWithScope.generateSerializerCall(
  irType: IrType,
  context: FileContext,
  debugInfo: String,
  isTypeArgument: Boolean = false,
): IrExpression {
  val irClass = irType.classOrFail.owner

  // fallback to the class itself, for example Unit serializer() is defined on Unit since it has no Companion
  val companionOrClass = irClass.companionObject() ?: irClass

  val serializerCall =
    // check if the class is one of the special ones that don't have serializer on Companion (List/Set/etc)
    context.getBuiltInSerializer(irClass.kotlinFqName)?.let { irCall(it) }
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
      val throwingSerializerConstructor = context.referenceClass(THROWING_SERIALIZER_CLASS_ID)!!.constructors.single()
      irCallConstructor(throwingSerializerConstructor, listOf(this.context.irBuiltIns.stringType)).apply {
        val errorString = "ThrowingSerializer was used for serialization/deserialization. " +
          "This could happen if there is a @Serializable class with non-serializable type argument in rpc interface.\n" +
          "Additional info:\n$debugInfo"
        arguments[0] = irString(errorString)
      }
    }
    else {
      error(message)
    }
  }
  else if (irType.isNullable()) {
    val nullableSerializerProperty = context.referenceProperties(nullableSerializerProperty).first().owner
    irCall(nullableSerializerProperty.getter!!).apply {
      insertExtensionReceiver(serializer)
    }
  }
  else {
    serializer
  }
}

// find .serializer() as an extension function
@UnsafeDuringIrConstructionAPI
private fun IrBuilderWithScope.findBuiltinSerializerExtensionFunctions(
  companionOrClass: IrClass,
  context: FileContext,
): IrFunctionAccessExpression? {
  val serializerFunction = context.referenceFunctions(CallableId(
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
@UnsafeDuringIrConstructionAPI
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

private val nullableSerializerProperty = CallableId(
  packageName = FqName.fromSegments(listOf("kotlinx", "serialization", "builtins")),
  className = null,
  callableName = "nullable".name
)
