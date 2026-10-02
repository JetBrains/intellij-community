@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.intellij.build.dev.DEV_PLUGIN_PREPARATION_FORMAT
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.DevPluginReference
import org.jetbrains.intellij.build.dev.sourceReferences
import org.jetbrains.intellij.build.dev.validateDevPluginLayoutAssetConsumers
import org.jetbrains.intellij.build.dev.validatePreparationOperation
import org.jetbrains.intellij.build.dev.validatePreparationPath
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingPlan

/**
 * The selected raw inputs of one plan without physical paths: the kind of each artifact (`archive`, `file` or
 * `directory`) and the id of each library. A library stands for its member jars, which the catalogue rule lists.
 */
internal class DevDistPluginInputKinds(
  artifacts: Map<String, String>,
  private val libraries: Set<String>,
) {
  private val artifacts = HashMap<String, String>()

  init {
    for ((id, kind) in artifacts) {
      require(id.isNotBlank() && id.trim() == id && id.none { it == '\u0000' || it == '\r' || it == '\n' }) { "Invalid artifact ID '$id'" }
      require(kind in setOf("archive", "file", "directory")) { "Unknown artifact root kind '$kind'" }
      this.artifacts.put(id, if (kind == "archive") "file" else kind)
    }
    for (id in libraries) {
      require(id.isNotBlank() && !this.artifacts.containsKey(id)) { "Invalid or duplicate library '$id'" }
    }
  }

  fun contains(id: String): Boolean = artifacts.containsKey(id) || id in libraries

  /** The kind of a raw input. A library is a set of files. */
  fun kind(id: String): String {
    if (id in libraries) return "file"
    return requireNotNull(artifacts.get(id)) { "Unresolved input '$id'" }
  }

  fun isLibrary(id: String): Boolean = id in libraries

  fun requireReference(reference: DevPluginReference) {
    if (kind(reference.artifact) == "directory") {
      if (reference.path.isNotEmpty()) validatePreparationPath(reference.path)
    }
    else {
      require(reference.path.isEmpty()) { "File input '${reference.artifact}' cannot have a relative path" }
    }
  }
}

/**
 * Checks every operation against the operations that [plan] requires and against the input kinds. The generator runs
 * it, so a mismatch fails `plugin-model-tool` and not a Bazel action. The packer runs the same checks from the plan file.
 */
internal fun validateDevDistPluginOperations(
  plan: PluginPackingPlan,
  inputs: DevDistPluginInputKinds,
  operations: List<DevPluginPreparationOperation>,
) {
  val required = plan.operations.associateBy { it.id }
  val seen = HashSet<String>()
  for (operation in operations) {
    validatePreparationOperation(operation, DEV_PLUGIN_PREPARATION_FORMAT)
    require(seen.add(operation.id)) { "Duplicate preparation operation '${operation.id}'" }
    val planned = requireNotNull(required.get(operation.id)) { "Unexpected preparation operation '${operation.id}'" }
    require(planned == operation) { "Preparation operation '${operation.id}' differs from the operation of the plan" }
    operation.sourceReferences().forEach(inputs::requireReference)
    validateDevPluginLayoutAssetConsumers(operation, plan)
  }
  val missing = required.keys - seen
  require(missing.isEmpty()) { "Missing preparation operations: $missing" }
}

/**
 * The ordered raw inputs the remainder action reads, derived from the selected assets and the operations without
 * reading payloads. Every operation is packer-executed, so the remainder reads the raw inputs of an operation in place of
 * its output. A reused jar is independent: its asset adds no input. Its module output can be a remainder input of
 * another asset under the same name, so the module name is not compared with the input IDs.
 */
internal fun deriveDevDistPluginRemainderInputs(
  plan: PluginPackingPlan,
  inputs: DevDistPluginInputKinds,
  operations: List<DevPluginPreparationOperation>,
): List<String> {
  require(plan.requiredInputs.distinct().size == plan.requiredInputs.size) { "Duplicate required inputs" }
  require(plan.operations.none { inputs.contains(it.output) }) { "An operation output aliases a raw catalogue ID" }
  val prepared = operations.associateBy { it.output }

  val usedInputs = LinkedHashSet<String>()
  fun use(input: String) {
    val operation = prepared.get(input)
    if (operation != null) {
      operation.sourceReferences().mapTo(usedInputs, DevPluginReference::artifact)
    }
    else {
      usedInputs.add(input)
    }
  }
  for (planned in plan.assets) {
    if (planned.artifact != null) continue
    val asset = planned.asset
    val recipe = asset.recipe
    when {
      asset.kind == "tree" -> {
        val input = asset.inputs.single()
        require(input in prepared || inputs.kind(input) == "directory") { "Tree '${asset.destination}' requires a directory artifact" }
        use(input)
      }
      asset.kind == "directory" || asset.symlinkTarget != null -> Unit
      recipe != null -> deriveJarInputs(recipe, inputs, prepared, ::use)
      else -> {
        require(asset.inputs.size == 1) { "Completed asset '${asset.destination}' requires one declared file" }
        val input = asset.inputs.single()
        require(input !in prepared) { "Completed asset '${asset.destination}' requires a declared file, not the prepared output '$input'" }
        require(!inputs.isLibrary(input)) { "Completed asset '${asset.destination}' copies the library '$input', which is a set of files" }
        inputs.requireReference(DevPluginReference(input))
        use(input)
      }
    }
  }
  return usedInputs.toList()
}

private fun deriveJarInputs(
  recipe: CanonicalJarRecipe,
  inputs: DevDistPluginInputKinds,
  prepared: Map<String, DevPluginPreparationOperation>,
  use: (String) -> Unit,
) {
  require(recipe.writer.manifest in setOf("single-meaningful-source", "keep", "drop")) {
    "Unknown manifest policy '${recipe.writer.manifest}'"
  }
  for (source in recipe.sources) {
    require(source.options.size == source.options.toSet().size && source.options.all {
      it in setOf("patch", "lib-module", "manifest=keep", "manifest=drop")
    }) { "Source '${source.input}' requires an unsupported preparation option: ${source.options}" }
    require(source.options.count { it.startsWith("manifest=") } <= 1) { "Source '${source.input}' has conflicting manifest policies" }
    if (source.kind == "prepared") {
      require(source.options.isEmpty() && source.entry.isEmpty() && source.filter == "prepared") {
        "Prepared source '${source.input}' must materialize its options"
      }
      val operation = requireNotNull(prepared.get(source.input)) { "Unresolved prepared source '${source.input}'" }
      require(recipe.writer.manifest != "single-meaningful-source" || source.preparedManifest != null) {
        "Prepared sources require an explicit manifest policy"
      }
      source.preparedManifest?.let { metadata ->
        require(metadata.sourceManifestPolicies == listOf(operation.manifest)) { "Prepared source '${source.input}' has stale manifest policies" }
        require(metadata.originalMeaningfulSourceCount != null) {
          "Preparation '${operation.id}' does not produce the module patches required by its manifest recipe"
        }
      }
      use(source.input)
      continue
    }
    val filter = when (source.filter) {
      "module", "library", "all" -> source.filter
      "module-v1" -> "module"
      "library-v1" -> "library"
      "none" -> "all"
      else -> throw IllegalArgumentException("Custom filter '${source.filter}' requires declared preparation inputs")
    }
    when (source.kind) {
      "zip", "archive", "module" -> {
        require(source.entry.isEmpty() && "patch" !in source.options && !inputs.isLibrary(source.input)) {
          "Archive source '${source.input}' contains entry options or names a library"
        }
      }
      "library" -> {
        require(source.entry.isEmpty() && "patch" !in source.options) { "Library '${source.input}' contains entry options" }
        require(inputs.isLibrary(source.input)) { "Unknown library '${source.input}'" }
      }
      "file" -> {
        require(filter == "all" && !inputs.isLibrary(source.input)) { "File source '${source.input}' contains filter options or names a library" }
        validatePreparationPath(source.entry)
      }
      else -> throw IllegalArgumentException("Source kind '${source.kind}' requires declared preparation inputs")
    }
    inputs.requireReference(DevPluginReference(source.input))
    use(source.input)
  }
}
