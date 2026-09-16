package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PreparedSourceManifestRecipe
import org.jetbrains.intellij.build.impl.LibraryEntriesLayoutPatcher
import org.jetbrains.intellij.build.impl.PluginLayout

/** Captures the original adapter inputs without opening sources or invoking callbacks. */
@ApiStatus.Internal
fun snapshotDevPluginLibraryLayoutRecipe(
  layout: PluginLayout,
  filters: List<DevPluginLayoutFilter>,
  patchers: List<DevPluginLayoutPatcherInputs>,
  patchOutputs: List<DevPluginLayoutPatchOutput>,
  seeds: List<DevPluginLayoutPatchSeed>,
  plan: PluginPackingPlan,
  idPrefix: String = "library-layout",
): DevPluginLibraryLayoutRecipeConfiguration {
  val callbacks = patchers.mapIndexed { index, binding ->
    val callback = binding.callback
    val layoutIndex = binding.layoutIndex.takeIf { it >= 0 } ?: index
    if (callback is LibraryEntriesLayoutPatcher) {
      require(binding.cwmFrontendOptions == null) { "A library callback cannot declare CWM frontend options" }
      require(layout.includedModules.any { it.moduleName == callback.targetModuleName } &&
              patchOutputs.any { it.moduleName == callback.targetModuleName }) { "The library callback requires a declared target output" }
      val library = requireNotNull(binding.libraries.singleOrNull {
        it.name == callback.libraryName && it.moduleName == callback.libraryModuleName
      }) { "Missing declared library '${callback.libraryName}' of '${callback.libraryModuleName}'" }
      require(library.roots.size == 1) { "${callback.libraryName} requires exactly one declared jar" }
      DevPluginLibraryLayoutCallback(
        layoutIndex = layoutIndex,
        libraryName = callback.libraryName, libraryModuleName = callback.libraryModuleName,
        prefix = callback.prefix, targetModuleName = callback.targetModuleName,
        orderedLibraries = binding.libraries.map { DevPluginLibraryLayoutRecipeLibrary(it.name, it.moduleName, it.roots) },
        orderedSources = binding.sources.map { DevPluginLibraryLayoutRecipeSource(it.moduleName, it.path, it.input) },
      )
    }
    else {
      val options = requireNotNull(binding.cwmFrontendOptions) { "Unsupported library layout callback" }
      require(binding.libraries.isEmpty() && binding.sources.map { it.moduleName to it.path } == cwmFrontendCallbackSourceKeys()) {
        "The CWM frontend callback requires its exact declared source files"
      }
      DevPluginLibraryLayoutCallback(
        kind = CWM_FRONTEND_CALLBACK_KIND,
        layoutIndex = layoutIndex,
        libraryName = "",
        libraryModuleName = "",
        prefix = "",
        targetModuleName = "",
        cwmFrontendOptions = options,
        orderedLibraries = emptyList(),
        orderedSources = binding.sources.map { DevPluginLibraryLayoutRecipeSource(it.moduleName, it.path, it.input) },
      )
    }
  }
  require(callbacks.isNotEmpty()) { "A library layout recipe requires an original library callback" }
  val adapter = DevPluginLayoutPreparationAdapter(layout, filters, patchers, patchOutputs, seeds, idPrefix)
  require(plan.plugin == layout.mainModule) { "The library layout consumers belong to another plugin" }
  return DevPluginLibraryLayoutRecipeConfiguration(
    mainModule = layout.mainModule, directoryName = layout.directoryName, mainJarName = layout.getMainJarName(), idPrefix = idPrefix,
    layoutPatcherCount = layout.patchers.size,
    orderedModules = layout.includedModules.map {
      DevPluginLibraryLayoutModule(it.moduleName, it.relativeOutputFile, it.includeDependencies, it.moduleSet)
    },
    orderedExclusions = layout.moduleExcludes.map { (moduleName, patterns) -> DevPluginLibraryLayoutExclusion(moduleName, patterns) },
    orderedFilters = filters.map { DevPluginLibraryLayoutRecipeFilter(it.moduleName, it.manifest, it.inputs, it.outputs) },
    orderedCallbacks = callbacks,
    orderedPatchOutputs = patchOutputs.map { DevPluginLibraryLayoutRecipeOutput(it.moduleName, it.output, it.entries) },
    orderedSeeds = seeds.map { DevPluginLibraryLayoutRecipeSeed(it.moduleName, it.entry, it.input, it.size, it.hash) },
    orderedOperations = adapter.preparations().map {
      DevPluginLibraryLayoutRecipeOperation(
        id = it.id, kind = if (it.id == "$idPrefix:patches") "library-layout-patches" else "module-filter",
        modelSignature = it.modelSignature, orderedInputs = it.inputs, orderedOutputs = it.outputs,
      )
    },
    orderedConsumers = captureDevPluginLibraryLayoutConsumers(plan, adapter),
  )
}

internal fun captureDevPluginLibraryLayoutConsumers(
  plan: PluginPackingPlan,
  adapter: DevPluginLayoutPreparationAdapter,
): List<DevPluginLibraryLayoutConsumer> {
  val manifests = adapter.facts.preparedSourceManifests
  return plan.assets.mapIndexedNotNull { index, planned ->
    val asset = planned.asset
    val sources = asset.recipe?.sources.orEmpty()
    if (asset.inputs.none { it in manifests } && sources.none { it.input in manifests }) return@mapIndexedNotNull null
    require(planned.artifact == null && asset.kind == "file" && asset.recipe != null && asset.symlinkTarget == null) {
      "A library layout output requires an original jar consumer"
    }
    val ownedSources = sources.filter { it.input in manifests }
    require(ownedSources.map { it.input }.toSet() == asset.inputs.filter { it in manifests }.toSet()) {
      "The library layout consumer must use its declared prepared inputs"
    }
    for (source in ownedSources) {
      val facts = manifests.getValue(source.input)
      val expected = PreparedSourceManifestRecipe(
        originalMeaningfulSourceCount = facts.originalMeaningfulSourceCount, sourceManifestPolicies = facts.sourceManifestPolicies,
      )
      val metadataOptional = facts.originalMeaningfulSourceCount != null && "single-meaningful-source" !in facts.sourceManifestPolicies
      require(
        source.kind == "prepared" && source.filter == "prepared" &&
        (source.preparedManifest == expected || metadataOptional && source.preparedManifest == null)
      ) {
        "The library layout consumer has stale manifest metadata for '${source.input}'"
      }
    }
    DevPluginLibraryLayoutConsumer(index, asset, null)
  }
}
