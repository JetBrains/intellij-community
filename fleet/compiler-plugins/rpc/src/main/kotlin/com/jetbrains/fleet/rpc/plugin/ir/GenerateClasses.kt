package com.jetbrains.fleet.rpc.plugin.ir

import com.jetbrains.fleet.rpc.plugin.ir.util.collectRpcMethods
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.isInterface
import com.jetbrains.fleet.rpc.plugin.ir.util.hasRpcAnnotation
import com.jetbrains.fleet.rpc.plugin.ir.util.isNonSuspendFlowFunction
import com.jetbrains.fleet.rpc.plugin.ir.util.isNonSuspendResourceFunction
import org.jetbrains.kotlin.backend.common.getCompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.util.file


class GenerateClasses(private val context: CompilerPluginContext) : IrElementTransformerVoidWithContext() {
  override fun visitClassNew(declaration: IrClass): IrStatement {
    if (declaration.isInterface && declaration.hasRpcAnnotation()) {
      val rpcMethods = collectRpcMethods(declaration).filter {
        if (it.isSuspend || it.isNonSuspendFlowFunction() || it.isNonSuspendResourceFunction()) {
          true
        }
        else {
          // Forbid unsupported methods
          context.messageCollector.report(
            CompilerMessageSeverity.ERROR,
            "${declaration.name} should either be suspend, return Resource<out RemoteResource> or return Flow<*> to be supported in @Rpc interface",
            it.getCompilerMessageLocation(declaration.file)
          )
          false
        }
      }
      val clientStubClass = buildClientStub(declaration, rpcMethods, context)
      buildRemoteApiDescriptorImpl(declaration, clientStubClass, rpcMethods, context)
    }

    return super.visitClassNew(declaration)
  }
}
