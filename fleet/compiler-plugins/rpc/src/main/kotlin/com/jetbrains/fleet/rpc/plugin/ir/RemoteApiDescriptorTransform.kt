package com.jetbrains.fleet.rpc.plugin.ir

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import com.jetbrains.fleet.rpc.plugin.ir.util.RPC_FQN
import com.jetbrains.fleet.rpc.plugin.ir.util.name
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.types.classOrNull

class RemoteApiDescriptorTransform : IrElementTransformerVoidWithContext() {
  override fun visitCall(expression: IrCall): IrExpression {
    if (expression.symbol.owner.kotlinFqName == REMOTE_API_DESCRIPTOR_FUNCTION_FQN) {
      val descriptorInstance = getRemoteApiDescriptorInstance(checkNotNull(expression.typeArguments[0])) {
        currentDeclarationParent?.dumpKotlinLike()
      }
      expression.arguments[0] = descriptorInstance
    }
    return super.visitCall(expression)
  }
}

fun getRemoteApiDescriptorInstance(type: IrType, debugInfo: () -> String?): IrExpression {
  val irClass = requireNotNull(type.classOrNull) {
    "Error in RPC compiler plugin: can't get class by type ${type.dumpKotlinLike()}, error occured in\n${debugInfo()}"
  }.owner

  val implClass =
    IrFactoryImpl.buildClass {
      name = irClass.name.getRemoteApiDescriptorImplName()
      kind = ClassKind.OBJECT
    }.apply {
      parent = irClass.parent
      createThisReceiverParameter()
    }

  return IrGetObjectValueImpl(
    startOffset = UNDEFINED_OFFSET,
    endOffset = UNDEFINED_OFFSET,
    type = implClass.defaultType,
    symbol = implClass.symbol
  )
}

fun Name.getRemoteApiDescriptorImplName() = Name.identifier(identifier + "RemoteApiDescriptorImpl")

val REMOTE_API_DESCRIPTOR_FUNCTION_FQN = RPC_FQN.child("remoteApiDescriptor".name)
