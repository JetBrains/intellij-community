package com.jetbrains.fleet.rpc.plugin.ir.remoteKind

import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_DEFERRED_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_FLOW_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_RECEIVE_CHANNEL_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.REMOTE_KIND_SEND_CHANNEL_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.ir.FileContext
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.isNullable

@UnsafeDuringIrConstructionAPI
internal fun IrBuilderWithScope.handleSpecialTypes(
  irType: IrType,
  context: FileContext,
  debugInfo: String,
): IrExpression? {
  return context.getRemoteKindConstructor(irType.classFqName)?.let { constructor ->
    val elementKindType = (irType as IrSimpleType).arguments.first().typeOrFail
    val remoteKind = toRemoteKind(elementKindType, context, debugInfo)
    irCallConstructor(constructor, listOf(remoteKind.type)).apply {
      arguments[0] = remoteKind
      arguments[1] = irBoolean(irType.isNullable())
    }
  }
}

@UnsafeDuringIrConstructionAPI
private fun FileContext.getRemoteKindConstructor(fqName: FqName?): IrConstructorSymbol? =
  when (fqName) {
    FLOW_FQN -> referenceClass(REMOTE_KIND_FLOW_CLASS_ID)!!.constructors.first()
    RECEIVE_CHANNEL_FQN -> referenceClass(REMOTE_KIND_RECEIVE_CHANNEL_CLASS_ID)!!.constructors.first()
    SEND_CHANNEL_FQN -> referenceClass(REMOTE_KIND_SEND_CHANNEL_CLASS_ID)!!.constructors.first()
    DEFERRED_FQN -> referenceClass(REMOTE_KIND_DEFERRED_CLASS_ID)!!.constructors.first()

    else -> null
  }

internal val FLOW_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "flow", "Flow"))
internal val RESOURCE_FQN = FqName.fromSegments(listOf("fleet", "util", "async", "Resource"))
private val RECEIVE_CHANNEL_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "channels", "ReceiveChannel"))
private val SEND_CHANNEL_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "channels", "SendChannel"))
private val DEFERRED_FQN = FqName.fromSegments(listOf("kotlinx", "coroutines", "Deferred"))
