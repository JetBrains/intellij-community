package noria.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * The Noria compiler plugin that transforms @Composable functions to:
 * 1. Reify closures in @Composable and @StabilizedClosure functions for value semantics
 * 2. Add NoriaContext as a parameter
 * 3. Wrap function calls with enterScope/exitScope for caching
 */
@OptIn(ExperimentalCompilerApi::class)
class NoriaCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "noria-compiler-plugin"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}
