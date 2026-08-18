package com.jetbrains.fleet.rpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.config.CompilerConfiguration
import com.jetbrains.fleet.rpc.plugin.fir.RpcFirExtensionRegistrar
import com.jetbrains.fleet.rpc.plugin.ir.RpcIrGenerationExtension

@OptIn(ExperimentalCompilerApi::class)
class RpcComponentRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String
    get() = "rpc-compiler-plugin"
  override val supportsK2: Boolean = true
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    IrGenerationExtension.registerExtension(RpcIrGenerationExtension(configuration))
    FirExtensionRegistrarAdapter.registerExtension(RpcFirExtensionRegistrar())
  }
}