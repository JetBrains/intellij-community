package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
data class DevPluginInputOwnership(
  @JvmField val callbackInputs: List<Path>,
  @JvmField val remainderInputs: List<Path>,
)

/**
 * The operation kinds that need the callback preparer, the executable with the layout callbacks on its classpath.
 * `dev_plugin_preparation` picks that executable when the plan file holds one of them, and states the choice as
 * `--callback-preparation`, which the preparer checks against the operations.
 */
@ApiStatus.Internal
val CALLBACK_PREPARER_OPERATION_KINDS: Set<String> = java.util.Set.of("library-layout-filter", "library-layout-patches", "native-presigned")

/**
 * The layout-assets transforms the Go remainder packer executes, with the plain copy of a `null` transform.
 * `gzip-xml-archive` stays a Kotlin preparation, because no pure-Go compressor reproduces the JDK deflate bytes.
 */
@ApiStatus.Internal
val GO_LAYOUT_TRANSFORMS: Set<String> = java.util.Set.of("archive-tree", "tree-map", "inline-text")

/**
 * The one statement of what the Go remainder packer executes from the recipe. The generator, the action compiler and
 * the recipe emitter call it. A `module-filter` operation is Go-executed. A `layout-assets` operation is Go-executed
 * when its format is `tree` or `entries` and every transform is in [GO_LAYOUT_TRANSFORMS]. Such an operation gets no
 * Kotlin action, and the Kotlin preparer holds no executor for it; [prepareDevPlugin] compiles it into the operations
 * of the recipe. A chain whose operations are all Go-executed declares no preparation target, and the Go packer reads
 * its plan file directly.
 */
@ApiStatus.Internal
fun isGoExecutedOperation(operation: DevPluginPreparationOperation): Boolean {
  if (operation.kind == "module-filter") return true
  if (operation.kind != "layout-assets") return false
  val layoutAssets = requireNotNull(operation.layoutAssets) { "A layout-assets operation requires layout assets" }
  return (layoutAssets.format == "tree" || layoutAssets.format == "entries") &&
         layoutAssets.assets.all { asset -> asset.transform?.let { it.kind in GO_LAYOUT_TRANSFORMS } ?: true }
}

/**
 * True when the operation reads the chain's platform from the plan's `variant` at run time. A `native-presigned`
 * operation selects the native entries of its target platform. A plugin with such an operation is never neutral.
 * The generator keeps one record and one chain per platform, so the preparer always parses a real platform id.
 */
@ApiStatus.Internal
fun readsPlatform(operation: DevPluginPreparationOperation): Boolean = operation.kind == "native-presigned"

/**
 * The preparer a chain needs: `callback` when an operation kind is in [CALLBACK_PREPARER_OPERATION_KINDS], `plain`
 * when an operation is not Go-executed, else `none`. The generator writes it as the `preparation` argument of
 * `dev_dist_complex_plugin`.
 */
@ApiStatus.Internal
fun devPluginPreparationKind(operations: List<DevPluginPreparationOperation>): String {
  return when {
    operations.any { it.kind in CALLBACK_PREPARER_OPERATION_KINDS } -> "callback"
    operations.any { !isGoExecutedOperation(it) } -> "plain"
    else -> "none"
  }
}

@ApiStatus.Internal
fun prepareDevPluginFromProjection(
  projectionFile: Path,
  artifactCatalogueFile: Path,
  descriptorFile: Path,
  pluginDirectory: Path,
  outputDirectory: Path,
  inputOwnership: DevPluginInputOwnership? = null,
  catalogueOutputDirectory: Path = outputDirectory,
  expectedExecutionVersion: Int? = null,
  expectedCallbackPreparation: Boolean? = null,
): DevPluginPreparationResult {
  val projection = Json.decodeFromString<PluginPackingProjection>(Files.readString(projectionFile))
  val executionVersion = pluginPackingExecutionVersion(projection.assets)
  require(expectedExecutionVersion == null || expectedExecutionVersion == executionVersion) {
    "Plugin '${projection.plugin}' has a stale execution version: declared=$expectedExecutionVersion, required=$executionVersion. " +
    "Regenerate the dev distribution declarations."
  }
  val plan = projection.plan()
  val catalogue = Json.decodeFromString<DevPluginArtifactCatalogue>(Files.readString(artifactCatalogueFile))
  validateDevPluginPreparationOutputs(plan, catalogue)
  val recipe = DevPluginPreparationRecipe(DEV_PLUGIN_PREPARATION_FORMAT, projection.operations)
  val callbackPreparation = recipe.operations.any { it.kind in CALLBACK_PREPARER_OPERATION_KINDS }
  require(expectedCallbackPreparation == null || expectedCallbackPreparation == callbackPreparation) {
    "The declared callback preparation differs from the plan file: declared=$expectedCallbackPreparation, actual=$callbackPreparation. " +
    "Regenerate the dev distribution declarations."
  }
  val actions = compileDevPluginPreparationActions(recipe, plan, catalogue)
  val derivation = deriveDevPluginInputs(plan, catalogue.toPlanCatalogue(), recipe.operations)
  if (inputOwnership != null) {
    validateInputOwnership(plan, derivation, catalogue, inputOwnership)
  }
  return prepareDevPlugin(
    plan = plan,
    runtimeLayoutSignature = projection.layoutSignature,
    remainderInputIds = derivation.inputs,
    catalogue = catalogue,
    cachedDescriptorContent = Files.readAllBytes(descriptorFile),
    pluginDirectory = pluginDirectory,
    outputDirectory = outputDirectory,
    preparationActions = actions,
    catalogueOutputDirectory = catalogueOutputDirectory,
    goExecutedOperations = recipe.operations.filter(::isGoExecutedOperation),
  )
}

/** Checks the declared action inputs against the one derivation the generator wrote them from. */
private fun validateInputOwnership(
  plan: PluginPackingPlan,
  derivation: DevPluginInputDerivation,
  catalogue: DevPluginArtifactCatalogue,
  ownership: DevPluginInputOwnership,
) {
  val inputs = PreparationCatalogue(catalogue)
  fun validate(kind: String, identifiers: Collection<String>, declared: List<Path>) {
    val expected = identifiers.mapTo(HashSet()) { identifier -> Path.of(inputs.artifact(identifier).root).toAbsolutePath().normalize() }
    val actual = declared.map { it.toAbsolutePath().normalize() }
    require(actual.size == actual.toSet().size && actual.toSet() == expected) {
      "Plugin '${plan.plugin}' has stale $kind action inputs: expected=$expected, actual=$actual. Regenerate the dev distribution declarations."
    }
  }
  validate("callback", derivation.preparationInputs, ownership.callbackInputs)
  validate("remainder", derivation.remainderInputs, ownership.remainderInputs)
}
