@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "DestructuringDeclaration")

package org.jetbrains.intellij.build.dev

import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PreparedSourceManifestRecipe
import org.jetbrains.intellij.build.devDist.devDistSignatureOf
import java.nio.file.FileSystems

private const val PLUGIN_DESCRIPTOR_ENTRY = "META-INF/plugin.xml"

internal fun validateDevPluginCallbackLibraryMetadata(configuration: DevPluginLibraryLayoutRecipeConfiguration, plan: PluginPackingPlan) {
  val modules = configuration.modules.map { it.name }.toSet()
  require(configuration.version == 1 && configuration.idPrefix.isNotBlank() && configuration.mainModule.isNotBlank()) {
    "Invalid library layout owner"
  }
  require(configuration.exclusions.map { it.moduleName }.distinct().size == configuration.exclusions.size) {
    "Duplicate library layout exclusion module"
  }
  val filtered = configuration.exclusions.filter { it.patterns.isNotEmpty() }.map { it.moduleName }.toSet()
  require(configuration.filters.map { it.moduleName }.toSet() == filtered && configuration.filters.size == filtered.size) {
    "Declare exactly the original filtered modules"
  }
  configuration.exclusions.flatMap { it.patterns }.forEach { FileSystems.getDefault().getPathMatcher("glob:$it") }
  require(
    configuration.callbacks.isNotEmpty() &&
    configuration.callbacks.all { it.kind in setOf("library-entries-v1", CWM_FRONTEND_CALLBACK_KIND) }) {
    "Unsupported library layout callback"
  }
  require(
    configuration.callbacks.map { it.layoutIndex }.distinct().size == configuration.callbacks.size &&
    configuration.callbacks.zipWithNext().all { (first, second) -> first.layoutIndex < second.layoutIndex } &&
    configuration.callbacks.all { it.layoutIndex in 0 until configuration.layoutPatcherCount }) {
    "Invalid original layout callback positions"
  }
  require(
    configuration.patchOutputs.isNotEmpty() && configuration.patchOutputs.map { it.moduleName }.distinct().size == configuration.patchOutputs.size &&
    configuration.patchOutputs.all { it.moduleName in modules }) { "Declare distinct library patch modules" }
  for (filter in configuration.filters) {
    require(
      filter.moduleName in modules && filter.inputs.isNotEmpty() && filter.inputs.size == filter.outputs.size &&
      filter.inputs.map { it.artifact }.distinct().size == filter.inputs.size
    ) { "A library filter requires one output per original input" }
    require(filter.manifest in setOf("keep", "drop", "coverage-agent", "single-meaningful-source")) { "Unsupported library filter manifest policy" }
  }
  for (callback in configuration.callbacks) {
    require(
      callback.libraries.map { it.name to it.moduleName }.distinct().size == callback.libraries.size &&
      callback.sources.map { it.moduleName to it.path }.distinct().size == callback.sources.size
    ) { "Duplicate library callback lookup" }
    if (callback.kind == "library-entries-v1") {
      require(callback.cwmFrontendOptions == null) { "A library callback cannot declare CWM frontend options" }
      require(callback.targetModuleName in modules && configuration.patchOutputs.any { it.moduleName == callback.targetModuleName }) {
        "A library callback requires a declared target output"
      }
      val library = requireNotNull(callback.libraries.singleOrNull { it.name == callback.libraryName && it.moduleName == callback.libraryModuleName }) {
        "A library callback requires its original library lookup"
      }
      require(library.roots.size == 1) { "A library callback requires exactly one declared jar" }
    }
    else {
      require(
        callback.libraryName.isEmpty() && callback.libraryModuleName.isEmpty() && callback.prefix.isEmpty() &&
        callback.targetModuleName.isEmpty() && callback.libraries.isEmpty() && callback.sources.isNotEmpty() &&
        callback.cwmFrontendOptions != null
      ) {
        "The CWM frontend callback requires only declared source files"
      }
    }
    callback.sources.forEach { validatePreparationPath(it.path) }
  }
  for (seed in configuration.seeds) {
    require(seed.moduleName in modules && seed.size >= 0) { "A library seed requires its original module and size" }
    validatePreparationPath(seed.entry)
  }
  for (output in configuration.patchOutputs) {
    val entries = output.entries ?: continue
    require(entries.distinct().size == entries.size && (output.moduleName == configuration.mainModule || PLUGIN_DESCRIPTOR_ENTRY !in entries)) {
      "Invalid library patch entries"
    }
    entries.forEach(::validatePreparationPath)
  }
  val patchesMainDescriptor = configuration.callbacks.any {
    it.kind == "library-entries-v1" && it.targetModuleName == configuration.mainModule && PLUGIN_DESCRIPTOR_ENTRY.startsWith(it.prefix)
  }
  if (patchesMainDescriptor && configuration.patchOutputs.any { it.moduleName == configuration.mainModule && it.entries == null }) {
    require(configuration.seeds.count { it.moduleName == configuration.mainModule && it.entry == PLUGIN_DESCRIPTOR_ENTRY } == 1) {
      "Dynamic main module patches require one authoritative descriptor seed"
    }
  }
  val ids = configuration.filters.map { "${configuration.idPrefix}:module-filter:${it.moduleName}" } + "${configuration.idPrefix}:patches"
  require(configuration.operations.map { it.id } == ids) { "Declare every original library operation in order" }
  val outputs = configuration.filters.flatMap { it.outputs } + configuration.patchOutputs.map { it.output }
  require(outputs.all { it.isNotBlank() } && outputs.distinct().size == outputs.size &&
          ids.flatMap { configuration.callbackReferences(it) }.none { it.artifact in outputs }) { "Conflicting library output ownership" }
  for (operation in configuration.operations) {
    val patches = operation.id == "${configuration.idPrefix}:patches"
    val requiredOutputs = if (patches) configuration.patchOutputs.map { it.output }
    else configuration.filters.single { operation.id == "${configuration.idPrefix}:module-filter:${it.moduleName}" }.outputs
    require(operation.kind == if (patches) "library-layout-patches" else "module-filter") { "Unsupported library layout operation kind" }
    require(operation.inputs == configuration.callbackReferences(operation.id).map { it.artifact }.distinct() && operation.outputs == requiredOutputs) {
      "Stale library operation inputs or outputs"
    }
    require(operation.modelSignature == librarySignature(configuration, operation.id)) { "Stale library operation signature" }
    val definition = requireNotNull(plan.preparations.singleOrNull { it.id == operation.id }) {
      "Missing or duplicate library preparation '${operation.id}'"
    }
    require(definition.let {
      it.inputs == operation.inputs && it.outputs == operation.outputs && it.modelSignature == operation.modelSignature && !it.alwaysRun
    }) { "Stale library preparation definition" }
  }
  val consumers = plan.assets.mapIndexedNotNull { index, planned ->
    if (planned.asset.inputs.none { it in outputs } && planned.asset.recipe?.sources.orEmpty().none { it.input in outputs }) null else index to planned
  }
  require(consumers.map { it.first } == configuration.consumers.map { it.index } &&
          consumers.zip(configuration.consumers).all { (actual, expected) -> actual.second.asset == expected.asset && actual.second.artifact == expected.artifact }) {
    "The library layout consumers changed. Regenerate the projection."
  }
  for ((_, planned) in consumers) {
    val asset = planned.asset
    require(planned.artifact == null && asset.kind == "file" && asset.recipe != null && asset.symlinkTarget == null) {
      "A library output requires its original jar consumer"
    }
    val sources = asset.recipe.sources.filter { it.input in outputs }
    require(sources.map { it.input }.toSet() == asset.inputs.filter { it in outputs }.toSet()) { "A library consumer requires its declared prepared inputs" }
    for (source in sources) {
      val expectedManifest = configuration.callbackPreparedManifest(source.input)
      val metadataOptional = expectedManifest.originalMeaningfulSourceCount != null &&
                             "single-meaningful-source" !in expectedManifest.sourceManifestPolicies
      require(
        source.kind == "prepared" && source.filter == "prepared" && source.options.isEmpty() && source.expansion.isEmpty() && source.entry.isEmpty() &&
        (source.preparedManifest == expectedManifest || metadataOptional && source.preparedManifest == null)
      ) { "Stale library prepared manifest facts for '${source.input}'" }
    }
  }
}

internal fun DevPluginLibraryLayoutRecipeConfiguration.callbackPreparedManifest(output: String): PreparedSourceManifestRecipe {
  val filter = filters.singleOrNull { output in it.outputs }
  if (filter != null) {
    val policy = if (patchOutputs.any { it.entries == null } && filter.manifest != "coverage-agent") "single-meaningful-source" else filter.manifest
    return PreparedSourceManifestRecipe(
      originalMeaningfulSourceCount = if (filter.moduleName.startsWith("intellij.libraries.")) 0 else 1,
      sourceManifestPolicies = listOf(policy)
    )
  }
  return PreparedSourceManifestRecipe(
    originalMeaningfulSourceCount = patchOutputs.single { it.output == output }.entries?.size,
    sourceManifestPolicies = listOf("keep")
  )
}

private fun librarySignature(configuration: DevPluginLibraryLayoutRecipeConfiguration, id: String): String {
  val layout = ArrayList<String>()
  layout.addAll(listOf(configuration.mainModule, configuration.directoryName, configuration.mainJarName, configuration.modules.size.toString()))
  for (module in configuration.modules) {
    layout.addAll(listOf(module.name, module.relativeOutputFile, module.moduleSet.toString()))
  }
  layout.add(configuration.exclusions.size.toString())
  for (exclusion in configuration.exclusions) layout.addAll(listOf(exclusion.moduleName, exclusion.patterns.size.toString()) + exclusion.patterns)
  layout.add(configuration.layoutPatcherCount.toString())
  for (callback in configuration.callbacks) {
    if (callback.kind == "library-entries-v1") {
      layout.addAll(
        listOf(
          callback.kind, callback.layoutIndex.toString(), callback.libraryName, callback.libraryModuleName, callback.prefix,
          callback.targetModuleName
        )
      )
    }
  }
  val values = ArrayList<String>()
  values.addAll(listOf(if (configuration.patchOutputs.any { it.entries == null }) "original-layout-preparation-v3" else "original-layout-preparation-v2", id, digest(layout)))
  for (filter in configuration.filters) {
    values.addAll(listOf("filter", filter.moduleName, filter.manifest, filter.inputs.size.toString()))
    for (input in filter.inputs) values.addAll(listOf(input.artifact, input.path))
    values.addAll(filter.outputs)
  }
  for (callback in configuration.callbacks) {
    values.addAll(listOf("callback", callback.layoutIndex.toString(), callback.libraries.size.toString(), callback.sources.size.toString()))
    callback.cwmFrontendOptions?.let { options ->
      values.addAll(
        listOf(
          CWM_FRONTEND_CALLBACK_KIND,
          options.isEapOverride?.let { "value:$it" } ?: "null",
          options.versionSuffixOverride?.let { "value:$it" } ?: "null",
          options.nightlyBuild.toString(),
          options.branchName?.let { "value:$it" } ?: "null",
        ))
    }
    for (library in callback.libraries) {
      values.addAll(listOf(library.name, library.moduleName.orEmpty(), library.roots.size.toString()))
      for (root in library.roots) values.addAll(listOf(root.artifact, root.path))
    }
    for (source in callback.sources) values.addAll(listOf(source.moduleName, source.path, source.input?.artifact.orEmpty(), source.input?.path.orEmpty()))
  }
  for (seed in configuration.seeds) {
    values.addAll(listOf("seed", seed.moduleName, seed.entry, seed.input.artifact, seed.input.path, seed.size.toString(), seed.hash.toString()))
  }
  for (output in configuration.patchOutputs) {
    values.addAll(listOf("patch-output", output.moduleName, output.output, output.entries?.size?.toString() ?: "dynamic") + output.entries.orEmpty())
  }
  return digest(values)
}

private fun digest(values: List<String>): String = devDistSignatureOf(values)
