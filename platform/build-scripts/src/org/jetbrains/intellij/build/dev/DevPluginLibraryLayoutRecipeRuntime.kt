@file:Suppress("DEPRECATION", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.impl.LibraryEntriesLayoutPatcher
import org.jetbrains.intellij.build.impl.LayoutPatcher
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Files
import java.nio.file.Path

/** Reconstructs the original callbacks and adapter. Compilation reads metadata only. Execution requires all declared input files. */
@ApiStatus.Internal
class DevPluginLibraryLayoutRecipeRuntime : DevPluginLibraryLayoutRuntime {
  override fun compile(
    configuration: DevPluginLibraryLayoutRecipeConfiguration,
    plan: PluginPackingPlan,
    catalogue: DevPluginArtifactCatalogue,
  ): Map<String, DevPluginPreparationAction> {
    require(plan.plugin == configuration.mainModule) { "The library layout recipe belongs to another plugin" }
    require(configuration.callbacks.isNotEmpty() && configuration.callbacks.all { it.kind in setOf("library-entries-v1", CWM_FRONTEND_CALLBACK_KIND) }) {
      "Unsupported library layout callback"
    }
    require(
      configuration.callbacks.map { it.layoutIndex }.distinct().size == configuration.callbacks.size &&
      configuration.callbacks.all { it.layoutIndex in 0 until configuration.layoutPatcherCount }) {
      "Invalid original layout callback positions"
    }
    require(configuration.exclusions.map { it.moduleName }.distinct().size == configuration.exclusions.size) {
      "Duplicate library layout exclusion module"
    }
    val layout = PluginLayout.plugin(configuration.mainModule) { spec ->
      spec.directoryName = configuration.directoryName
      spec.mainJarName = configuration.mainJarName
    }
    (layout.includedModules as MutableCollection<ModuleItem>).clear()
    layout.withModules(configuration.modules.asSequence().map {
      ModuleItem(
        moduleName = it.name, relativeOutputFile = it.relativeOutputFile, reason = null, moduleSet = it.moduleSet,
        includeDependencies = it.includeDependencies,
      )
    })
    for (exclusion in configuration.exclusions) layout.excludeFromModule(exclusion.moduleName, exclusion.patterns)
    val callbacks = configuration.callbacks.associateBy { it.layoutIndex }
    val patchers = ArrayList<DevPluginLayoutPatcherInputs>()
    for (layoutIndex in 0 until configuration.layoutPatcherCount) {
      val callback = callbacks.get(layoutIndex)
      if (callback == null) {
        layout.withPatch(UNBOUND_LAYOUT_PATCHER)
        continue
      }
      val libraries = callback.libraries.map { DevPluginLayoutLibraryInput(it.name, it.moduleName, it.roots) }
      val original = when (callback.kind) {
        "library-entries-v1" -> {
          require(configuration.modules.any { it.name == callback.targetModuleName } &&
                  configuration.patchOutputs.any { it.moduleName == callback.targetModuleName }) {
            "The library callback requires a declared target output"
          }
          LibraryEntriesLayoutPatcher(callback.libraryName, callback.libraryModuleName, callback.prefix, callback.targetModuleName).also { patcher ->
            val library = requireNotNull(libraries.singleOrNull {
              it.name == patcher.libraryName && it.moduleName == patcher.libraryModuleName
            }) { "Missing declared library '${patcher.libraryName}' of '${patcher.libraryModuleName}'" }
            require(library.roots.size == 1) { "${patcher.libraryName} requires exactly one declared jar" }
          }
        }
        CWM_FRONTEND_CALLBACK_KIND -> DevPluginCwmFrontendLayoutCallbackRuntime().create(callback)
        else -> error("Unsupported library layout callback '${callback.kind}'")
      }
      layout.withPatch(original)
      patchers.add(
        DevPluginLayoutPatcherInputs(
          callback = original,
          libraries = libraries,
          sources = callback.sources.map { DevPluginLayoutSourceInput(it.moduleName, it.path, it.input) },
          cwmFrontendOptions = callback.cwmFrontendOptions,
          layoutIndex = layoutIndex,
        )
      )
    }
    val filters = configuration.filters.map { DevPluginLayoutFilter(it.moduleName, it.inputs, it.outputs, it.manifest) }
    val outputs = configuration.patchOutputs.map { DevPluginLayoutPatchOutput(it.moduleName, it.output, it.entries) }
    val seeds = configuration.seeds.map { DevPluginLayoutPatchSeed(it.moduleName, it.entry, it.input, it.size, it.hash) }
    val adapter = DevPluginLayoutPreparationAdapter(layout, filters, patchers, outputs, seeds, configuration.idPrefix)
    val definitions = adapter.preparations()
    val declared = configuration.operations.map { operation ->
      val kind = if (operation.id == "${configuration.idPrefix}:patches") "library-layout-patches" else "module-filter"
      require(operation.kind == kind) { "Unsupported library layout operation '${operation.kind}'" }
      PluginPackingPreparation(operation.id, operation.inputs, operation.outputs, operation.modelSignature)
    }
    require(declared == definitions) { "Incomplete or stale library layout operations. Regenerate the projection." }
    val owned = plan.preparations.filter { it.id == configuration.idPrefix || it.id.startsWith("${configuration.idPrefix}:") }
    require(owned.size == definitions.size && owned.toSet() == definitions.toSet()) {
      "Incomplete or stale library layout preparations. Regenerate the projection."
    }
    val consumers = captureDevPluginLibraryLayoutConsumers(plan, adapter)
    require(consumers.size == configuration.consumers.size && consumers.zip(configuration.consumers).all { (actual, expected) ->
      actual.index == expected.index && actual.asset == expected.asset && actual.artifact == expected.artifact
    }) { "The library layout consumers changed. Regenerate the projection." }
    val originals = adapter.compileActions(layout, plan, catalogue)
    val references = LinkedHashMap<String, List<DevPluginReference>>()
    for ((moduleName, inputs) in filters) references.put("${configuration.idPrefix}:module-filter:$moduleName", inputs)
    references.put(
      "${configuration.idPrefix}:patches",
      seeds.map { it.input } + patchers.flatMap { binding -> binding.libraries.flatMap { it.roots } + binding.sources.mapNotNull { it.input } },
    )
    val rawCatalogue = PreparationCatalogue(catalogue)
    val rawIds = catalogue.artifacts.mapTo(HashSet()) { it.id }
    val rawBindings = HashMap<DevPluginReference, DevPluginLibraryLayoutRawBinding>()
    for (reference in references.values.flatten()) {
      if (reference.artifact !in rawIds) continue
      val artifact = rawCatalogue.requireReference(reference)
      rawBindings.put(reference, DevPluginLibraryLayoutRawBinding(Path.of(artifact.root).toAbsolutePath().normalize(), artifact.kind))
    }
    return originals.mapValues { (id, original) ->
      DevPluginPreparationAction { context ->
        require(context.definition == definitions.single { it.id == id }) { "Stale library layout action '$id'" }
        for (reference in references.getValue(id)) {
          val binding = rawBindings.get(reference)
          val capturedPath = binding?.let { if (it.kind == "file") it.root else it.root.resolve(reference.path) }
          requireDevPluginLibraryLayoutInput(context, reference, capturedPath)
        }
        if (id == "${configuration.idPrefix}:patches") {
          for ((moduleName, entry, input, size, hash) in seeds) {
            val bytes = context.readBytes(input)
            require(bytes.size == size && Hashing.xxh3_64().hashBytesToLong(bytes) == hash) {
              if (moduleName == configuration.mainModule && entry == PLUGIN_XML_RELATIVE_PATH) {
                "The authoritative descriptor seed changed. Regenerate the projection."
              }
              else "The declared layout seed '$moduleName/$entry' changed. Regenerate the projection."
            }
          }
        }
        original.prepare(context)
      }
    }
  }
}

internal fun requireDevPluginLibraryLayoutInput(
  context: DevPluginPreparationContext,
  reference: DevPluginReference,
  capturedPath: Path?,
): Path {
  val path = context.inputPath(reference)
  require(Files.isRegularFile(path)) { "Missing library layout input file '$reference'" }
  require(capturedPath == null || Files.isRegularFile(capturedPath) && Files.isSameFile(path, capturedPath)) {
    "The raw library layout binding changed for '$reference'. Recompile the actions."
  }
  return path
}

@ApiStatus.Internal
fun interface DevPluginSpecialLayoutCallbackRuntime {
  fun create(callback: DevPluginLibraryLayoutCallback): LayoutPatcher
}

private val UNBOUND_LAYOUT_PATCHER: LayoutPatcher = { _, _, _ -> }

/** The context checks the artifact kind through the reference path before it returns the file. */
private class DevPluginLibraryLayoutRawBinding(
  @JvmField val root: Path,
  @JvmField val kind: String,
)
