package com.jetbrains.fleet.rpc.plugin

import com.jetbrains.fleet.rpc.plugin.ir.ServiceGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class RpcComponentRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String
    get() = "rpc-compiler-plugin"
  override val supportsK2: Boolean = true
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    IrGenerationExtension.registerExtension(RpcIrGenerationExtension(messageCollector))
    FirExtensionRegistrarAdapter.registerExtension(RpcFirExtensionRegistrar())
  }
}