package com.jetbrains.fleet.rpc.plugin.ir

import com.jetbrains.fleet.rpc.plugin.REMOTE_API_DESCRIPTOR_FUNCTION_FQN
import com.jetbrains.fleet.rpc.plugin.ir.util.hasRpcAnnotation
import com.jetbrains.fleet.rpc.plugin.ir.util.irGetObject
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.util.kotlinFqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
class RewriteIntrinsicsPass(
  val irContext: IrPluginContext,
  val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {
  override fun visitCall(expression: IrCall): IrExpression {
    if (expression.symbol.owner.kotlinFqName == REMOTE_API_DESCRIPTOR_FUNCTION_FQN) {
      val apiSymbol = checkNotNull(expression.typeArguments[0]).classOrFail
      if (apiSymbol.owner.hasRpcAnnotation()) {
        val context = FileContext(irContext, messageCollector, currentFile)
        val decriptorSymbol = getDescriptorInstance(context, apiSymbol.owner)
        expression.arguments[0] = irGetObject(decriptorSymbol.owner)
      }
      else {
        messageCollector.report(
          CompilerMessageSeverity.ERROR,
          "Type parameter ${apiSymbol.owner.kotlinFqName} is not marked with @Rpc annotation (compiling remoteDescriptorCall in ${currentFile.fileEntry.name})",
        )
      }
    }
    return super.visitCall(expression)
  }
}
