package com.jetbrains.fleet.rpc.plugin.ir.util

import com.jetbrains.fleet.rpc.plugin.ir.remoteKind.FLOW_FQN
import com.jetbrains.fleet.rpc.plugin.ir.remoteKind.RESOURCE_FQN
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.superTypes

fun collectRpcMethods(irClass: IrClass): List<IrSimpleFunction> {
  return irClass.allSuperInterfaces().flatMap { superType ->
    getRpcMethods(superType)
  }
}

/**
 * /not suspend/ fun(): Flow<*>
 */
fun IrSimpleFunction.isNonSuspendFlowFunction(): Boolean {
  return !isSuspend && returnType.isClassType(FLOW_FQN.toUnsafe(), false)
}

/**
 * /not suspend/ fun(): Resource<out RemoteResource>
 */
fun IrSimpleFunction.isNonSuspendResourceFunction(): Boolean {
  return when {
    isSuspend -> false
    !returnType.isClassType(RESOURCE_FQN.toUnsafe(), false) -> false
    else -> {
      when (val arg = (returnType as IrSimpleType).arguments.first()) {
        is IrStarProjection -> false
        is IrTypeProjection -> {
          arg.type.superTypes().any { it.classFqName == REMOTE_RESOURCE_FQN }
        }
      }
    }
  }
}

private val FUN_NAMES_TO_SKIP = listOf("equals", "hashCode", "toString", "dispatch", "signature")

private fun getRpcMethods(rpcClass: IrClass): List<IrSimpleFunction> {
  return rpcClass.declarations.filterIsInstance<IrSimpleFunction>().filter { declaration ->
    !declaration.isFakeOverride && declaration.overriddenSymbols.isEmpty() && declaration.name.identifier !in FUN_NAMES_TO_SKIP
  }
}
