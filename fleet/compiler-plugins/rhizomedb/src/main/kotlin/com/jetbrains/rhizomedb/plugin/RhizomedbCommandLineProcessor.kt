package com.jetbrains.rhizomedb.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys

@OptIn(ExperimentalCompilerApi::class)
class RhizomedbCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "rhizomedb"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}

// todo - support list of modules??
fun getJvmOutputDir(configuration: CompilerConfiguration) =
  configuration[JVMConfigurationKeys.MODULES]?.singleOrNull()?.getOutputDirectory()
  ?: configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)?.absolutePath
