package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.devDistSignature

private val preparationRecipeJson = Json { encodeDefaults = true }

/** The recipe format of every generated operation. The generator hashes an operation with this version. */
@ApiStatus.Internal
const val DEV_PLUGIN_PREPARATION_FORMAT: Int = 2

/** The `operations` of a plan file. The remainder packer executes every operation; no Kotlin action runs. */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginPreparationRecipe(
  @EncodeDefault(EncodeDefault.Mode.ALWAYS) @JvmField val version: Int = 1,
  @JvmField val operations: List<DevPluginPreparationOperation>,
)

/**
 * One operation of a plan file. The one kind is `layout-assets`: the operation writes a tree, a file or jar entries
 * from [layoutAssets] over its [inputs].
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginPreparationOperation(
  @JvmField val id: String,
  @JvmField val kind: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val inputs: List<DevPluginReference> = emptyList(),
  @JvmField val output: String,
  @JvmField val manifest: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val filter: String = "",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val layoutAssets: DevPluginLayoutAssetPreparation? = null,
)

/** The layout-assets transforms the remainder packer executes, with the plain copy of a `null` transform. */
@ApiStatus.Internal
val GO_LAYOUT_TRANSFORMS: Set<String> = java.util.Set.of("archive-tree")

/**
 * The one statement of what the remainder packer executes from a plan file. A `layout-assets` operation in every
 * layout format (`tree`, `entries`) with every transform in [GO_LAYOUT_TRANSFORMS] is packer-executed.
 * [devPluginPreparationOperationSignature] refuses every other operation, so a plan file never holds one. The chain of
 * a complex plugin declares no preparation target, and the packer reads the plan file directly.
 */
@ApiStatus.Internal
fun isPackerExecutedOperation(operation: DevPluginPreparationOperation): Boolean {
  if (operation.kind != "layout-assets") return false
  val layoutAssets = requireNotNull(operation.layoutAssets) { "A layout-assets operation requires layout assets" }
  return layoutAssets.format in setOf("tree", "entries") &&
         layoutAssets.assets.all { asset -> asset.transform?.let { it.kind in GO_LAYOUT_TRANSFORMS } ?: true }
}

/**
 * Returns the SHA-256 signature for [PluginPackingPreparation.modelSignature].
 * The canonical input is compact UTF-8 JSON for a recipe with this operation alone, in declaration order.
 * Default never-encoded fields are omitted. Other defaults are included. The recipe version is part of the signature.
 */
@ApiStatus.Internal
fun devPluginPreparationOperationSignature(
  operation: DevPluginPreparationOperation,
  version: Int = 1,
): String {
  require(version in 1..2) { "Unsupported preparation recipe version $version" }
  validatePreparationOperation(operation, version)
  val configuration = preparationRecipeJson.encodeToString(DevPluginPreparationRecipe(version, listOf(operation)))
  return devDistSignature { putString(configuration) }
}

/**
 * A layout-assets output in the `tree` format has one consumer: the tree asset at the layout root.
 * An `entries` output is a prepared jar source, which the jar recipe validation checks.
 */
@ApiStatus.Internal
fun validateDevPluginLayoutAssetConsumers(operation: DevPluginPreparationOperation, plan: PluginPackingPlan) {
  val layoutAssets = requireNotNull(operation.layoutAssets)
  if (layoutAssets.format == "entries") return
  val consumers = plan.assets.filter { operation.output in it.asset.inputs }
  val consumer = consumers.singleOrNull()
  require(
    consumer != null && consumer.artifact == null && consumer.asset.kind == layoutAssets.format &&
    consumer.asset.destination == layoutAssets.root && consumer.asset.inputs == listOf(operation.output) &&
    !consumer.asset.classPath
  ) {
    "Layout asset preparation '${operation.id}' requires one ${layoutAssets.format} asset at '${layoutAssets.root}'"
  }
}

private fun validatePreparationOperation(operation: DevPluginPreparationOperation, version: Int) {
  require(isPackerExecutedOperation(operation)) { "No packer operation executes '${operation.id}' of kind '${operation.kind}'" }
  require(operation.kind == "layout-assets" || operation.layoutAssets == null) { "Only a layout-assets operation may declare layout assets" }
  require(operation.manifest in setOf("keep", "drop", "coverage-agent", "rewrite-boot-class-path")) {
    "Unknown preparation manifest policy '${operation.manifest}'"
  }
  val references = operation.sourceReferences()
  for (id in listOf(operation.id, operation.output) + references.map(DevPluginReference::artifact)) {
    require(id.isNotBlank() && id.trim() == id && id.none { it == '\u0000' || it == '\r' || it == '\n' }) {
      "Invalid preparation ID '$id'"
    }
  }
  for (reference in references) {
    if (reference.path.isNotEmpty()) {
      validatePreparationPath(reference.path)
    }
  }
  require(version == 2) { "A layout-assets operation requires preparation recipe version 2" }
  require(operation.manifest == "keep" && operation.filter.isEmpty()) {
    "A layout-assets operation requires its original layout policy"
  }
  validateDevPluginLayoutAssetPreparation(requireNotNull(operation.layoutAssets) { "A layout-assets operation requires layout assets" }, operation.inputs)
}

/** Copies mutable recipe values before they cross the generation and execution boundary. */
@ApiStatus.Internal
fun snapshotDevPluginPreparationRecipe(recipe: DevPluginPreparationRecipe): DevPluginPreparationRecipe {
  return recipe.copy(operations = java.util.List.copyOf(recipe.operations.map { operation ->
    operation.copy(
      inputs = java.util.List.copyOf(operation.inputs),
      layoutAssets = operation.layoutAssets?.let { preparation ->
        preparation.copy(assets = java.util.List.copyOf(preparation.assets.map { asset ->
          asset.copy(
            sources = java.util.List.copyOf(asset.sources),
            transform = asset.transform?.let { transform ->
              transform.copy(mappings = java.util.List.copyOf(transform.mappings))
            },
          )
        }))
      },
    )
  }))
}

/** The catalogue references an operation reads. */
@ApiStatus.Internal
fun DevPluginPreparationOperation.sourceReferences(): List<DevPluginReference> = inputs

@ApiStatus.Internal
fun validatePreparationPath(path: String) {
  require(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' || it == '\r' || it == '\n' } &&
          path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe preparation path '$path'" }
}
