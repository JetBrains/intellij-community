@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparationFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparedEffect
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparedSourceManifest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetPreparation
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.DevPluginReference
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import java.nio.file.Path

data class GeneratedDevPluginLayoutAssetBindings(
  @JvmField val facts: PluginSymbolicPreparationFacts,
  @JvmField val catalogueFacts: DevDistPluginCatalogueFacts,
  @JvmField val operations: List<DevPluginPreparationOperation>,
)

/**
 * Binds the layout assets of [owner] for one plan record. [hostPlatform] is the `HOST_PLATFORMS` entry of the record's
 * distribution, or null for a record without one. An asset that names host platforms stays only in the plan of a named
 * platform, see [DevPluginLayoutAsset.hostPlatforms]. A callback whose every asset leaves the plan is an omitted slot of it.
 * [binder] binds the closed asset sources of the half, see [DevDistAssetBinder].
 */
@ApiStatus.Internal
fun generateDevPluginLayoutAssetBindings(
  key: String,
  owner: DevPluginLayoutAssetOwner,
  requestedFormat: String,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  binder: DevDistAssetBinder,
  resources: DevDistResourceSources = DevDistResourceSources(index, outputProvider),
  hostPlatform: String? = null,
): GeneratedDevPluginLayoutAssetBindings {
  val spec = owner.devPluginLayoutAssetSpec
  val omitted = GeneratedDevPluginLayoutAssetBindings(
    facts = PluginSymbolicPreparationFacts(omittedSlots = setOf(key)),
    catalogueFacts = DevDistPluginCatalogueFacts(),
    operations = emptyList(),
  )
  if (spec.omitted) {
    return omitted
  }
  require(spec.assets.isNotEmpty()) { "Layout callback '$key' must declare assets or an explicit omission" }
  val selectedAssets = selectHostPlatformAssets(key, spec.assets, hostPlatform)
  if (selectedAssets.isEmpty()) {
    return omitted
  }
  val resolver = DevPluginLayoutAssetSourceResolver(key, spec.sources, index, outputProvider, resources, binder)
  val concreteAssets = ArrayList<ConcreteLayoutAsset>()
  val operationInputs = ArrayList<DevPluginReference>()
  val libraryInputs = HashSet<Int>()
  val inputIndexes = HashMap<Int, List<Int>>()

  fun indexes(sourceIndex: Int): List<Int> {
    require(sourceIndex in spec.sources.indices) { "Layout callback '$key' has an invalid source index $sourceIndex" }
    return inputIndexes.getOrPut(sourceIndex) {
      resolver.resolve(sourceIndex).references.map { source ->
        operationInputs.add(source.reference)
        if (source.library) libraryInputs.add(operationInputs.lastIndex)
        operationInputs.lastIndex
      }
    }
  }

  for (asset in selectedAssets) {
    val transform = asset.transform
    val sourceIndexes = asset.sources.flatMap(::indexes)
    if (transform == null && sourceIndexes.size > 1) {
      val sources = asset.sources.flatMap { resolver.resolve(it).references }
      require(sources.size == sourceIndexes.size && sources.all { it.kind != "directory" && !it.library }) {
        "Expanded direct layout asset '$key' requires regular file sources"
      }
      for ((source, inputIndex) in sources.zip(sourceIndexes)) {
        concreteAssets.add(ConcreteLayoutAsset(
          asset = asset.copy(destination = joinLayoutPath(asset.destination, source.fileName), sources = listOf(inputIndex)),
          directory = false,
        ))
      }
    }
    else {
      val directory = transform?.kind == "archive-tree" ||
                      asset.sources.any { resolver.resolve(it).references.any { source -> source.kind == "directory" } }
      concreteAssets.add(ConcreteLayoutAsset(asset.copy(sources = sourceIndexes), directory))
    }
  }

  if (isDirectDeclaredFile(requestedFormat, concreteAssets, operationInputs)) {
    val asset = concreteAssets.single().asset
    val source = operationInputs.single()
    require(source.path.isEmpty() && 0 !in libraryInputs) { "Declared layout file '$key' must bind a complete Bazel file" }
    return GeneratedDevPluginLayoutAssetBindings(
      facts = PluginSymbolicPreparationFacts(declaredAssets = mapOf(key to listOf(PluginPackingAsset(
        destination = asset.destination,
        inputs = listOf(source.artifact),
        mode = asset.mode.takeIf { it != 0 } ?: 420,
        classPath = false,
      )))),
      catalogueFacts = resolver.catalogueFacts(),
      operations = emptyList(),
    )
  }

  val root = when (requestedFormat) {
    "entries" -> ""
    "tree" -> commonLayoutRoot(concreteAssets)
    else -> error("Unknown layout callback format '$requestedFormat' for '$key'")
  }
  val payloadAssets = if (requestedFormat == "tree") {
    concreteAssets.map { concrete -> concrete.asset.copy(destination = relativeLayoutPath(root, concrete.asset.destination)) }
  }
  else {
    concreteAssets.map(ConcreteLayoutAsset::asset)
  }
  val id = "layout-assets:$key"
  val output = "$id:output"
  val operation = DevPluginPreparationOperation(
    id = id,
    kind = "layout-assets",
    inputs = operationInputs,
    output = output,
    manifest = "keep",
    layoutAssets = DevPluginLayoutAssetPreparation(format = requestedFormat, root = root, assets = payloadAssets),
  )
  val effect = when (requestedFormat) {
    "entries" -> PluginSymbolicPreparedEffect(
      operation = operation,
      sources = listOf(JarSourceRecipe(output, "prepared", "prepared")),
    )
    "tree" -> PluginSymbolicPreparedEffect(
      operation = operation,
      assets = listOf(PluginPackingAsset(
        destination = root,
        inputs = listOf(output),
        kind = "tree",
        classPath = false,
      )),
    )
    else -> error("Unknown layout callback format '$requestedFormat' for '$key'")
  }
  return GeneratedDevPluginLayoutAssetBindings(
    facts = PluginSymbolicPreparationFacts(
      effects = mapOf(key to effect),
      preparedSourceManifests = if (requestedFormat == "entries") {
        mapOf(output to PluginSymbolicPreparedSourceManifest(1, listOf("keep")))
      }
      else {
        emptyMap()
      },
    ),
    catalogueFacts = resolver.catalogueFacts(),
    operations = listOf(operation),
  )
}

/**
 * The assets of [assets] that serve [hostPlatform], with their platform lists removed: the payload of one plan record
 * never carries them. Every named platform must be a `HOST_PLATFORMS` entry, so a typo omits no asset in silence.
 */
private fun selectHostPlatformAssets(key: String, assets: List<DevPluginLayoutAsset>, hostPlatform: String?): List<DevPluginLayoutAsset> {
  require(hostPlatform == null || hostPlatform in HOST_PLATFORMS) { "Layout callback '$key' is bound for an unknown host platform '$hostPlatform'" }
  return assets.mapNotNull { asset ->
    val platforms = asset.hostPlatforms
    require(platforms.all { it in HOST_PLATFORMS } && platforms.distinct().size == platforms.size) {
      "Layout asset '${asset.destination}' of '$key' names an unknown or repeated host platform: $platforms"
    }
    when {
      platforms.isEmpty() -> asset
      hostPlatform in platforms -> asset.copy(hostPlatforms = emptyList())
      else -> null
    }
  }
}

/**
 * The raw input of the checkout directory [source] of a module, for the layout slot [key]. A directory with exclusions
 * reads the filtered filegroup of its package, so its package needs a module whose `dev` section declares the filegroup.
 */
internal fun moduleDirectoryRawInput(
  id: String,
  key: String,
  mainModule: String,
  source: DevPluginLayoutAssetSource.ModuleDirectory,
  index: DevDistBazelIndex,
  resources: DevDistResourceSources,
): DevDistPluginRawInput {
  val resource = resources.declaredResourceSource(mainModule = mainModule, moduleName = source.moduleName, resourcePath = source.path)
  require(resource.isDirectory) { "Layout source '$key' requires a directory: ${source.moduleName}:${source.path}" }
  val filtered = !source.exclusions.isEmpty()
  if (filtered && index.modulesInPackage(resource.absolutePackage).isEmpty()) {
    throw DevDistUnplannableLayoutException(
      "Layout source '$key' filters '${source.path}' of module '${source.moduleName}', and its package '${resource.absolutePackage}' " +
      "has no module, so no dev section declares the filtered filegroup"
    )
  }
  return DevDistPluginRawInput(
    id = id,
    label = if (filtered) resource.filteredLabel else resource.label,
    kind = "directory",
    fileName = resource.fileName,
    sourceTreePrefix = resource.sourceTreePrefix,
    sourceTreeExclusions = source.exclusions,
  )
}

private data class ConcreteLayoutAsset(
  @JvmField val asset: DevPluginLayoutAsset,
  @JvmField val directory: Boolean,
)

private data class ResolvedLayoutAssetSource(
  @JvmField val references: List<ResolvedLayoutAssetReference>,
)

/** [library] marks a reference to a library container. It stands for every member jar, so it names no one file. */
private data class ResolvedLayoutAssetReference(
  @JvmField val reference: DevPluginReference,
  @JvmField val fileName: String,
  @JvmField val kind: String,
  @JvmField val library: Boolean = false,
)

private class DevPluginLayoutAssetSourceResolver(
  private val key: String,
  private val sources: List<DevPluginLayoutAssetSource>,
  private val index: DevDistBazelIndex,
  private val outputProvider: ModuleOutputProvider,
  private val resources: DevDistResourceSources,
  private val binder: DevDistAssetBinder,
) {
  private val resolved = HashMap<Int, ResolvedLayoutAssetSource>()
  private val rawInputs = LinkedHashMap<String, DevDistPluginRawInput>()
  private val libraries = LinkedHashSet<DevDistPluginLibraryInput>()
  private val fileFacts = LinkedHashMap<String, DevDistPluginFileFacts>()

  fun resolve(sourceIndex: Int): ResolvedLayoutAssetSource {
    return resolved.getOrPut(sourceIndex) {
      when (val source = sources.get(sourceIndex)) {
        is DevPluginLayoutAssetSource.ModuleDirectory -> resolveModuleDirectory(sourceIndex, source)
        is DevPluginLayoutAssetSource.BazelTarget -> resolveBazelTarget(sourceIndex, source)
        is DevPluginLayoutAssetSource.ProjectLibrary -> resolveProjectLibrary(source)
        is DevPluginLayoutAssetSource.ModuleLibrary -> resolveModuleLibrary(source)
        is DevPluginLayoutAssetSource.OptionalLocalDirectory -> resolveOptionalLocalDirectory(source)
        is DevPluginLayoutAssetSource.DebuggerEgg,
        is DevPluginLayoutAssetSource.JupyterFrontend,
        is DevPluginLayoutAssetSource.CidrDependency,
        is DevPluginLayoutAssetSource.RustNativeHelper,
        is DevPluginLayoutAssetSource.GdScriptSdk -> resolveBoundSource(source)
      }
    }
  }

  fun catalogueFacts(): DevDistPluginCatalogueFacts {
    return DevDistPluginCatalogueFacts(
      additionalInputs = rawInputs.values.toList(),
      additionalLibraries = libraries.toList(),
      fileFacts = fileFacts.toMap(),
    )
  }

  private fun resolveModuleDirectory(sourceIndex: Int, source: DevPluginLayoutAssetSource.ModuleDirectory): ResolvedLayoutAssetSource {
    val input = moduleDirectoryRawInput(
      id = "module-resource:$key:$sourceIndex:source",
      key = key,
      mainModule = source.moduleName,
      source = source,
      index = index,
      resources = resources,
    )
    registerRawInput(input)
    return ResolvedLayoutAssetSource(listOf(ResolvedLayoutAssetReference(DevPluginReference(input.id), input.fileName, "directory")))
  }

  /** A closed source: the binder of the half binds it to one raw input, see [DevDistAssetBinder]. */
  private fun resolveBoundSource(source: DevPluginLayoutAssetSource): ResolvedLayoutAssetSource {
    val input = binder.bind(source, index)
    registerRawInput(input)
    return ResolvedLayoutAssetSource(listOf(ResolvedLayoutAssetReference(DevPluginReference(input.id), input.fileName, input.kind)))
  }

  /** The one root filegroup lists the local parts that a preceding build made below `out/`. */
  private fun resolveOptionalLocalDirectory(source: DevPluginLayoutAssetSource.OptionalLocalDirectory): ResolvedLayoutAssetSource {
    val path = source.path
    require(path.startsWith("out/bundle-plugins/") || Regex("out/[^/]+/bundle-plugins/[^/]+(?:/[^/]+)*").matches(path)) {
      "Layout callback '$key' names unsupported optional local directory '$path'"
    }
    require(path.split('/').all { it.isNotEmpty() && it !in setOf(".", "..") }) {
      "Layout callback '$key' names unsafe optional local directory '$path'"
    }
    val id = "optional-local-directory:$path"
    registerRawInput(DevDistPluginRawInput(
      id = id,
      label = "//:dev_dist_optional_local_directories",
      kind = "directory",
      fileName = path.substringAfterLast('/'),
      sourceTreePrefix = path,
      optionalSourceTree = true,
    ))
    return ResolvedLayoutAssetSource(listOf(ResolvedLayoutAssetReference(DevPluginReference(id), path.substringAfterLast('/'), "directory")))
  }

  private fun resolveBazelTarget(sourceIndex: Int, source: DevPluginLayoutAssetSource.BazelTarget): ResolvedLayoutAssetSource {
    require(source.kind in setOf("archive", "directory", "file")) {
      "Layout callback '$key' has an unsupported Bazel source kind '${source.kind}'"
    }
    val id = "layout-source:$key:$sourceIndex"
    val fileName = validateFileName(source.fileName)
    val prefix = source.prefix
    if (prefix == null) {
      registerRawInput(DevDistPluginRawInput(id, source.label, source.kind, fileName))
      return ResolvedLayoutAssetSource(listOf(ResolvedLayoutAssetReference(DevPluginReference(id), fileName, source.kind)))
    }
    val input = DevDistPluginRawInput(
      id = id,
      label = source.label,
      kind = "directory",
      fileName = if (source.kind == "directory") source.fileName else fileName,
      sourceTreePrefix = prefix,
    )
    registerRawInput(input)
    val path = if (source.kind == "directory") "" else joinLayoutPath(prefix, fileName)
    return ResolvedLayoutAssetSource(listOf(ResolvedLayoutAssetReference(
      reference = DevPluginReference(id, path),
      fileName = fileName,
      kind = source.kind,
    )))
  }

  private fun resolveProjectLibrary(source: DevPluginLayoutAssetSource.ProjectLibrary): ResolvedLayoutAssetSource {
    val description = requireNotNull(index.targets.projectLibraries.get(source.name)) {
      "Project library '${source.name}' has no Bazel target record"
    }
    val roots = outputProvider.findLibraryRoots(source.name, moduleLibraryModuleName = null)
    return ResolvedLayoutAssetSource(listOf(libraryReference(DevDistPluginLibraryInput(source.name), description, roots, "project library '${source.name}'")))
  }

  private fun resolveModuleLibrary(source: DevPluginLayoutAssetSource.ModuleLibrary): ResolvedLayoutAssetSource {
    val module = requireNotNull(outputProvider.findModule(source.module)) {
      "Layout callback '$key' found no module '${source.module}'"
    }
    val library = requireNotNull(module.libraryCollection.libraries.singleOrNull { it.name == source.name }) {
      "Layout callback '$key' found no module library '${source.name}' in '${source.module}'"
    }
    val description = requireNotNull(index.targets.modules.get(source.module)?.moduleLibraries?.get(source.name)) {
      "Module library '${source.module}:${source.name}' has no Bazel target record"
    }
    return ResolvedLayoutAssetSource(listOf(libraryReference(
      input = DevDistPluginLibraryInput(source.name, source.module),
      description = description,
      roots = getLibraryRoots(library, outputProvider),
      owner = "module library '${source.module}:${source.name}'",
    )))
  }

  /**
   * One reference to the library container. The operation input names the container, and the packer expands it to
   * the member jars the catalogue lists. The recorded jar targets serve one check here: the index and the JPS roots
   * agree on the member count.
   */
  private fun libraryReference(
    input: DevDistPluginLibraryInput,
    description: BazelTargetsInfo.LibraryDescription,
    roots: List<Path>,
    owner: String,
  ): ResolvedLayoutAssetReference {
    require(description.jarTargets.size == roots.size && roots.isNotEmpty()) {
      "The ordered Bazel files and JPS roots differ for $owner: labels=${description.jarTargets.size}, roots=${roots.size}"
    }
    val label = index.libraryLabel(input.libraryName, input.moduleName, dependentIsCommunity = false)
    require(label == description.target) { "The container label of $owner does not match the owner index: $label" }
    libraries.add(input)
    return ResolvedLayoutAssetReference(DevPluginReference(label), requireNotNull(roots.first().fileName).toString(), "archive", library = true)
  }

  private fun validateFileName(fileName: String): String {
    require(fileName.isNotBlank() && fileName !in setOf(".", "..") && fileName.none { it in "/\\:\u0000" }) {
      "Layout callback '$key' has an invalid file name '$fileName'"
    }
    return fileName
  }

  private fun registerRawInput(input: DevDistPluginRawInput) {
    val previous = rawInputs.putIfAbsent(input.id, input)
    require(previous == null || previous == input) { "Layout callback '$key' has conflicting input '${input.id}'" }
    val facts = DevDistPluginFileFacts(input.kind, input.fileName)
    val previousFacts = fileFacts.putIfAbsent(input.id, facts)
    require(previousFacts == null || previousFacts == facts) { "Layout callback '$key' has conflicting file facts for '${input.id}'" }
  }
}

private fun isDirectDeclaredFile(
  requestedFormat: String,
  assets: List<ConcreteLayoutAsset>,
  inputs: List<DevPluginReference>,
): Boolean {
  return requestedFormat == "tree" && assets.size == 1 && inputs.size == 1 &&
         assets.single().asset.transform == null && !assets.single().directory
}

private fun commonLayoutRoot(assets: List<ConcreteLayoutAsset>): String {
  val candidates = assets.map { concrete ->
    if (concrete.directory) concrete.asset.destination else concrete.asset.destination.substringBeforeLast('/', "")
  }
  require(candidates.isNotEmpty()) { "A prepared layout tree requires a destination" }
  if (candidates.all(String::isEmpty)) {
    return ""
  }
  require(candidates.none(String::isEmpty)) {
    "Prepared layout assets cannot mix the plugin root with another destination: $candidates"
  }
  var common = candidates.first().split('/')
  for (candidate in candidates.drop(1)) {
    val parts = candidate.split('/')
    common = common.take(common.zip(parts).takeWhile { (first, second) -> first == second }.size)
  }
  require(common.isNotEmpty()) { "Prepared layout assets have no common destination root: $candidates" }
  return common.joinToString("/")
}

private fun relativeLayoutPath(root: String, path: String): String {
  return when {
    path == root -> ""
    path.startsWith("$root/") -> path.removePrefix("$root/")
    else -> error("Layout path '$path' is outside its prepared root '$root'")
  }
}

private fun joinLayoutPath(first: String, second: String): String {
  return when {
    first.isEmpty() -> second
    second.isEmpty() -> first
    else -> "$first/$second"
  }
}
