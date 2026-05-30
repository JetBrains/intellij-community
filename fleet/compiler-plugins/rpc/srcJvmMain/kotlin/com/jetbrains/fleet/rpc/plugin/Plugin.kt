package com.jetbrains.fleet.rpc.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class RpcCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "rpc-compiler-plugin"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}
