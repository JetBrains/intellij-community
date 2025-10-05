package com.jetbrains.fleet.rpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import com.jetbrains.fleet.rpc.plugin.ir.ServiceGenerationExtension
import org.jetbrains.kotlin.config.CommonConfigurationKeys

@OptIn(ExperimentalCompilerApi::class)
class RpcCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "rpc"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}

@OptIn(ExperimentalCompilerApi::class)
class RpcComponentRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean = true
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    IrGenerationExtension.registerExtension(ServiceGenerationExtension(messageCollector))
  }
}