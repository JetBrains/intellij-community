package fleet.multiplatform.expects

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.getCompilerMessageLocation
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.remapTypeParameters
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.utils.addToStdlib.assignFrom
import org.jetbrains.kotlin.utils.addToStdlib.zipWithNulls
import java.util.*

private const val packageName = "fleet.util.multiplatform"
private val actualFqName = FqName("$packageName.Actual")
private val linkToActualFqName = FqName("$packageName.linkToActual")

class ExpectsPluginIrGenerationExtension(val logger: MessageCollector) : IrGenerationExtension {
  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    val expectDeclarations = mutableMapOf<FqName, MutableList<IrFunction>>()
    val actualDeclarations = mutableListOf<IrFunction>()
    var hasFailed = false

    // Collect expect + actual from sources to compile
    moduleFragment.accept(object : IrVisitorVoid() {
      override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)
      }

      override fun visitFunction(declaration: IrFunction) {
        val isExpect = when (val body = declaration.body) {
          is IrBlockBody -> (body.statements.singleOrNull() as? IrReturn)?.value.isLinkToActualFunction()
          is IrExpressionBody -> body.expression.isLinkToActualFunction()
          else -> false
        }

        if (isExpect) {
          expectDeclarations.getOrPut(declaration.kotlinFqName) { mutableListOf() }.add(declaration)
        }
        else if (declaration.hasAnnotation(actualFqName)) {
          actualDeclarations.add(declaration)
        }

        super.visitFunction(declaration)
      }
    }, null)

    val usedActuals = expectDeclarations.flatMap { (fqName, expectOverloads) ->
      // Search for actual with matching FqName once
      val actualFqName = fqName.toActualName(pluginContext.platform)
      val actualOverloads = pluginContext.referenceFunctions(
        CallableId(
          packageName = actualFqName.parent(),
          callableName = actualFqName.shortName()
        )
      ).also { afns ->
        expectOverloads.forEach { e ->
          afns.forEach { a ->
            pluginContext.recordLookup(a.owner, e.file)
          }
        }
      }
        .filter { it.owner.hasAnnotation(fleet.multiplatform.expects.actualFqName) }
        .map { it.owner }

      if (actualOverloads.isEmpty()) {
        expectOverloads.forEach { expect ->
          reportError(
            "no `@Actual fun ${actualFqName}()` found, cannot link `linkToActual()`",
            expect
          )
          hasFailed = true
        }

        emptyList()
      }
      else {
        expectOverloads.mapNotNull { expect ->
          val actual = actualOverloads.findMatching(expect)

          if (actual == null) {
            reportError(
              "none of existing `@Actual fun $actualFqName()` have compatible signature, cannot link `linkToActual()`",
              expect
            )
            hasFailed = true
          }
          else {
            // Link them manually
            expect.body = with(DeclarationIrBuilder(pluginContext, expect.symbol)) {
              irBlockBody {
                +irReturn(
                  irCall(actual).also { call ->
                    call.arguments.assignFrom(expect.parameters.map(::irGet))
                    call.typeArguments.assignFrom(expect.typeParameters.map { it.defaultType })
                  })
              }
            }
          }

          actual
        }
      }
    }

    // Check that actual functions are mapped somewhere as well (already compiled code or from the mapping above)
    actualDeclarations
      // No need to run search for those already mapped
      .minus(usedActuals)
      .forEach { actual ->
        if (!actual.name.identifier.endsWith(pluginContext.platform.specifier)) {
          reportError(
            "function ${actual.kotlinFqName} marked with @${actualFqName.shortName()} should have a name that ending with `${pluginContext.platform.specifier}`",
            actual
          )
          hasFailed = true
        }
        else {
          // Search in compiled files for matching method
          val expectFqn = actual.kotlinFqName.toExpectName(pluginContext.platform)

          // We cannot really check for calls of `linkToActual` here since it's compiled / substituted
          val sameFqName = pluginContext.referenceFunctions(
            CallableId(
              packageName = expectFqn.parent(),
              callableName = expectFqn.shortName()
            )
          ).also { efns ->
            efns.forEach { e ->
              pluginContext.recordLookup(e.owner, actual.file)
            }
          }

          if (sameFqName.isEmpty()) {
            reportError(
              "no `fun ${expectFqn.shortName()}()` calling ${linkToActualFqName.shortName()}() found, invalid `@Actual` usage",
              actual
            )
            hasFailed = true
          }
          else {
            val matching = sameFqName.firstOrNull {
              it.owner.matchesWith(actual)
            }

            if (matching == null) {
              reportError(
                "none of existing `fun ${expectFqn.shortName()}()` has compatible signature, invalid `@Actual` usage",
                actual
              )
              hasFailed = true
            }
          }
        }
      }

    if (hasFailed) {
      // Need to throw, or compilation wouldn't be canceled (incremental compilation issues)
      error("issues with @Actual and `linkToActual`, please check compilation errors for details")
    }
  }

  private fun reportError(message: String, declaration: IrFunction) {
    logger.report(
      CompilerMessageSeverity.ERROR,
      message,
      declaration.getCompilerMessageLocation(declaration.file)
    )
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrExpression?.isLinkToActualFunction(): Boolean {
  return when (this) {
    is IrCall -> symbol.owner.kotlinFqName == linkToActualFqName
    else -> false
  }
}

// Compares signatures
private fun IrFunction.matchesWith(to: IrFunction): Boolean {
  // Description kept for debugging purposes
  val problem = when {
    to.parameters.size != parameters.size -> "non matching number of parameters"
    to.typeParameters.size != typeParameters.size -> "non matching number of type parameters"
    to.returnType != returnType.remapTypeParameters(this, to) -> "non matching return type"

    !to.parameters.zipWithNulls(parameters).all { (expect, actual) ->
      expect.matchesWith(actual, this, to)
    } -> "non matching parameters"

    !to.typeParameters.zipWithNulls(typeParameters).all { (expect, actual) ->
      expect?.variance == actual?.variance && expect?.superTypes == actual?.superTypes?.map { it.remapTypeParameters(this, to) }
    } -> "non matching type parameters"

    else -> null
  }

  return problem == null
}

private fun IrValueParameter?.matchesWith(
  actual: IrValueParameter?,
  thisParams: IrTypeParametersContainer,
  actualParams: IrTypeParametersContainer,
): Boolean {
  return when {
    this != null && actual == null -> false
    this == null && actual != null -> false
    this?.kind != actual?.kind -> false
    this?.type != actual?.type?.remapTypeParameters(thisParams, actualParams) -> false
    else -> true
  }
}

private fun Iterable<IrFunction>.findMatching(to: IrFunction): IrFunction? {
  return firstOrNull { actual ->
    actual.matchesWith(to)
  }
}

private val TargetPlatform?.specifier: String
  get() {
    // TODO check this doesn't hurt in KMP or use "Impl"
    return when (val single = this?.singleOrNull()) {
      null -> "Impl"
      else -> single.platformName.split("-").joinToString("") {
        it.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      }
    }
  }

private fun FqName.toActualName(platform: TargetPlatform?): FqName = parent()
  .child(Name.identifier(shortName().identifier + platform.specifier))

private fun FqName.toExpectName(platform: TargetPlatform?): FqName = parent()
  .child(Name.identifier(shortName().identifier.removeSuffix(platform.specifier)))