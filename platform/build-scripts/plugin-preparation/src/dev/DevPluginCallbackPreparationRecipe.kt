@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan

/** Converts every original library operation, in declaration order, with its complete immutable owner. */
@ApiStatus.Internal
fun DevPluginLibraryLayoutRecipeConfiguration.toDevPluginPreparationOperations(): List<DevPluginPreparationOperation> {
  return java.util.List.copyOf(operations.map {
    val kind = when (it.kind) {
      "module-filter" -> "library-layout-filter"
      "library-layout-patches" -> "library-layout-patches"
      else -> throw IllegalArgumentException("Unsupported library layout operation '${it.kind}'")
    }
    DevPluginPreparationOperation(it.id, kind, requireNotNull(callbackReferences(it.id).firstOrNull()) { "A library operation requires a source reference" }, "", "keep", libraryLayout = this)
  })
}

internal fun DevPluginPreparationOperation.isCallbackPreparation(): Boolean {
  return kind == "library-layout-filter" || kind == "library-layout-patches"
}

internal fun DevPluginPreparationOperation.callbackSourceReferences(): List<DevPluginReference> {
  return requireNotNull(libraryLayout).callbackReferences(id)
}

internal fun DevPluginPreparationOperation.callbackOutputs(): List<String> {
  return requireNotNull(requireNotNull(libraryLayout).operations.singleOrNull { it.id == id }) { "Missing or duplicate library operation '$id'" }.outputs
}

internal fun DevPluginPreparationOperation.callbackSignature(): String {
  require(output.isEmpty() && manifest == "keep" && excludes.isEmpty() && entry.isEmpty() && mode == 0 &&
          filter.isEmpty() && overrides.isEmpty()) { "A callback operation requires the original owner policy" }
  require(callbackSourceReferences().firstOrNull() == input) { "A callback operation requires its first exact source reference" }
  val operation = requireNotNull(requireNotNull(libraryLayout).operations.singleOrNull { it.id == id }) { "Missing or duplicate library operation '$id'" }
  require(operation.kind == if (kind == "library-layout-filter") "module-filter" else "library-layout-patches") {
    "The shared library kind differs from the original operation"
  }
  return operation.modelSignature
}

internal fun DevPluginLibraryLayoutRecipeConfiguration.callbackReferences(id: String): List<DevPluginReference> {
  return if (id == "$idPrefix:patches") {
    seeds.map { it.input } + callbacks.flatMap { callback ->
      callback.libraries.flatMap { it.roots } + callback.sources.mapNotNull { it.input }
    }
  }
  else {
    requireNotNull(filters.singleOrNull { id == "$idPrefix:module-filter:${it.moduleName}" }) { "Missing or duplicate library filter '$id'" }.inputs
  }
}

internal fun validateDevPluginCallbackOperations(operations: List<DevPluginPreparationOperation>, plan: PluginPackingPlan) {
  val json = Json { encodeDefaults = true }
  for ((_, group) in operations.filter { it.isCallbackPreparation() }.groupBy { it.callbackOwner() }) {
    val library = requireNotNull(group.first().libraryLayout)
    require(library.mainModule == plan.plugin) { "The library layout recipe belongs to another plugin" }
    val encoded = json.encodeToString(library)
    require(group.all { it.libraryLayout?.let { config -> json.encodeToString(config) } == encoded }) {
      "The library layout owner configurations disagree"
    }
    val expected = library.toDevPluginPreparationOperations()
    require(group.map { it.id } == expected.map { it.id }) { "Declare the complete callback owner in its original operation order" }
    require(group.zip(expected).all { (actual, original) -> actual.kind == original.kind && actual.input == original.input }) {
      "The shared callback operations differ from their original owner"
    }
    val prefix = library.idPrefix
    val owned = plan.preparations.filter { it.id == prefix || it.id.startsWith("$prefix:") }
    require(owned.map { it.id }.toSet() == expected.map { it.id }.toSet() && owned.size == expected.size) {
      "Incomplete or stale callback owner preparations"
    }
    validateDevPluginCallbackLibraryMetadata(library, plan)
  }
}

internal fun validateDevPluginCallbackReferences(
  operation: DevPluginPreparationOperation,
  plan: PluginPackingPlan,
  requireRawReference: (DevPluginReference) -> String,
) {
  val consumer = plan.preparations.indexOfFirst { it.id == operation.id }
  for (reference in operation.callbackSourceReferences()) {
    val producer = plan.preparations.indexOfFirst { reference.artifact in it.outputs }
    if (producer >= 0) {
      require(operation.libraryLayout != null && producer < consumer) { "A callback input must have an earlier producer" }
      validatePreparationPath(reference.path)
    }
    else {
      requireRawReference(reference)
    }
  }
}

internal fun compileDevPluginCallbackActions(
  operations: List<DevPluginPreparationOperation>,
  plan: PluginPackingPlan,
  catalogue: DevPluginArtifactCatalogue,
): Map<String, DevPluginPreparationAction> {
  val result = LinkedHashMap<String, DevPluginPreparationAction>()
  for ((_, group) in operations.filter { it.isCallbackPreparation() }.groupBy { it.callbackOwner() }) {
    val first = group.first()
    val original by lazy { compileLibraryLayout(requireNotNull(first.libraryLayout), plan, catalogue) }
    for (operation in group) {
      result.put(operation.id, DevPluginPreparationAction { context -> original.getValue(operation.id).prepare(context) })
    }
  }
  return result
}

private fun DevPluginPreparationOperation.callbackOwner(): String = requireNotNull(libraryLayout).idPrefix

private fun compileLibraryLayout(
  configuration: DevPluginLibraryLayoutRecipeConfiguration,
  plan: PluginPackingPlan,
  catalogue: DevPluginArtifactCatalogue,
): Map<String, DevPluginPreparationAction> {
  return loadBuildScriptsRuntime<DevPluginLibraryLayoutRuntime>("DevPluginLibraryLayoutRecipeRuntime", "Library layout preparation")
    .compile(configuration, plan, catalogue)
}

/**
 * Loads a runtime that `intellij.platform.buildScripts` implements. This module cannot depend on that module, so the
 * callback preparer supplies it on the runtime classpath, and the minimal preparer runs without it.
 */
internal inline fun <reified T> loadBuildScriptsRuntime(simpleClassName: String, purpose: String): T {
  val runtimeClass = try {
    Class.forName("org.jetbrains.intellij.build.dev.$simpleClassName")
  }
  catch (failure: ClassNotFoundException) {
    throw IllegalStateException("$purpose requires the original build-scripts runtime", failure)
  }
  return runtimeClass.getDeclaredConstructor().newInstance() as T
}
