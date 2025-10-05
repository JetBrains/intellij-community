package com.jetbrains.rhizomedb.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class RhizomedbComponentRegistrar(
  private val readProvider: ((String) -> List<String>)? = null,
  private val writeProvider: ((String, Collection<String>) -> Unit)? = null
) : CompilerPluginRegistrar() {
  override val supportsK2: Boolean = true
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val output = getJvmOutputDir(configuration)
    IrGenerationExtension.registerExtension(
      EntityTypeRegistrationGenerator(
        jvmOutputDir = output,
        readProvider = readProvider,
        writeProvider = writeProvider
      )
    )
  }
}
