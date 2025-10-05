package com.jetbrains.fleet.rpc.plugin.ir.remoteKind

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import com.jetbrains.fleet.rpc.plugin.ir.CompilerPluginContext
import com.jetbrains.fleet.rpc.plugin.ir.util.RPC_FQN
import com.jetbrains.fleet.rpc.plugin.ir.util.name
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.isNullable

internal fun IrBuilderWithScope.handleSpecialTypes(irType: IrType, context: CompilerPluginContext, debugInfo: String): IrExpression? {
  return context.fqnToRemoteKind(irType.classFqName)?.let { constructor ->
    val elementKindType = (irType as IrSimpleType).arguments.first().typeOrFail
    val remoteKind = toRemoteKind(elementKindType, context, debugInfo)
    irCallConstructor(constructor, listOf(remoteKind.type)).apply {
      arguments[0] = remoteKind
      arguments[1] = irBoolean(irType.isNullable())
    }
  }
}

private fun CompilerPluginContext.fqnToRemoteKind(fqName: FqName?) = cache.remember {
  val remoteKindFlowConstructor = pluginContext.referenceClass(
    ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Flow")), false)
  )!!.constructors.first()

  val remoteKindReceiveChannelConstructor = pluginContext.referenceClass(
    ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "ReceiveChannel")), false)
  )!!.constructors.first()

  val remoteKindSendChannelConstructor = pluginContext.referenceClass(
    ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "SendChannel")), false)
  )!!.constructors.first()

  val remoteKindDeferredConstructor = pluginContext.referenceClass(
    ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Deferred")), false)
  )!!.constructors.first()

  mapOf(
    FLOW_FQN to remoteKindFlowConstructor,
    RECEIVE_CHANNEL_FQN to remoteKindReceiveChannelConstructor,
    SEND_CHANNEL_FQN to remoteKindSendChannelConstructor,
    DEFERRED_FQN to remoteKindDeferredConstructor,
  )
}[fqName]


internal val FLOW_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "flow", "Flow"))
internal val RESOURCE_FQN = FqName.fromSegments(listOf("fleet", "util", "async", "Resource"))
private val RECEIVE_CHANNEL_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "channels", "ReceiveChannel"))
private val SEND_CHANNEL_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "channels", "SendChannel"))
private val DEFERRED_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "Deferred"))
