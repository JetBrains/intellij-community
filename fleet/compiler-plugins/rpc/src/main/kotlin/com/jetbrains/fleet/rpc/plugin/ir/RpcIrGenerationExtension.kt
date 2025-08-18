package com.jetbrains.fleet.rpc.plugin.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

class RpcIrGenerationExtension(private val messageCollector: MessageCollector) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.accept(GenerateDescriptorImplsPass(pluginContext, messageCollector), null)
    moduleFragment.accept(RewriteIntrinsicsPass(pluginContext, messageCollector), null)
  }
}

data class FileContext(
  private val irContext: IrPluginContext,
  val messageCollector: MessageCollector,
  val currentFile: IrFile,
) {
  val irBuiltIns: IrBuiltIns get() = irContext.irBuiltIns
  val irFactory: IrFactory get() = irContext.irFactory

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  fun referenceClass(classId: ClassId): IrClassSymbol? =
    irContext.referenceClass(classId)?.also {
      irContext.recordLookup(it.owner, currentFile)
    }

  @UnsafeDuringIrConstructionAPI
  fun referenceFunctions(callableId: CallableId): Collection<IrSimpleFunctionSymbol> =
    irContext.referenceFunctions(callableId).also { fns ->
      fns.forEach {
        irContext.recordLookup(it.owner, currentFile)
      }
    }

  @UnsafeDuringIrConstructionAPI
  fun referenceProperties(callableId: CallableId): Collection<IrPropertySymbol> =
    irContext.referenceProperties(callableId).also { props ->
      props.forEach {
        irContext.recordLookup(it.owner, currentFile)
      }
    }
}
