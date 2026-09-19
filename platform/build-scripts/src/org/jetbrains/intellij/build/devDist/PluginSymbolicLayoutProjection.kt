@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.json.Json
import org.jdom.Element
import org.jdom.Namespace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.getLibraryFileName
import org.jetbrains.intellij.build.impl.BUILT_IN_HELP_MODULE_NAME
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.getLibNameBySourceFile
import org.jetbrains.intellij.build.impl.isSeparateLibraryJar
import org.jetbrains.intellij.build.impl.nameToJarFileName
import org.jetbrains.intellij.build.impl.removeVersionFromJar
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.util.isProductionRuntimeDependency
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Path

private fun nativeFingerprint(values: List<String>): String = devDistSignature {
  putInt(values.size)
  for (value in values) putString(value)
}

/** The comment a content module descriptor states to go into the main jar of the plugin. */
private val PACK_CONTENT_INTO_PLUGIN_JAR_MARKER = Regex("""<!--\s+intellij-build:\s+pack-content-into-plugin-jar\s+-->""")

/**
 * Projects one original layout without reading compiled roots or invoking layout callbacks.
 * The catalogue describes the output provider's selected roots. Descriptor facts describe the prepared descriptor.
 * The result cannot select producers until every required preparation has declared its inputs and contributions.
 * [nativePolicy] enables native derivation at each source occurrence. Its absence preserves the existing caller-supplied preparation model.
 * Native bindings require a policy; an absent policy is not evidence that native handling leaves archives untouched.
 */
@ApiStatus.Internal
fun projectPluginSymbolicLayout(
  layout: PluginLayout,
  project: JpsProject,
  catalogue: PluginSymbolicArtifactCatalogue,
  descriptorFacts: PluginSymbolicDescriptorFacts,
  preparationFacts: PluginSymbolicPreparationFacts = PluginSymbolicPreparationFacts(),
  variant: PluginSymbolicVariant,
  nativePolicy: PluginSymbolicNativePolicy? = null,
): PluginSymbolicLayout {
  val original = if (nativePolicy == null) null else {
    SymbolicLayoutProjector(
      layout, project, catalogue, descriptorFacts, preparationFacts, variant, nativePolicy, collectNativeContext = true,
    ).project()
  }
  return SymbolicLayoutProjector(
    layout, project, catalogue, descriptorFacts, preparationFacts, variant, nativePolicy,
    nativeContexts = original?.let(::nativeAssetContexts).orEmpty(),
  ).project()
}

private fun nativeAssetContexts(original: PluginSymbolicLayout): Map<String, String> {
  val producers = original.preparations.flatMap { preparation -> preparation.outputs.map { it to preparation } }.toMap()
  return original.assets.mapNotNull { asset ->
    val recipe = asset.recipe ?: return@mapNotNull null
    val values = ArrayList<String>()
    values.addAll(listOf("native-asset-context-v1", Json.encodeToString(CanonicalJarRecipe.serializer(), recipe)))
    val visited = HashSet<String>()
    fun visit(input: String) {
      val preparation = producers.get(input) ?: return
      if (!visited.add(preparation.id)) return
      values.add(Json.encodeToString(PluginPackingPreparation.serializer(), preparation))
      preparation.inputs.forEach(::visit)
    }
    asset.inputs.forEach(::visit)
    for (use in original.nativeRequirements.filter { it.occurrence.destination == asset.destination }) {
      val occurrence = use.occurrence
      values.addAll(listOf(occurrence.input, occurrence.channel.name, occurrence.ordinal.toString(), use.modelSignature))
    }
    asset.destination to nativeFingerprint(values)
  }.toMap()
}

private class SymbolicLayoutProjector(
  private val layout: PluginLayout,
  private val project: JpsProject,
  private val catalogue: PluginSymbolicArtifactCatalogue,
  private val descriptors: PluginSymbolicDescriptorFacts,
  private val preparationFacts: PluginSymbolicPreparationFacts,
  private val variant: PluginSymbolicVariant,
  private val nativePolicy: PluginSymbolicNativePolicy?,
  private val collectNativeContext: Boolean = false,
  private val nativeContexts: Map<String, String> = emptyMap(),
) {
  private val artifacts = catalogue.artifacts.associateBy { it.id }
  private val preparedRoots = catalogue.artifacts.filter { it.preparationKey != null }.groupBy { it.preparationKey }
  private val libraries = catalogue.libraries.associateBy { it.moduleName to it.libraryName }
  private val libraryFileCounts = catalogue.libraries.filter { it.id != null }.associate { requireNotNull(it.id) to it.files.size }

  /** The container id of a library with one member, by that member. A native effect may read the container in place of the member. */
  private val singleMemberLibraries = catalogue.libraries.filter { it.id != null && it.files.size == 1 }.associate { it.files.single() to requireNotNull(it.id) }
  private val frontend = FrontendCompatibility(descriptors.frontendRoots, project::findModuleByName)
  private val assembly = PluginSymbolicJarAssembly()
  private val copiedFiles = HashSet<Pair<String, String>>()
  private val effects = LinkedHashMap<String, PluginSymbolicPreparedEffect>()
  private val gaps = LinkedHashMap<String, PluginSymbolicLayoutGap>()
  private val roots = LinkedHashSet<String>()
  private val declaredAssets = LinkedHashMap<String, List<PluginPackingAsset>>()
  private val omittedSlots = HashSet<String>()
  private val nativeSlots = HashMap<Triple<String, String, PluginSymbolicNativeSourceChannel>, Int>()
  private val nativeUses = LinkedHashMap<PluginSymbolicNativeOccurrence, PluginSymbolicNativeUse>()
  private val preparedNativeUses = HashMap<String, PluginSymbolicNativeUse>()

  init {
    require(artifacts.size == catalogue.artifacts.size) { "Duplicate symbolic artifact ID" }
    require(libraries.size == catalogue.libraries.size) { "Duplicate symbolic library owner and name" }
    val libraryIds = HashMap<String, List<String>>()
    for (library in catalogue.libraries) {
      val id = library.id ?: continue
      require(id.isNotBlank() && id !in artifacts) { "Library container '$id' conflicts with an artifact" }
      val previous = libraryIds.putIfAbsent(id, library.files)
      require(previous == null || previous == library.files) { "Library container '$id' has conflicting expansions" }
    }
    require(catalogue.artifacts.all {
      it.id.isNotBlank() && it.kind in setOf("archive", "directory", "file") &&
      it.fileName.isNotBlank() && '/' !in it.fileName && '\\' !in it.fileName
    }) { "An artifact requires an ID, a root kind, and a file name" }
  }

  fun project(): PluginSymbolicLayout {
    if (nativePolicy == null && preparationFacts.nativeBindings.isNotEmpty()) {
      gap("native-policy", "Native occurrence bindings require the selected distribution's native policy")
    }
    if (!descriptors.isPluginXmlFinal &&
        (layout.hasRawPluginXmlPatcher || layout.hasPluginXmlPatcher || layout.hasCustomVersion ||
         variant.scramble && layout.deprecatedPostProcessor.isNotEmpty() || preparationFacts.effects.containsKey("descriptor"))) {
      effect("descriptor", "Descriptor callbacks require ordered inputs and the resulting descriptor facts")?.let {
        roots.addAll(it.preparation.outputs)
      }
    }
    var requiresModulePatches = false
    for (index in layout.patchers.indices) {
      val key = "layout-patcher:$index"
      if (omitLayoutSlot(key)) continue
      requiresModulePatches = true
      effect(key, "LayoutPatcher does not declare its inputs or ordered module patches")
    }
    if (requiresModulePatches && preparationFacts.modulePatches.isEmpty()) {
      gap("module-patches", "Layout patchers require the final ordered patches, including the plugin descriptor")
    }
    for (item in contributions()) {
      addModule(item)
    }
    addCustomModuleLibraries()
    addProjectLibraries()
    addResources()
    if (variant.scramble && layout.pathsToScramble.isNotEmpty()) {
      gap("scramble", "The layout does not declare transformed jar recipes and the scrambler's complete inputs")
    }
    val reportGap: (PluginSymbolicLayoutGap) -> Unit = { gap(it.key, it.detail) }
    val assets = buildList {
      addAll(assembly.assets(preparationFacts.preparedSourceManifests, libraryFileCounts, reportGap))
      for ((key, effect) in effects) {
        val resource = key.startsWith("resource:") || key.startsWith("resource-generator:") || key.startsWith("platform-resource-generator:")
        effect.assets.mapTo(this) {
          resolvePluginSymbolicManifest(if (resource) it.copy(classPath = false) else it, preparationFacts.preparedSourceManifests, libraryFileCounts, reportGap)
        }
      }
      for ((key, declared) in declaredAssets) {
        val resource = key.startsWith("resource:") || key.startsWith("resource-generator:") || key.startsWith("platform-resource-generator:")
        declared.mapTo(this) {
          resolvePluginSymbolicManifest(if (resource) it.copy(classPath = false) else it, preparationFacts.preparedSourceManifests, libraryFileCounts, reportGap)
        }
      }
    }
    val preparationsById = LinkedHashMap<String, PluginPackingPreparation>()
    for (preparation in preparationFacts.dependencies + effects.values.map { it.preparation }) {
      val previous = preparationsById.putIfAbsent(preparation.id, preparation)
      if (previous != null && previous != preparation) {
        gap("preparation:${preparation.id}", "Preparation '${preparation.id}' has conflicting definitions")
      }
    }
    val preparations = preparationsById.values.toList()
    validateInputs(assets, preparations)
    if (!collectNativeContext) {
      for (occurrence in preparationFacts.nativeBindings.keys) {
        if (occurrence !in nativeUses) gap("native-binding:$occurrence", "The native binding names an unknown source occurrence")
      }
    }
    for (slot in preparationFacts.omittedSlots) {
      if (slot !in omittedSlots) gap("omitted-slot:$slot", "The omitted slot does not name a selected layout callback")
    }
    return PluginSymbolicLayout(
      plugin = layout.mainModule,
      variant = variant.id,
      assets = assets,
      preparations = preparations,
      preparationRoots = roots.toList(),
      gaps = gaps.values.toList(),
      nativeRequirements = nativeUses.values.toList(),
    )
  }

  private fun contributions(): List<ModuleItem> {
    val result = ArrayList<ModuleItem>()
    val added = HashSet<String>()
    val customPaths = layout.includedModules.filter {
      '/' !in it.relativeOutputFile && it.relativeOutputFile != layout.getMainJarName()
    }.mapTo(HashSet()) { it.moduleName }
    val pluginXml = descriptors.pluginXml
    if (pluginXml == null) {
      if (layout.mainModule != BUILT_IN_HELP_MODULE_NAME) {
        gap("plugin-descriptor", "The prepared plugin descriptor is missing")
      }
    }
    else {
      val descriptor = JDOMUtil.load(pluginXml)
      if (hasUnresolvedDescriptorIncludes(descriptor)) {
        gap("descriptor-includes", "Resolve descriptor includes before projecting the content order")
      }
      for (content in descriptor.getChildren("content")) {
        for (element in content.getChildren("module")) {
          val name = element.getAttributeValue("name") ?: continue
          if ('/' in name || !added.add(name)) continue
          val destination = contentDestination(name, element.getAttributeValue("loading"), customPaths)
          if (destination == null) {
            added.remove(name)
          }
          else {
            result.add(ModuleItem(name, destination, reason = null))
          }
        }
      }
    }
    for (item in layout.includedModules) {
      if (item.moduleName in added && '/' !in item.relativeOutputFile) {
        require(item.relativeOutputFile == layout.getMainJarName()) {
          "Custom output path is not allowed for content module '${item.moduleName}': ${item.relativeOutputFile}"
        }
        continue
      }
      result.add(item)
      added.add(item.moduleName)
    }
    if (layout.auto) {
      val mainModule = module(layout.mainModule)
      val prefix = "${layout.mainModule.removeSuffix(".plugin")}."
      for (dependency in mainModule?.getProductionModuleDependencies(withTests = false).orEmpty()) {
        val name = dependency.moduleReference.moduleName
        if (name.startsWith(prefix) && added.add(name) && name !in descriptors.packedElsewhere) {
          result.add(ModuleItem(name, defaultJar(name), reason = null))
        }
      }
    }
    return result
  }

  private fun contentDestination(name: String, loading: String?, customPaths: Set<String>): String? {
    if (loading == "embedded" && name in customPaths) return null
    if (!descriptors.moduleXml.containsKey(name)) {
      gap("module-descriptor:$name", "Declare the descriptor text or its known absence")
      return null
    }
    val xml = descriptors.moduleXml.get(name)
    if (xml == null && loading != "embedded") {
      gap("module-descriptor:$name", "The content module descriptor is missing")
      return null
    }
    val packIntoMain = xml != null && PACK_CONTENT_INTO_PLUGIN_JAR_MARKER.containsMatchIn(xml)
    if (loading == "embedded") {
      return if (packIntoMain) defaultJar(name) else "$name.jar"
    }
    val module = module(name) ?: return null
    val hasModuleLibraries = name !in layout.getModulesWithExcludedModuleLibraries() &&
                            libraryDependencies(module, withTests = false).any { it.libraryReference.parentReference is JpsModuleReference }
    val separate = !packIntoMain &&
                   (!hasRootXmlAttribute(requireNotNull(xml), "package") || hasModuleLibraries ||
                    frontend.isSplit(layout.mainModule, name))
    return when {
      separate -> "modules/$name.jar"
      name in customPaths -> null
      else -> defaultJar(name)
    }
  }

  private fun defaultJar(moduleName: String): String {
    return if (frontend.isSplit(layout.mainModule, moduleName)) {
      layout.getMainJarName().removeSuffix(".jar") + "-frontend.jar"
    }
    else {
      layout.getMainJarName()
    }
  }

  private fun addModule(item: ModuleItem) {
    val module = module(item.moduleName) ?: return
    val destination = "lib/${item.relativeOutputFile}"
    val declaredPatches = preparationFacts.modulePatches.get(module.name)
    val patches = declaredPatches ?: if (module.name == layout.mainModule && descriptors.pluginXml != null) {
      listOf(JarSourceRecipe(descriptors.pluginXmlInput, "file", "none", PLUGIN_XML_RELATIVE_PATH, options = listOf("patch")))
    }
    else {
      emptyList()
    }
    if (module.name == layout.mainModule && descriptors.pluginXml != null &&
        patches.none { it.input == descriptors.pluginXmlInput && (it.entry == PLUGIN_XML_RELATIVE_PATH || it.kind == "prepared") }) {
      gap("descriptor-patch", "The final patches must contain the prepared plugin descriptor")
    }
    val moduleSources = ArrayList<PluginSymbolicJarSource>()
    patches.filter { module.name == layout.mainModule || it.entry != PLUGIN_XML_RELATIVE_PATH }.mapTo(moduleSources, ::PluginSymbolicJarSource)
    val moduleRoots = catalogue.moduleRoots.get(module.name)
    if (moduleRoots == null) gap("module-roots:${module.name}", "Declare the ordered output roots, including an explicit empty list")
    val orderedRoots = moduleRoots.orEmpty()
    val excludes = layout.moduleExcludes.get(module.name).orEmpty()
    val directoryFilterIdentity = if (excludes.isEmpty()) "common-module-excludes" else Any()
    val filteredSources by lazy(LazyThreadSafetyMode.NONE) {
      val prepared = effect("module-filter:${module.name}", "Custom module filters require their root inputs and prepared sources")
      if (prepared == null) null else {
        val contributions = prepared.sourceContributions
        val outputs = contributions.values.flatten()
        if (prepared.sources.isNotEmpty() || orderedRoots.distinct().size != orderedRoots.size || contributions.keys != orderedRoots.toSet() ||
            contributions.values.any { it.isEmpty() } || outputs.any { it.kind != "prepared" || it.input !in prepared.preparation.outputs } ||
            outputs.map { it.input }.distinct().size != outputs.size || outputs.map { it.input }.toSet() != prepared.preparation.outputs.toSet()) {
          gap("module-filter-sources:${module.name}", "Declare exactly one ordered contribution for each module root, with distinct prepared outputs")
          null
        }
        else {
          requireInputs(prepared, orderedRoots)
          contributions
        }
      }
    }
    for (input in orderedRoots) {
      val identity = if (artifacts.get(input)?.kind == "directory") directoryFilterIdentity to input else Any()
      moduleSources.add(PluginSymbolicJarSource(identity) {
        val use = nativeUse(
          input, destination, PluginSymbolicNativeSourceChannel.MODULE_OUTPUT, "module-v1", excludes,
          "module-filter:${module.name}".takeIf { excludes.isNotEmpty() },
        )
        val original = if (excludes.isEmpty()) sources(input, "module-v1") else filteredSources?.get(input).orEmpty()
        applyNative(use, original)
      })
    }
    assembly.addOriginalModule(destination, moduleSources, testOutput = module.name in catalogue.testModules, descriptorModule = module.name == layout.mainModule)
    if (variant.searchableOptions && module.name != BUILT_IN_HELP_MODULE_NAME) {
      if (module.name != layout.mainModule && !descriptors.moduleXml.containsKey(module.name)) {
        gap("searchable-options-descriptor:${module.name}", "Declare the layout module's descriptor text or its known absence")
      }
      else if (module.name == layout.mainModule || descriptors.moduleXml.get(module.name) != null) {
        val prepared = effect("searchable-options:${module.name}", "Declare the searchable-option inputs and ordered sources")
        assembly.addSources(destination, prepared?.sources.orEmpty())
      }
    }
    if (module.name == layout.mainModule) {
      for ((index, customAsset) in layout.customAssets.withIndex()) {
        if (customAsset.platformSpecific != null) continue
        val prepared = layoutEffect("custom-asset:$index", "CustomAssetDescriptor.getSources does not declare its inputs or source order")
        if (customAsset.relativePath == null) {
          assembly.addSources(destination, prepared?.sources.orEmpty())
        }
        else {
          assembly.addSources(requireNotNull(customAsset.relativePath), prepared?.sources.orEmpty(), separate = true)
        }
      }
    }
    if (module.name !in layout.getModulesWithExcludedModuleLibraries()) {
      addModuleLibraries(item, module)
    }
  }

  private fun addModuleLibraries(item: ModuleItem, module: JpsModule) {
    val destination = "lib/${item.relativeOutputFile}"
    val excluded = layout.getExcludedModuleLibraries().get(module.name).orEmpty()
    for (dependency in libraryDependencies(module, withTests = module.name in catalogue.testModules)) {
      if (dependency.libraryReference.parentReference !is JpsModuleReference) continue
      val library = dependency.library
      if (library == null) {
        gap("library:${module.name}:${dependency.libraryReference.libraryName}", "The JPS library reference does not resolve")
        continue
      }
      val name = getLibraryFileName(library)
      if (name in excluded || layout.getIncludedModuleLibraries().any { it.libraryName == name && !it.extraCopy }) continue
      val libraryFiles = libraryFiles(library)
      if (item.reason == ModuleIncludeReasons.PRODUCT_MODULES) {
        assembly.addOriginalSources(destination, librarySources(library, libraryFiles, destination, PluginSymbolicNativeSourceChannel.MODULE_JAR_LIBRARY))
        continue
      }
      val files = claim(libraryFiles, destination).toMutableList()
      val mainJar = item.relativeOutputFile == layout.getMainJarName()
      if (!mainJar || files.size > 1) {
        for (index in files.indices.reversed()) {
          val input = files.get(index)
          val fileName = artifacts.getValue(input).fileName
          val separate = if (mainJar) fileName.endsWith("-rt.jar") || fileName.startsWith("maven-") else isSeparateLibraryJar(fileName)
          if (separate) {
            files.removeAt(index)
            val sibling = "lib/${removeVersionFromJar(fileName)}"
            assembly.addOriginalSources(sibling, librarySources(library, claim(listOf(input), sibling), sibling))
          }
        }
      }
      val target = if (mainJar) "lib/${removeVersionFromJar(nameToJarFileName(name))}" else destination
      val channel = if (mainJar) PluginSymbolicNativeSourceChannel.LAYOUT_LIBRARY else PluginSymbolicNativeSourceChannel.MODULE_JAR_LIBRARY
      assembly.addOriginalSources(target, librarySources(library, files, target, channel))
    }
  }

  private fun addCustomModuleLibraries() {
    for (item in layout.getIncludedModuleLibraries()) {
      val module = module(item.moduleName) ?: continue
      val library = module.libraryCollection.libraries.firstOrNull { getLibraryFileName(it) == item.libraryName }
      if (library == null) {
        gap("library:${item.moduleName}:${item.libraryName}", "The layout's module library does not exist")
        continue
      }
      val relativePath = item.relativeOutputPath
      val target = if (relativePath.endsWith(".jar")) relativePath else join(relativePath, nameToJarFileName(item.libraryName))
      val destination = "lib/$target"
      assembly.addOriginalSources(destination, librarySources(library, claim(libraryFiles(library), destination), destination))
    }
  }

  private fun addProjectLibraries() {
    for (data in layout.getIncludedProjectLibraries().sortedBy { it.libraryName }) {
      val library = project.libraryCollection.findLibrary(data.libraryName)
      if (library == null) {
        gap("library:${data.libraryName}", "The layout's project library does not exist")
        continue
      }
      val files = libraryFiles(library)
      val outPath = data.outPath.orEmpty()
      if (outPath.endsWith(".jar") || data.packMode == LibraryPackMode.STANDALONE_MERGED) {
        val target = if (outPath.endsWith(".jar")) outPath else join(outPath, nameToJarFileName(library.name))
        val destination = "lib/$target"
        assembly.addOriginalSources(destination, librarySources(library, claim(files, destination), destination))
      }
      else {
        for (input in files) {
          val destination = "lib/${join(outPath, artifacts.getValue(input).fileName)}"
          assembly.addOriginalSources(destination, librarySources(library, listOf(input), destination))
        }
      }
    }
  }

  private fun addResources() {
    if (!variant.skipCustomResourceGenerators) {
      for ((index, resource) in layout.resourcePaths.withIndex()) {
        resourceEffect("resource:$index", "Declare source entries and output paths for ${resource.moduleName}:${resource.resourcePath}")
      }
      for (index in layout.resourceGenerators.indices) {
        resourceEffect("resource-generator:$index", "ResourceGenerator declares neither all inputs nor its output paths")
      }
    }
    // A platform slot is keyed by its index among the callbacks that serve the distribution, so the key is the same on every platform.
    val distribution = variant.distribution ?: return
    for (index in layout.platformResourceGeneratorsBundledAndDevMode.get(distribution).orEmpty().indices) {
      resourceEffect("platform-resource-generator:$index", "The selected platform generator requires inputs and output paths")
    }
    for (index in layout.customAssets.filter { it.platformSpecific == distribution }.indices) {
      layoutEffect("platform-custom-asset:$index", "Platform custom assets require explicit copy, extraction, mode, and link declarations")
    }
  }

  private fun sources(input: String, filter: String): List<JarSourceRecipe> {
    val artifact = artifacts.get(input)
    if (artifact == null) {
      gap("artifact:$input", "The artifact catalogue lacks this input")
      return emptyList()
    }
    val preparationKey = artifact.preparationKey
    if (preparationKey != null) {
      val prepared = effect(preparationKey, "The source requires declared filter or native preparation") ?: return emptyList()
      requireInputs(prepared, listOf(input))
      if (prepared.sourceContributions.isNotEmpty()) {
        val contributions = HashMap<JarSourceRecipe, String>()
        if (prepared.sourceContributions.any { (root, sources) ->
          sources.any { source -> contributions.putIfAbsent(source, root)?.let { it != root } == true }
        }) {
          gap("source-contributions:$preparationKey", "Shared preparation sources must identify distinct per-input contributions, not a merged bundle")
          return emptyList()
        }
        val contribution = prepared.sourceContributions.get(input)
        if (contribution == null) gap("source-contributions:$preparationKey:$input", "Declare the ordered contribution of '$input'")
        return contribution.orEmpty()
      }
      if (preparedRoots.getValue(preparationKey).size > 1) {
        gap("source-contributions:$preparationKey", "A shared preparation requires per-input contributions to preserve original source positions")
        return emptyList()
      }
      return prepared.sources
    }
    if (artifact.kind == "directory") {
      gap("directory-source:$input", "Declare directory preparation for '$input'; the jar compiler does not accept raw directory roots")
      return emptyList()
    }
    if (artifact.kind == "file") {
      gap("artifact:$input", "A file root requires a declared entry name or preparation")
      return emptyList()
    }
    val kind = if (artifact.kind == "archive" && filter == "module-v1") "module" else artifact.kind
    return listOf(JarSourceRecipe(input, kind, filter))
  }

  private fun librarySources(
    library: JpsLibrary,
    files: List<String>,
    destination: String,
    channel: PluginSymbolicNativeSourceChannel = PluginSymbolicNativeSourceChannel.LAYOUT_LIBRARY,
  ): List<PluginSymbolicJarSource> {
    if (files.isEmpty()) return emptyList()
    val owner = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    val name = if (owner == null) library.name else getLibraryFileName(library)
    val record = libraries.get(owner to name)
    val group = if (record?.id != null && files == record.files && files.all {
      val artifact = artifacts.getValue(it)
      artifact.kind == "archive" && artifact.preparationKey == null
    }) {
      PluginSymbolicLibraryGroup(JarSourceRecipe(record.id, "library", "library-v1"), files)
    }
    else null
    return files.map { input ->
      val candidate = if (channel == PluginSymbolicNativeSourceChannel.MODULE_JAR_LIBRARY) {
        nativePolicy?.presignedLibraries?.containsKey(getLibNameBySourceFile(Path.of(artifacts.getValue(input).fileName)))
      }
      else false
      PluginSymbolicJarSource(PluginSymbolicLibrarySourceIdentity(input, candidate), group) {
        val use = nativeUse(input, destination, channel, "library-v1")
        applyNative(use, sources(input, "library-v1"))
      }
    }
  }

  private fun nativeUse(
    input: String,
    destination: String,
    channel: PluginSymbolicNativeSourceChannel,
    filter: String,
    excludes: List<String> = emptyList(),
    filterKey: String? = null,
  ): PluginSymbolicNativeUse? {
    val policy = nativePolicy ?: return null
    val artifact = artifacts.get(input) ?: return null
    val slot = Triple(destination, input, channel)
    val ordinal = nativeSlots.getOrDefault(slot, 0)
    nativeSlots.put(slot, ordinal + 1)
    val occurrence = PluginSymbolicNativeOccurrence(destination, input, channel, ordinal)
    val requirement = try {
      derivePluginSymbolicNativeRequirements(layout, listOf(PluginSymbolicNativeSource(artifact, channel)), policy).single()
    }
    catch (error: IllegalArgumentException) {
      gap("native-policy:$occurrence", checkNotNull(error.message))
      return null
    }
    val keys = listOfNotNull(artifact.preparationKey, filterKey).distinct()
    val signature = ArrayList<String>()
    signature.addAll(listOf("native-use-v1", destination, input, channel.name, ordinal.toString(), requirement.modelSignature, filter, excludes.size.toString()))
    signature.addAll(excludes)
    signature.add(artifact.fileName)
    signature.add(nativeContexts.get(destination).orEmpty())
    signature.add(keys.size.toString())
    for (key in keys) {
      val effect = preparationFacts.effects.get(key)
      signature.add(key)
      if (effect == null) {
        signature.add("missing")
        continue
      }
      val preparation = effect.preparation
      signature.addAll(listOf("present", preparation.id, preparation.modelSignature, preparation.inputs.size.toString()))
      signature.addAll(preparation.inputs)
      signature.add(preparation.outputs.size.toString())
      signature.addAll(preparation.outputs)
      val contributions = effect.sourceContributions.get(input) ?: effect.sources
      signature.add(contributions.size.toString())
      for (source in contributions) {
        signature.addAll(listOf(source.input, source.kind, source.filter, source.entry))
        signature.add(source.options.size.toString())
        signature.addAll(source.options)
      }
    }
    val use = PluginSymbolicNativeUse(occurrence, requirement.handling, requirement.distributionPrefix, keys, nativeFingerprint(signature))
    nativeUses.put(occurrence, use)
    if (collectNativeContext) return use
    val binding = preparationFacts.nativeBindings.get(occurrence)
    if (requirement.handling == PluginSymbolicNativeHandling.UNTOUCHED) {
      if (binding != null) gap("native-binding:$occurrence", "An untouched source must not have a native binding")
    }
    else if (binding == null || binding.requirementSignature != use.modelSignature || binding.effectKey.isBlank()) {
      gap("native-binding:$occurrence", "The source requires a native effect bound to its current occurrence and policy signature")
    }
    else if (!preparationFacts.effects.containsKey(binding.effectKey)) {
      gap("native-binding:$occurrence", "The bound native effect '${binding.effectKey}' is missing")
    }
    return use
  }

  private fun applyNative(use: PluginSymbolicNativeUse?, original: List<JarSourceRecipe>): List<JarSourceRecipe> {
    if (use == null || collectNativeContext) return original
    if (use.handling == PluginSymbolicNativeHandling.UNTOUCHED) return recordNativeSources(use, original)
    val occurrence = use.occurrence
    val binding = preparationFacts.nativeBindings.get(occurrence) ?: return emptyList()
    if (binding.requirementSignature != use.modelSignature || binding.effectKey.isBlank()) return emptyList()
    val native = preparationFacts.effects.get(binding.effectKey) ?: return emptyList()
    fun invalid(detail: String): List<JarSourceRecipe> {
      gap("native-effect:$occurrence", detail)
      return emptyList()
    }
    if (native.preparation.id.isBlank() || native.preparation.inputs.isEmpty() || native.preparation.outputs.isEmpty() ||
        native.preparation.modelSignature.isBlank() || (native.preparation.inputs + native.preparation.outputs).any { it.isBlank() } ||
        native.preparation.outputs.distinct().size != native.preparation.outputs.size ||
        native.sources.isEmpty() || native.sourceContributions.isNotEmpty() ||
        native.sources.any { it.kind != "prepared" || it.filter != "prepared" || it.input !in native.preparation.outputs || it.input in artifacts } ||
        native.sources.map { it.input }.distinct().size != native.sources.size) {
      return invalid("A native occurrence requires distinct prepared contributions from its declared effect outputs")
    }
    val required = LinkedHashSet<String>()
    for (key in use.preparationKeys) {
      val generic = preparationFacts.effects.get(key) ?: return invalid("The combined native preparation lacks '$key'")
      val contributions = generic.sourceContributions.get(occurrence.input) ?: generic.sources
      if (contributions.isEmpty() || contributions.any { it.input !in generic.preparation.outputs }) {
        return invalid("The combined native preparation requires the declared source outputs of '$key'")
      }
      required.addAll(contributions.map { it.input })
    }
    val closure = nativeInputClosure(native.preparation, occurrence) ?: return emptyList()
    val consumesSource = occurrence.input in closure || singleMemberLibraries.get(occurrence.input)?.let { it in closure } == true
    if (!consumesSource || !closure.containsAll(required)) {
      return invalid("The native effect must consume this raw input, or its library, and all preceding generic or Java-filter outputs: ${listOf(occurrence.input) + required}")
    }
    if (original.isEmpty()) return invalid("The native effect lacks the original source's complete preparation")
    effects.putIfAbsent(binding.effectKey, native)
    return recordNativeSources(use, native.sources)
  }

  private fun nativeInputClosure(preparation: PluginPackingPreparation, occurrence: PluginSymbolicNativeOccurrence): Set<String>? {
    val producers = HashMap<String, PluginPackingPreparation>()
    for (producer in preparationFacts.dependencies + preparationFacts.effects.values.map { it.preparation }) {
      for (output in producer.outputs) {
        val previous = producers.putIfAbsent(output, producer)
        if (previous != null && previous != producer) {
          gap("native-effect:$occurrence", "Native preparation output '$output' has conflicting producers")
          return null
        }
      }
    }
    val visited = HashSet<String>()
    val visiting = HashSet<String>()
    fun visit(input: String): Boolean {
      if (input in visiting) return false
      if (!visited.add(input)) return true
      visiting.add(input)
      for (dependency in producers.get(input)?.inputs.orEmpty()) if (!visit(dependency)) return false
      visiting.remove(input)
      return true
    }
    if (!preparation.inputs.all(::visit)) {
      gap("native-effect:$occurrence", "The native effect has a preparation cycle")
      return null
    }
    return visited
  }

  private fun recordNativeSources(use: PluginSymbolicNativeUse, sources: List<JarSourceRecipe>): List<JarSourceRecipe> {
    for (source in sources) {
      if (source.input in artifacts) continue
      val previous = preparedNativeUses.putIfAbsent(source.input, use)
      if (previous != null && previous.occurrence != use.occurrence &&
          (previous.handling != PluginSymbolicNativeHandling.UNTOUCHED || use.handling != PluginSymbolicNativeHandling.UNTOUCHED)) {
        gap("native-effect:${use.occurrence}", "Prepared output '${source.input}' is shared by incompatible source occurrences")
        return emptyList()
      }
    }
    return sources
  }

  private fun libraryFiles(library: JpsLibrary): List<String> {
    val owner = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    val name = if (owner == null) library.name else getLibraryFileName(library)
    val record = libraries.get(owner to name)
    if (record == null) {
      gap("library-files:$owner:$name", "Declare the ordered file identities from the output provider")
      return emptyList()
    }
    return record.files.filter { input ->
      if (artifacts.containsKey(input)) true
      else {
        gap("artifact:$input", "The library references an unknown artifact")
        false
      }
    }
  }

  private fun claim(files: List<String>, destination: String): List<String> {
    return files.filter { copiedFiles.add(it to destination) }
  }

  /** Resolves a layout module name. A Product DSL `._test` name resolves to the JPS module it marks. */
  private fun module(name: String): JpsModule? {
    val module = project.findModuleByName(name) ?: project.findModuleByName(name.removeSuffix("._test"))
    if (module == null) gap("module:$name", "The JPS module does not exist")
    return module
  }

  private fun libraryDependencies(module: JpsModule, withTests: Boolean): List<JpsLibraryDependency> {
    val service = JpsJavaExtensionService.getInstance()
    return module.dependenciesList.dependencies.filterIsInstance<JpsLibraryDependency>().filter {
      isProductionRuntimeDependency(it, service, withTests)
    }
  }

  private fun effect(key: String, detail: String): PluginSymbolicPreparedEffect? {
    val effect = preparationFacts.effects.get(key)
    if (effect == null) {
      gap(key, detail)
      return null
    }
    require((effect.preparation.outputs.isNotEmpty() || effect.preparation.alwaysRun) && effect.preparation.modelSignature.isNotBlank()) {
      "Preparation '$key' requires outputs or an always-run marker, and a signature"
    }
    effects.putIfAbsent(key, effect)
    return effect
  }

  private fun resourceEffect(key: String, detail: String) {
    if (omitLayoutSlot(key)) return
    val assets = preparationFacts.declaredAssets.get(key)
    if (assets == null) {
      effect(key, detail)
      return
    }
    require(!preparationFacts.effects.containsKey(key) && assets.isNotEmpty()) {
      "Declared asset slot '$key' must contain assets and must not also contain a preparation"
    }
    declaredAssets.put(key, assets)
  }

  private fun layoutEffect(key: String, detail: String): PluginSymbolicPreparedEffect? {
    return if (omitLayoutSlot(key)) null else effect(key, detail)
  }

  private fun omitLayoutSlot(key: String): Boolean {
    if (key !in preparationFacts.omittedSlots) return false
    require(key !in preparationFacts.effects && key !in preparationFacts.declaredAssets && omittedSlots.add(key)) {
      "Omitted layout slot '$key' must not declare another binding"
    }
    return true
  }

  private fun requireInputs(effect: PluginSymbolicPreparedEffect, inputs: List<String>) {
    val producers = (preparationFacts.dependencies + preparationFacts.effects.values.map { it.preparation })
      .flatMap { preparation -> preparation.outputs.map { it to preparation } }.toMap()
    val required = HashSet<String>()
    fun visit(input: String) {
      if (required.add(input)) producers.get(input)?.inputs?.forEach(::visit)
    }
    effect.preparation.inputs.forEach(::visit)
    require(required.containsAll(inputs)) { "Preparation '${effect.preparation.id}' does not require every source input: $inputs" }
  }

  private fun validateInputs(assets: List<PluginPackingAsset>, preparations: List<PluginPackingPreparation>) {
    val produced = preparations.flatMap { it.outputs }.toSet()
    val libraryIds = catalogue.libraries.mapNotNull { it.id }.toSet()
    val consumed = assets.flatMap { it.inputs } + preparations.flatMap { it.inputs } + roots
    for (input in consumed) {
      if (input !in artifacts && input !in produced && input !in libraryIds) {
        gap("artifact:$input", "Declare this input in the catalogue or preparation graph")
      }
    }
    for ((key, effect) in effects) {
      val used = effect.preparation.alwaysRun || effect.preparation.outputs.any { it in consumed }
      if (!used) gap(key, "The declared preparation has no output in the symbolic contributions")
    }
  }

  private fun gap(key: String, detail: String) {
    gaps.putIfAbsent(key, PluginSymbolicLayoutGap(key, detail))
  }

  private fun join(directory: String, fileName: String): String {
    return if (directory.isEmpty()) fileName else "$directory/$fileName"
  }
}

private fun hasUnresolvedDescriptorIncludes(element: Element): Boolean {
  for (child in element.children) {
    if (child.name == "include" && child.namespace == JDOMUtil.XINCLUDE_NAMESPACE) {
      if (child.getAttribute("href") == null || child.getAttribute("base", Namespace.XML_NAMESPACE) != null ||
          child.getChild("fallback", child.namespace) == null && child.getAttribute("includeIf") == null &&
          child.getAttribute("includeUnless") == null) {
        return true
      }
    }
    else if (hasUnresolvedDescriptorIncludes(child)) {
      return true
    }
  }
  return false
}
