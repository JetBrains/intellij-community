package fleet.multiplatform.expects

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector

@OptIn(ExperimentalCompilerApi::class)
class ExpectsPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    // Backend plugin
    IrGenerationExtension.registerExtension(
      ExpectsPluginIrGenerationExtension(
        logger = configuration.messageCollector
      )
    )
  }
}
