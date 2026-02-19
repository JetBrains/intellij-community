package com.jetbrains.fleet.rpc.plugin.ir.util

import com.jetbrains.fleet.rpc.plugin.RPC_ANNOTATION_FQN
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.isAnnotationWithEqualFqName

fun IrClass.hasRpcAnnotation() =
  this.annotations.any { it.isAnnotationWithEqualFqName(RPC_ANNOTATION_FQN) }

fun getProxyType(irBuiltIns: IrBuiltIns): IrSimpleType { // typealias Proxy = suspend (String, Array<Any?>) -> Any?
  val stringType = irBuiltIns.stringType
  val nullableAnyType = irBuiltIns.anyNType.makeNullable()
  val anyArrayType = irBuiltIns.arrayClass.typeWith(nullableAnyType)

  return irBuiltIns.suspendFunctionN(2)
    .typeWith(/*thisType, */stringType, anyArrayType, nullableAnyType)
}
