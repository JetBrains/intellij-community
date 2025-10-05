package com.jetbrains.fleet.rpc.plugin.ir

import com.jetbrains.fleet.rpc.plugin.ir.util.Cache
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class ServiceGenerationExtension(private val messageCollector: MessageCollector) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val context = CompilerPluginContext(moduleFragment, pluginContext, messageCollector, Cache())
    moduleFragment.accept(GenerateClasses(context), null)
    moduleFragment.accept(RemoteApiDescriptorTransform(), null)
  }
}

data class CompilerPluginContext(val moduleFragment: IrModuleFragment,
                                 val pluginContext: IrPluginContext,
                                 val messageCollector: MessageCollector,
                                 val cache: Cache)