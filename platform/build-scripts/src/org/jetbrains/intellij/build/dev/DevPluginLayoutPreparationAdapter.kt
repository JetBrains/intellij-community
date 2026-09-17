@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.FileSource
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.PluginSymbolicPreparationFacts
import org.jetbrains.intellij.build.devDist.PluginSymbolicPreparedEffect
import org.jetbrains.intellij.build.devDist.PluginSymbolicPreparedSourceManifest
import org.jetbrains.intellij.build.devDist.devDistSignatureOf
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.LayoutPatcher
import org.jetbrains.intellij.build.impl.LibraryEntriesLayoutPatcher
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.commonModuleExcludes
import org.jetbrains.intellij.build.impl.createModuleSourcesNamesFilter
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import java.nio.file.FileSystems
import java.nio.file.Files

@ApiStatus.Internal
data class DevPluginLayoutFilter(
  @JvmField val moduleName: String,
  @JvmField val inputs: List<DevPluginReference>,
  @JvmField val outputs: List<String>,
  @JvmField val manifest: String,
)

@ApiStatus.Internal
data class DevPluginLayoutLibraryInput(
  @JvmField val name: String,
  @JvmField val moduleName: String?,
  @JvmField val roots: List<DevPluginReference>,
)

/** A null [input] declares a source lookup whose result is absent. */
@ApiStatus.Internal
data class DevPluginLayoutSourceInput(
  @JvmField val moduleName: String,
  @JvmField val path: String,
  @JvmField val input: DevPluginReference?,
)

/**
 * Binds an original callback whose dependencies the caller has reviewed.
 * The callback must use only these context lookups and immutable captured values.
 * Captured paths, global state, and platform layout dependencies are unsupported.
 * This declaration is not an in-process filesystem sandbox.
 */
@ApiStatus.Internal
data class DevPluginLayoutPatcherInputs(
  @JvmField val callback: LayoutPatcher,
  @JvmField val libraries: List<DevPluginLayoutLibraryInput> = emptyList(),
  @JvmField val sources: List<DevPluginLayoutSourceInput> = emptyList(),
  @JvmField val cwmFrontendOptions: DevPluginCwmFrontendCallbackOptions? = null,
  /** The callback position in the original layout. A negative value uses this binding's position. */
  @JvmField val layoutIndex: Int = -1,
)

@ApiStatus.Internal
data class DevPluginLayoutPatchOutput(
  @JvmField val moduleName: String,
  @JvmField val output: String,
  /** A null list defers entry names and counts until the original callbacks run. The module output remains declared. */
  @JvmField val entries: List<String>? = null,
)

@ApiStatus.Internal
data class DevPluginLayoutPatchSeed(
  @JvmField val moduleName: String,
  @JvmField val entry: String,
  @JvmField val input: DevPluginReference,
  @JvmField val size: Int,
  @JvmField val hash: Long,
)

/**
 * Runs the original layout callbacks in the process that captured them.
 * Compilation checks the live layout and callback identities before any input is read.
 * It cannot reconstruct callbacks from a serialized projection or inspect mutable closure state.
 * [patchOutputs] states the module outputs and any known entry order after all callbacks run.
 * Dynamic patches must retain the descriptor seed used for planning.
 * Ordinary archive filters use the jar's runtime source count when patch entries are dynamic.
 * Multiple callback effects share one preparation, which the symbolic projector deduplicates.
 */
@ApiStatus.Internal
class DevPluginLayoutPreparationAdapter(
  layout: PluginLayout,
  filters: List<DevPluginLayoutFilter>,
  patchers: List<DevPluginLayoutPatcherInputs> = emptyList(),
  patchOutputs: List<DevPluginLayoutPatchOutput> = emptyList(),
  seeds: List<DevPluginLayoutPatchSeed> = emptyList(),
  private val idPrefix: String = "original-layout",
) {
  private val filters = filters.map { it.copy(inputs = it.inputs.toList(), outputs = it.outputs.toList()) }
  private val patchers = patchers.mapIndexed { index, binding ->
    binding.copy(
      libraries = binding.libraries.map { it.copy(roots = it.roots.toList()) },
      sources = binding.sources.toList(),
      layoutIndex = binding.layoutIndex.takeIf { it >= 0 } ?: index,
    )
  }
  private val patchOutputs = patchOutputs.map { it.copy(entries = it.entries?.toList()) }
  private val dynamicPatches = this.patchOutputs.any { it.entries == null }
  private val dynamicMainOutput = this.patchOutputs.any { it.moduleName == layout.mainModule && it.entries == null }
  private val patchesMainDescriptor = this.patchers.any { binding ->
    val patcher = binding.callback
    patcher !is LibraryEntriesLayoutPatcher ||
    patcher.targetModuleName == layout.mainModule && PLUGIN_XML_RELATIVE_PATH.startsWith(patcher.prefix)
  }
  private val seeds = seeds.toList()
  private val layoutState = layoutState(layout)
  private val modules = layout.includedModules.map { it.moduleName }.distinct()
  private val mainModule = layout.mainModule
  private val definitions = LinkedHashMap<String, PluginPackingPreparation>()

  @JvmField
  val facts: PluginSymbolicPreparationFacts

  init {
    require(idPrefix.isNotBlank()) { "A layout preparation requires an ID prefix" }
    require(
      this.patchers.map { it.layoutIndex }.distinct().size == this.patchers.size &&
      this.patchers.zipWithNext().all { (first, second) -> first.layoutIndex < second.layoutIndex } &&
      this.patchers.all { it.layoutIndex in layout.patchers.indices && layout.patchers[it.layoutIndex] === it.callback }) {
      "Layout callback bindings must use distinct original positions in declaration order"
    }
    val filteredModules = layout.moduleExcludes.filterValues { it.isNotEmpty() }.keys
    require(this.filters.map { it.moduleName }.toSet() == filteredModules && this.filters.size == filteredModules.size) {
      "Declare exactly the original layout's filtered modules: $filteredModules"
    }
    require(this.patchOutputs.map { it.moduleName }.distinct().size == this.patchOutputs.size && this.patchOutputs.all { it.moduleName in modules }) {
      "Patch outputs must name distinct modules from the original layout"
    }
    require(this.patchers.isNotEmpty() || (this.patchOutputs.isEmpty() && this.seeds.isEmpty())) { "Patch outputs require an original callback" }
    require(this.patchers.isEmpty() || this.patchOutputs.isNotEmpty()) { "Original callbacks require declared patch outputs" }
    for (seed in this.seeds) {
      require(seed.moduleName in modules) { "Patch seed names an undeclared module '${seed.moduleName}'" }
      require(seed.size >= 0) { "A patch seed requires its original file size" }
      validatePreparationPath(seed.entry)
    }
    for (output in this.patchOutputs) {
      val entries = output.entries ?: continue
      require(entries.distinct().size == entries.size) { "Duplicate patch entries for '${output.moduleName}'" }
      entries.forEach(::validatePreparationPath)
      require(output.moduleName == mainModule || PLUGIN_XML_RELATIVE_PATH !in entries) { "Only the main module can retain its plugin descriptor" }
    }
    require(
      !dynamicMainOutput || !patchesMainDescriptor ||
      this.seeds.count { it.moduleName == mainModule && it.entry == PLUGIN_XML_RELATIVE_PATH } == 1) {
      "Dynamic main module patches require one authoritative descriptor seed"
    }
    for (binding in this.patchers) {
      require(binding.libraries.map { it.name to it.moduleName }.distinct().size == binding.libraries.size) { "Duplicate callback library lookup" }
      require(binding.sources.map { it.moduleName to it.path }.distinct().size == binding.sources.size) { "Duplicate callback source lookup" }
      binding.sources.forEach { validatePreparationPath(it.path) }
    }
    val effects = LinkedHashMap<String, PluginSymbolicPreparedEffect>()
    val manifests = LinkedHashMap<String, PluginSymbolicPreparedSourceManifest>()
    for (filter in this.filters) {
      require(filter.inputs.isNotEmpty() && filter.inputs.size == filter.outputs.size && filter.moduleName in modules) {
        "A module filter requires one output per ordered archive input"
      }
      require(filter.inputs.map { it.artifact }.distinct().size == filter.inputs.size) { "Each filtered root requires a distinct artifact ID" }
      require(filter.manifest in setOf("keep", "drop", "coverage-agent", "single-meaningful-source")) { "A module filter requires a supported manifest policy" }
      createFilter(layout, filter.moduleName)
      val id = "$idPrefix:module-filter:${filter.moduleName}"
      val definition = PluginPackingPreparation(id, filter.inputs.map { it.artifact }.distinct(), filter.outputs, signature(layout, id))
      definitions.put(id, definition)
      val contributions = LinkedHashMap<String, List<JarSourceRecipe>>()
      filter.inputs.forEachIndexed { index, input -> contributions.put(input.artifact, listOf(preparedRecipe(filter.outputs[index]))) }
      effects.put("module-filter:${filter.moduleName}", PluginSymbolicPreparedEffect(definition, sourceContributions = contributions))
      for (output in filter.outputs) {
        manifests.put(output, PluginSymbolicPreparedSourceManifest(if (filter.moduleName.startsWith(LIB_MODULE_PREFIX)) 0 else 1, listOf(filterManifest(filter))))
      }
    }
    val modulePatches = LinkedHashMap<String, List<JarSourceRecipe>>()
    if (this.patchers.isNotEmpty()) {
      val id = "$idPrefix:patches"
      val inputs = patchReferences().map { it.artifact }.distinct()
      require(inputs.isNotEmpty()) { "Layout patch preparation requires a declared input, such as its descriptor seed" }
      val definition = PluginPackingPreparation(id, inputs, this.patchOutputs.map { it.output }, signature(layout, id))
      definitions.put(id, definition)
      for (binding in this.patchers) effects.put("layout-patcher:${binding.layoutIndex}", PluginSymbolicPreparedEffect(definition))
      for (output in this.patchOutputs) {
        modulePatches.put(output.moduleName, listOf(preparedRecipe(output.output)))
        manifests.put(output.output, PluginSymbolicPreparedSourceManifest(output.entries?.size, listOf("keep")))
      }
    }
    val outputs = definitions.values.flatMap { it.outputs }
    require(outputs.all { it.isNotBlank() } && outputs.distinct().size == outputs.size) { "Duplicate or empty layout preparation output" }
    require(outputs.none { output -> definitions.values.any { output in it.inputs } }) { "A layout preparation output must not alias an input" }
    facts = PluginSymbolicPreparationFacts(effects = effects, modulePatches = modulePatches, preparedSourceManifests = manifests)
  }

  fun preparations(): List<PluginPackingPreparation> = definitions.values.toList()

  /** Validates without reading inputs. Prepared references name paths inside output directories from earlier plan preparations. */
  fun compileActions(
    runtimeLayout: PluginLayout,
    plan: PluginPackingPlan,
    catalogue: DevPluginArtifactCatalogue,
  ): Map<String, DevPluginPreparationAction> {
    validateRuntime(runtimeLayout)
    validateDevPluginPreparationOutputs(plan, catalogue)
    val required = plan.preparations.associateBy { it.id }
    for (definition in definitions.values) {
      require(required.get(definition.id) == definition && definition.modelSignature == signature(runtimeLayout, definition.id)) {
        "Stale original layout preparation '${definition.id}'. Regenerate the projection."
      }
    }
    validateReferences(plan, catalogue)
    val result = LinkedHashMap<String, DevPluginPreparationAction>()
    for (filter in filters) {
      val id = "$idPrefix:module-filter:${filter.moduleName}"
      val includes = createFilter(runtimeLayout, filter.moduleName)
      result.put(id, DevPluginPreparationAction { context ->
        validateAction(runtimeLayout, id, context)
        filter.inputs.mapIndexed { index, input -> prepareFilteredArchive(context, filter.outputs[index], input, filterManifest(filter), includes) }
      })
    }
    if (patchers.isNotEmpty()) {
      val id = "$idPrefix:patches"
      result.put(id, DevPluginPreparationAction { context ->
        validateAction(runtimeLayout, id, context)
        val patcher = ModuleOutputPatcher()
        for (seed in seeds) {
          val file = context.inputPath(seed.input)
          patcher.patchModuleOutputWithFile(seed.moduleName, seed.entry, FileSource(seed.entry, seed.size, seed.hash, file))
        }
        val descriptor = if (dynamicMainOutput && patchesMainDescriptor) {
          patcher.getPatchedSources(mainModule).get(PLUGIN_XML_RELATIVE_PATH) as FileSource?
        }
        else null
        val descriptorBytes = descriptor?.let { Files.readAllBytes(it.file) }
        if (descriptor != null) {
          require(descriptorBytes!!.size == descriptor.size && Hashing.xxh3_64().hashBytesToLong(descriptorBytes) == descriptor.hash) {
            "The authoritative descriptor seed changed. Regenerate the projection."
          }
        }
        val platform = PlatformLayout()
        for (binding in patchers) {
          runtimeLayout.patchers[binding.layoutIndex](patcher, platform, DevPluginLayoutPreparationContext(context, binding))
          validateRuntime(runtimeLayout)
          val undeclared = patcher.getPatchedModuleNames() - modules.toSet()
          require(undeclared.isEmpty()) { "Original layout callback patched undeclared modules: ${undeclared.sorted()}" }
          if (dynamicMainOutput && patchesMainDescriptor) {
            require(
              patcher.getPatchedSources(mainModule).get(PLUGIN_XML_RELATIVE_PATH) == descriptor &&
              (descriptor == null || Files.readAllBytes(descriptor.file).contentEquals(descriptorBytes))
            ) {
              "Dynamic layout callbacks must preserve the authoritative descriptor seed"
            }
          }
        }
        for (moduleName in modules) {
          val names = patcher.getPatchedSources(moduleName).keys.filter { moduleName == mainModule || it != PLUGIN_XML_RELATIVE_PATH }
          val output = patchOutputs.singleOrNull { it.moduleName == moduleName }
          require(output != null || names.isEmpty()) { "Original layout callback patched module '$moduleName' without a declared patch output" }
          val declared = output?.entries
          require(declared == null || names == declared) {
            "Original layout patches for '$moduleName' differ from the declared order: expected=$declared, actual=$names"
          }
        }
        patchOutputs.map { output ->
          val prepared = context.prepareModulePatches(output.output, output.moduleName, patcher, output.moduleName == mainModule)
          prepared.copy(sources = prepared.sources.map { source ->
            source.copy(entries = source.entries.mapIndexed { index, entry ->
              val input = requireNotNull(entry.input)
              if (input.artifact == output.output) entry
              else entry.copy(input = context.writeFile(output.output, "files/$index", context.readBytes(input)))
            })
          })
        }
      })
    }
    return result
  }

  private fun validateReferences(plan: PluginPackingPlan, catalogue: DevPluginArtifactCatalogue) {
    val inputs = PreparationCatalogue(catalogue)
    val rawIds = catalogue.artifacts.mapTo(HashSet()) { it.id }
    catalogue.libraries.mapTo(rawIds) { it.id }
    val positions = HashMap<String, Int>()
    val producers = HashMap<String, Int>()
    for ((index, preparation) in plan.preparations.withIndex()) {
      require(preparation.id.isNotBlank() && positions.putIfAbsent(preparation.id, index) == null) {
        "Invalid or repeated preparation '${preparation.id}'"
      }
      for (output in preparation.outputs) {
        require(output.isNotBlank() && producers.putIfAbsent(output, index) == null) { "Conflicting preparation ownership for '$output'" }
      }
    }
    fun requireEarlier(input: String, consumer: String) {
      val producer = requireNotNull(producers.get(input)) { "Unresolved input '$input' in preparation '$consumer'" }
      require(producer < positions.getValue(consumer)) { "Input '$input' in preparation '$consumer' must have an earlier producer" }
    }
    for (preparation in plan.preparations) {
      for (input in preparation.inputs) {
        if (input !in rawIds) requireEarlier(input, preparation.id)
      }
    }
    fun requireReference(reference: DevPluginReference, consumer: String) {
      if (reference.artifact in rawIds) {
        inputs.requireReference(reference)
      }
      else {
        requireEarlier(reference.artifact, consumer)
        validatePreparationPath(reference.path)
      }
    }
    for (filter in filters) {
      for (input in filter.inputs) requireReference(input, "$idPrefix:module-filter:${filter.moduleName}")
    }
    if (patchers.isNotEmpty()) {
      for (input in patchReferences()) requireReference(input, "$idPrefix:patches")
    }
  }

  private fun validateAction(layout: PluginLayout, id: String, context: DevPluginPreparationContext) {
    validateRuntime(layout)
    require(context.definition == definitions.get(id)) { "Stale original layout action '$id'" }
  }

  private fun validateRuntime(layout: PluginLayout) {
    require(
      layoutState(layout) == layoutState &&
      patchers.all { it.layoutIndex in layout.patchers.indices && layout.patchers[it.layoutIndex] === it.callback }) {
      "The runtime original layout or callbacks changed. Regenerate the projection."
    }
  }

  private fun patchReferences(): List<DevPluginReference> {
    return seeds.map { it.input } + patchers.flatMap { binding -> binding.libraries.flatMap { it.roots } + binding.sources.mapNotNull { it.input } }
  }

  private fun filterManifest(filter: DevPluginLayoutFilter): String {
    return if (dynamicPatches && filter.manifest != "coverage-agent") "single-meaningful-source" else filter.manifest
  }

  private fun signature(layout: PluginLayout, id: String): String {
    val values = ArrayList<String>()
    values.addAll(listOf(if (dynamicPatches) "original-layout-preparation-v3" else "original-layout-preparation-v2", id, layoutState(layout)))
    for (filter in filters) {
      values.addAll(listOf("filter", filter.moduleName, filter.manifest, filter.inputs.size.toString()))
      for (input in filter.inputs) values.addAll(listOf(input.artifact, input.path))
      values.addAll(filter.outputs)
    }
    for (binding in patchers) {
      values.addAll(listOf("callback", binding.layoutIndex.toString(), binding.libraries.size.toString(), binding.sources.size.toString()))
      binding.cwmFrontendOptions?.let { options ->
        values.addAll(
          listOf(
            CWM_FRONTEND_CALLBACK_KIND,
            options.isEapOverride?.let { "value:$it" } ?: "null",
            options.versionSuffixOverride?.let { "value:$it" } ?: "null",
            options.nightlyBuild.toString(),
            options.branchName?.let { "value:$it" } ?: "null",
          ))
      }
      for (library in binding.libraries) {
        values.addAll(listOf(library.name, library.moduleName.orEmpty(), library.roots.size.toString()))
        for (root in library.roots) values.addAll(listOf(root.artifact, root.path))
      }
      for (source in binding.sources) values.addAll(listOf(source.moduleName, source.path, source.input?.artifact.orEmpty(), source.input?.path.orEmpty()))
    }
    for (seed in seeds) values.addAll(listOf("seed", seed.moduleName, seed.entry, seed.input.artifact, seed.input.path, seed.size.toString(), seed.hash.toString()))
    for (output in patchOutputs) {
      values.addAll(listOf("patch-output", output.moduleName, output.output, output.entries?.size?.toString() ?: "dynamic") + output.entries.orEmpty())
    }
    return digest(values)
  }
}

private fun preparedRecipe(output: String): JarSourceRecipe = JarSourceRecipe(output, "prepared", "prepared")

/**
 * Materializes one filtered module jar as prepared entries in central-directory order. Only a layout filter of this
 * adapter reaches it, and the generator declares none: the Go packer executes the `module-filter` operation kind.
 */
private fun prepareFilteredArchive(
  context: DevPluginPreparationContext,
  output: String,
  input: DevPluginReference,
  manifest: String,
  includes: (String) -> Boolean,
): DevPluginPreparedSource {
  val entries = ArrayList<DevPluginPreparedEntry>()
  readZipFile(context.inputPath(input)) { name, dataSupplier ->
    if (name == "META-INF/listOfEntities.txt" || (manifest == "coverage-agent" && name == "META-INF/MANIFEST.MF") || includes(name)) {
      val buffer = dataSupplier()
      val content = ByteArray(buffer.remaining())
      buffer.get(content)
      val reference = context.writeFile(output, "entries/${entries.size}", content)
      entries.add(DevPluginPreparedEntry(kind = "file", name = name, input = reference))
    }
    ZipEntryProcessorResult.CONTINUE
  }
  return DevPluginPreparedSource(output, listOf(DevPluginExecutionSource(kind = "entries", manifest = manifest, entries = entries)))
}

private fun createFilter(layout: PluginLayout, moduleName: String): (String) -> Boolean {
  val excludes = commonModuleExcludes + layout.moduleExcludes.getValue(moduleName).map { FileSystems.getDefault().getPathMatcher("glob:$it") }
  return createModuleSourcesNamesFilter(excludes)
}

private fun layoutState(layout: PluginLayout): String {
  val values = ArrayList<String>()
  values.addAll(listOf(layout.mainModule, layout.directoryName, layout.getMainJarName()))
  appendLayoutState(layout, values)
  return digest(values)
}

private fun appendLayoutState(layout: BaseLayout, values: MutableList<String>) {
  values.add(layout.includedModules.size.toString())
  for (module in layout.includedModules) {
    values.addAll(listOf(module.moduleName, module.relativeOutputFile, module.moduleSet.toString()))
  }
  values.add(layout.moduleExcludes.size.toString())
  for ((moduleName, excludes) in layout.moduleExcludes) values.addAll(listOf(moduleName, excludes.size.toString()) + excludes)
  values.add(layout.patchers.size.toString())
  for ((index, patcher) in layout.patchers.withIndex()) {
    if (patcher is LibraryEntriesLayoutPatcher) {
      values.addAll(listOf("library-entries-v1", index.toString(), patcher.libraryName, patcher.libraryModuleName, patcher.prefix, patcher.targetModuleName))
    }
  }
}

private fun digest(values: List<String>): String = devDistSignatureOf(values)
