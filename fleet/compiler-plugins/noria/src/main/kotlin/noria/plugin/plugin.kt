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
import java.io.File

/**
 * The Noria compiler plugin that transforms @Composable functions to:
 * 1. Reify closures in @Composable and @StabilizedClosure functions for value semantics
 * 2. Add NoriaContext as a parameter
 * 3. Wrap function calls with enterScope/exitScope for caching
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
    FirExtensionRegistrarAdapter.registerExtension(ComposeFirExtensionRegistrar())

    IrGenerationExtension.registerExtension(object : IrGenerationExtension {
      override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.acceptVoid(ComposableLambdaAnnotator(pluginContext))
      }
    })

    // NOTE: the order is important here ReifyClosures should happen before adding NoriaContext as a parameter otherwise it will also be captured
    IrGenerationExtension.registerExtension(object : IrGenerationExtension {
      override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        ComposerLambdaMemoization(pluginContext, moduleFragment).lower(moduleFragment)
      }
    })

    IrGenerationExtension.registerExtension(object : IrGenerationExtension {
      override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        NoriaParamTransformer(pluginContext).lower(moduleFragment)
      }
    })

    IrGenerationExtension.registerExtension(object : IrGenerationExtension {
      override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        ComposerIntrinsicTransformer(pluginContext).lower(moduleFragment)
      }
    })

    // WrapWithScopes should run after adding NoriaContext as a parameter, since we need NoriaContext for wrapping
    IrGenerationExtension.registerExtension(WrapWithScopesPass)

    if (false) {
      IrGenerationExtension.registerExtension(object : IrGenerationExtension {
        override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
          val transformer = object : IrElementTransformerVoid() {
            override fun visitFile(declaration: IrFile): IrFile {
              val filePath = declaration.path
              if (filePath.endsWith(".kt")) {
                File(filePath.removeSuffix(".kt") + "_IR.txt").writeText(declaration.dump())
                File(filePath.removeSuffix(".kt") + "_KT.txt").writeText(declaration.dumpKotlinLike())
              }
              return super.visitFile(declaration)
            }
          }
          moduleFragment.transformChildren(transformer, null)
        }
      })
    }
  }
}