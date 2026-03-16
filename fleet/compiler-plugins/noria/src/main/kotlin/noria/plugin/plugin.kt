package noria.plugin

import noria.plugin.k2.ComposeFirExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.platform.jvm.isJvm
import java.io.File

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
