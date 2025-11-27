package noria.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class NoriaRuntime {
  companion object {
    val ExplicitGroupsComposable = FqName.fromSegments(listOf("androidx", "compose", "runtime", "ExplicitGroupsComposable"))
    val ReadOnlyComposable = FqName.fromSegments(listOf("androidx", "compose", "runtime", "ReadOnlyComposable"))
    val NoriaContextFqn = FqName.fromSegments(listOf("noria", "NoriaContext"))
    val NoriaRuntimeFqn = FqName.fromSegments(listOf("noria", "impl"))
    val RTsuspendClosureFqn = FqName.fromSegments(listOf("noria", "impl", "RTsuspendClosure"))
    val RTclosureFqn = FqName.fromSegments(listOf("noria", "impl", "RTclosure"))
    val CurrentNoriaContextIntrinsic = FqName.fromSegments(listOf("noria", "<get-currentNoriaContext>"))
    val ComposableClassId: ClassId = ClassId(
      FqName("androidx.compose.runtime"),
      Name.identifier("Composable")
    )
    val ValueLambdaFqName: FqName = FqName("noria.ValueLambda")
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
