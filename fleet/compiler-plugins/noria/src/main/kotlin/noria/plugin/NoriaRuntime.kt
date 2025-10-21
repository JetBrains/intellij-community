package noria.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class NoriaRuntime {
  companion object {
    val OptOutFromOuterScopes = FqName.fromSegments(listOf("noria", "OptOutFromOuterScopes"))
    val OptOutFromInnerScopes = FqName.fromSegments(listOf("noria", "OptOutFromInnerScopes"))
    val NoriaContextFqn = FqName.fromSegments(listOf("noria", "NoriaContext"))
    val ClosureContextFqn = FqName.fromSegments(listOf("noria", "ClosureContext"))
    val NoriaRuntimeFqn = FqName.fromSegments(listOf("noria", "impl"))
  }
}

internal fun funByName(
  name: String,
  prefix: FqName,
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment
): IrSimpleFunctionSymbol {
  val callableId = CallableId(prefix, Name.identifier(name))
  return pluginContext.referenceFunctions(callableId).singleOrNull()
         ?: throw SymbolNotFoundException(callableId.toString(), moduleFragment)
}

class SymbolNotFoundException(fqn: String, module: IrModuleFragment) :
  RuntimeException("$fqn cannot be resolved from module `${module.name.asString()}`.")
