package noria.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * The plugin for compiler that wrap extension functions of Frame class with `enterScope` and `exitScope` calls
 * in order to implement caching strategy based on call place.
 *
 * To enable the plugin set the additional parameter in Kotlin facet configuration.
 * E.g. to enable it for `noria-ui` module add `-Xplugin=$MODULE_DIR$/../compiler-plugin/out/noria-plugin.jar`
 */
@OptIn(ExperimentalCompilerApi::class)
class NoriaCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "noria"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}

@OptIn(ExperimentalCompilerApi::class)
class NoriaComponentRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean = true
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    IrGenerationExtension.registerExtension(ReifyClosures)
    IrGenerationExtension.registerExtension(WrapWithScopesPass)
  }
}