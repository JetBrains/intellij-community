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
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetTransform
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.DevPluginReference
import org.jetbrains.intellij.build.dev.devPluginPreparationOperationSignature
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.impl.LibraryResourceGenerator
import org.jetbrains.intellij.build.impl.ModuleResourceTree
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
data class GeneratedDevPluginBindings(
  @JvmField val facts: PluginSymbolicPreparationFacts = PluginSymbolicPreparationFacts(),
  @JvmField val catalogueFacts: DevDistPluginCatalogueFacts = DevDistPluginCatalogueFacts(),
  @JvmField val operations: List<DevPluginPreparationOperation> = emptyList(),
)

/**
 * Derives the asset bindings that the Product DSL states as data. [binder] binds the closed asset sources of the half.
 * [resources] resolves the `withResource*` declarations of the run.
 */
@ApiStatus.Internal
fun generateDevPluginAssetBindings(
  request: DevDistPluginRequest,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  binder: DevDistAssetBinder,
  resources: DevDistResourceSources = DevDistResourceSources(index, outputProvider),
): GeneratedDevPluginBindings {
  val layout = request.layout
  val effects = LinkedHashMap<String, PluginSymbolicPreparedEffect>()
  val declaredAssets = LinkedHashMap<String, List<PluginPackingAsset>>()
  val preparedSourceManifests = LinkedHashMap<String, PluginSymbolicPreparedSourceManifest>()
  val fileFacts = LinkedHashMap<String, DevDistPluginFileFacts>()
  val additionalInputs = ArrayList<DevDistPluginRawInput>()
  val operations = ArrayList<DevPluginPreparationOperation>()
  val directResourceTrees = ArrayList<DirectResourceTree>()
  var callbackFacts = PluginSymbolicPreparationFacts()
  var callbackCatalogueFacts = DevDistPluginCatalogueFacts()
  val hostPlatform = request.variant.distribution?.let(::devDistHostPlatform)

  fun addCallback(key: String, callback: Any, format: String) {
    val owner = callback as? DevPluginLayoutAssetOwner ?: throw DevDistUnplannableLayoutException(
      "Plugin '${layout.mainModule}' has an undeclared layout callback '$key': ${callback.javaClass.name}"
    )
    val bindings = generateDevPluginLayoutAssetBindings(key, owner, format, index, outputProvider, binder, resources, hostPlatform)
    callbackFacts = mergeGeneratedPreparationFacts(callbackFacts, bindings.facts)
    callbackCatalogueFacts = mergeGeneratedCatalogueFacts(callbackCatalogueFacts, bindings.catalogueFacts)
    operations.addAll(bindings.operations)
  }

  /**
   * A platform slot is keyed by its index among the callbacks that serve the variant's distribution.
   * The key `platform-custom-asset:N` must match the key that `PluginSymbolicLayoutProjection.addResources` requests.
   * A platform custom asset is a tree: production copies its files below the plugin directory, never into a jar.
   */
  fun addSelectedPlatformCallbacks() {
    val distribution = request.variant.distribution ?: return
    for ((generatorIndex, generator) in layout.platformResourceGenerators.get(distribution).orEmpty().withIndex()) {
      addCallback("platform-resource-generator:$generatorIndex", generator, "tree")
    }
    for ((assetIndex, asset) in layout.customAssets.filter { it.platformSpecific == distribution }.withIndex()) {
      val key = "platform-custom-asset:$assetIndex"
      val owner = asset as? DevPluginLayoutAssetOwner ?: throw DevDistUnplannableLayoutException(
        "Plugin '${layout.mainModule}' has an undeclared layout callback '$key': ${asset.javaClass.name}"
      )
      addCallback(key, owner, "tree")
    }
  }

  fun result(): GeneratedDevPluginBindings {
    val directFacts = PluginSymbolicPreparationFacts(
      effects = effects.toMap(),
      preparedSourceManifests = preparedSourceManifests.toMap(),
      declaredAssets = declaredAssets.toMap(),
    )
    val directCatalogueFacts = DevDistPluginCatalogueFacts(additionalInputs = additionalInputs.toList(), fileFacts = fileFacts.toMap())
    return GeneratedDevPluginBindings(
      facts = mergeGeneratedPreparationFacts(directFacts, callbackFacts),
      catalogueFacts = mergeGeneratedCatalogueFacts(directCatalogueFacts, callbackCatalogueFacts),
      operations = operations.toList(),
    )
  }

  for ((assetIndex, asset) in layout.customAssets.withIndex()) {
    if (asset.platformSpecific != null) continue
    val owner = asset as? DevPluginLayoutAssetOwner ?: error(
      "Plugin '${layout.mainModule}' has an undeclared layout callback 'custom-asset:$assetIndex': ${asset.javaClass.name}",
    )
    addCallback("custom-asset:$assetIndex", owner, customAssetPreparationFormat(owner))
  }

  if (request.variant.skipCustomResourceGenerators) {
    addSelectedPlatformCallbacks()
    return result()
  }

  for ((resourceIndex, resource) in layout.resourcePaths.withIndex()) {
    val source = resources.declaredResourceSource(
      mainModule = layout.mainModule,
      moduleName = resource.moduleName,
      resourcePath = resource.resourcePath,
    )
    val inputId = "module-resource:$resourceIndex:source"
    additionalInputs.add(DevDistPluginRawInput(
      id = inputId,
      label = source.label,
      kind = if (source.isDirectory) "directory" else "file",
      fileName = source.fileName,
      sourceTreePrefix = source.sourceTreePrefix,
    ))
    val key = "resource:$resourceIndex"
    if (!resource.packToZip) {
      if (source.isDirectory) {
        directResourceTrees.add(DirectResourceTree(key, resource.relativeOutputPath, inputId))
        continue
      }
      declaredAssets.put(key, listOf(PluginPackingAsset(
        destination = joinResourceDestination(resource.relativeOutputPath, source.fileName),
        inputs = listOf(inputId),
        mode = 493,
        kind = "file",
        classPath = false,
      )))
      continue
    }
    require(source.isDirectory) {
      "Plugin '${layout.mainModule}' has an unsupported archived file resource: ${resource.moduleName}:${resource.resourcePath}"
    }
    // The packer copies the directory as the entries of one jar. The jar has no manifest, as the archived resource had none.
    val id = "layout-assets:resource:$resourceIndex"
    val output = "$id:output"
    val operation = DevPluginPreparationOperation(
      id = id,
      kind = "layout-assets",
      inputs = listOf(DevPluginReference(inputId)),
      output = output,
      manifest = "keep",
      layoutAssets = DevPluginLayoutAssetPreparation(
        format = "entries",
        assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0))),
      ),
    )
    effects.put(key, PluginSymbolicPreparedEffect(
      preparation = PluginPackingPreparation(
        id = id,
        inputs = listOf(inputId),
        outputs = listOf(output),
        modelSignature = devPluginPreparationOperationSignature(operation, version = 2),
      ),
      assets = listOf(PluginPackingAsset(
        destination = resource.relativeOutputPath,
        inputs = listOf(output),
        recipe = CanonicalJarRecipe(
          sources = listOf(JarSourceRecipe(output, "prepared", "prepared")),
          writer = JarWriterRecipe(manifest = "drop"),
        ),
        classPath = false,
      )),
    ))
    preparedSourceManifests.put(output, PluginSymbolicPreparedSourceManifest(1, listOf("keep")))
    operations.add(operation)
  }
  for ((generatorIndex, candidate) in layout.resourceGenerators.withIndex()) {
    val key = "resource-generator:$generatorIndex"
    if (candidate is ModuleResourceTree) {
      // A declared tree is a plain copy of its directory, or of the filtered filegroup when it states exclusions.
      // It joins the `withResource*` directories, so a second tree over its destination is an overlay as theirs is.
      val spec = candidate.devPluginLayoutAssetSpec
      val source = spec.sources.single() as DevPluginLayoutAssetSource.ModuleDirectory
      val inputId = "module-resource:$key:0:source"
      additionalInputs.add(moduleDirectoryRawInput(id = inputId, key = key, mainModule = layout.mainModule, source = source, index = index, resources = resources))
      directResourceTrees.add(DirectResourceTree(key, spec.assets.single().destination, inputId))
      continue
    }
    if (candidate is DevPluginLayoutAssetOwner) {
      addCallback(key, candidate, "tree")
      continue
    }
    // The builder accepts a declared generator or a library resource only, so any other entry is a builder defect.
    val generator = candidate as? LibraryResourceGenerator ?: error(
      "Plugin '${layout.mainModule}' has an undeclared resource generator at index $generatorIndex: ${candidate.javaClass.name}",
    )
    val library = requireNotNull(index.targets.projectLibraries.get(generator.libraryName)) {
      "Project library '${generator.libraryName}' has no Bazel target record"
    }
    check(library.jarTargets.size == 1) {
      "Project library '${generator.libraryName}' requires one recorded jar target, but has ${library.jarTargets.size}"
    }
    check(index.libraryLabel(generator.libraryName, owner = null, dependentIsCommunity = false) == library.target) {
      "Project library '${generator.libraryName}' has a stale Bazel container label"
    }
    val roots = outputProvider.findLibraryRoots(generator.libraryName, moduleLibraryModuleName = null)
    check(roots.size == 1) {
      "Project library '${generator.libraryName}' requires one root, but has ${roots.size}"
    }
    val fileName = requireNotNull(roots.single().fileName).toString()
    check(fileName.isNotEmpty()) { "Project library '${generator.libraryName}' has no root filename" }
    val inputFileFacts = DevDistPluginFileFacts(kind = "archive", fileName = fileName)
    val previousFileFacts = fileFacts.putIfAbsent(library.jarTargets.single(), inputFileFacts)
    check(previousFileFacts == null || previousFileFacts == inputFileFacts) {
      "Raw library input '${library.jarTargets.single()}' has conflicting file facts"
    }

    // The packer extracts the library archive into the target tree. The operation names the container, and the
    // catalogue rule expands it to the one member jar.
    val inputId = library.target
    val id = "layout-assets:resource-generator:$generatorIndex"
    val output = "$id:output"
    val operation = DevPluginPreparationOperation(
      id = id,
      kind = "layout-assets",
      inputs = listOf(DevPluginReference(inputId)),
      output = output,
      manifest = "keep",
      layoutAssets = DevPluginLayoutAssetPreparation(
        format = "tree",
        root = generator.targetPath,
        assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0), transform = DevPluginLayoutAssetTransform.archiveTree())),
      ),
    )
    val preparation = PluginPackingPreparation(
      id = id,
      inputs = listOf(inputId),
      outputs = listOf(output),
      modelSignature = devPluginPreparationOperationSignature(operation, version = 2),
    )
    effects.put(
      "resource-generator:$generatorIndex",
      PluginSymbolicPreparedEffect(
        preparation = preparation,
        assets = listOf(PluginPackingAsset(generator.targetPath, listOf(output), kind = "tree", classPath = false)),
      ),
    )
    operations.add(operation)
  }
  bindDirectResourceTrees(
    trees = directResourceTrees,
    effects = effects,
    declaredAssets = declaredAssets,
    operations = operations,
  )
  addSelectedPlatformCallbacks()
  return result()
}

private data class DirectResourceTree(
  @JvmField val key: String,
  @JvmField val destination: String,
  @JvmField val input: String,
)

private fun bindDirectResourceTrees(
  trees: List<DirectResourceTree>,
  effects: MutableMap<String, PluginSymbolicPreparedEffect>,
  declaredAssets: MutableMap<String, List<PluginPackingAsset>>,
  operations: MutableList<DevPluginPreparationOperation>,
) {
  val byDestination = LinkedHashMap<String, ArrayList<DirectResourceTree>>()
  for (tree in trees) {
    byDestination.computeIfAbsent(tree.destination) { ArrayList() }.add(tree)
  }
  for ((destination, group) in byDestination) {
    if (group.size == 1) {
      val tree = group.single()
      declaredAssets.put(tree.key, listOf(PluginPackingAsset(
        destination = destination,
        inputs = listOf(tree.input),
        kind = "tree",
        classPath = false,
      )))
      continue
    }
    val id = "resource-tree-overlay:${group.first().key.substringAfter(':')}"
    val output = "$id:output"
    val operation = DevPluginPreparationOperation(
      id = id,
      kind = "layout-assets",
      inputs = group.map { DevPluginReference(it.input) },
      output = output,
      manifest = "keep",
      layoutAssets = DevPluginLayoutAssetPreparation(
        format = "tree",
        root = destination,
        assets = group.indices.map { DevPluginLayoutAsset(destination = "", sources = listOf(it)) },
      ),
    )
    val preparation = PluginPackingPreparation(
      id = id,
      inputs = group.map(DirectResourceTree::input),
      outputs = listOf(output),
      modelSignature = devPluginPreparationOperationSignature(operation, version = 2),
    )
    for ((index, tree) in group.withIndex()) {
      effects.put(tree.key, PluginSymbolicPreparedEffect(
        preparation = preparation,
        assets = if (index == 0) listOf(PluginPackingAsset(
          destination = destination,
          inputs = listOf(output),
          kind = "tree",
          classPath = false,
        )) else emptyList(),
      ))
    }
    operations.add(operation)
  }
}

/** A custom asset of the main jar: a tree when it extracts an archive, else the entries the jar takes. */
private fun customAssetPreparationFormat(owner: DevPluginLayoutAssetOwner): String {
  return if (owner.devPluginLayoutAssetSpec.assets.any { it.transform?.kind == "archive-tree" }) "tree" else "entries"
}

private fun mergeGeneratedCatalogueFacts(
  first: DevDistPluginCatalogueFacts,
  second: DevDistPluginCatalogueFacts,
): DevDistPluginCatalogueFacts {
  val preparationKeys = LinkedHashMap(first.preparationKeys)
  for ((input, key) in second.preparationKeys) {
    val previous = preparationKeys.putIfAbsent(input, key)
    require(previous == null || previous == key) { "Raw input '$input' has conflicting preparation keys" }
  }
  val additionalInputs = LinkedHashMap<String, DevDistPluginRawInput>()
  for (input in first.additionalInputs + second.additionalInputs) {
    val previous = additionalInputs.putIfAbsent(input.id, input)
    require(previous == null || previous == input) { "Raw input '${input.id}' has conflicting Bazel bindings" }
  }
  val fileFacts = LinkedHashMap(first.fileFacts)
  for ((input, facts) in second.fileFacts) {
    val previous = fileFacts.putIfAbsent(input, facts)
    require(previous == null || previous == facts) { "Raw input '$input' has conflicting file facts" }
  }
  return DevDistPluginCatalogueFacts(
    preparationKeys = preparationKeys,
    additionalInputs = additionalInputs.values.toList(),
    additionalLibraries = (first.additionalLibraries + second.additionalLibraries).distinct(),
    fileFacts = fileFacts,
    testModules = first.testModules + second.testModules,
  )
}

/**
 * A checkout resource of a plugin, resolved in the Bazel package that owns its files.
 *
 * That package is the nearest one above the resource, below the package of the module that declares it. A directory
 * is read through the `dev_dist_resources` filegroup of the package. The tool writes the filegroup into the `dev`
 * section of the package's first module from the plans that name it. A package without a module declares the filegroup
 * by hand. A file is read as its own source-file label, which the `dev` section of its package exports.
 */
@ApiStatus.Internal
data class DeclaredResourceSource(
  @JvmField val module: String,
  /** The `@community//pkg` or `//pkg` prefix of an absolute label of the owning package. */
  @JvmField val absolutePackage: String,
  /** The path of the owning package below the root of its half, with `/` separators, empty for a root package. */
  @JvmField val packagePath: String,
  /** The path below the package directory, with `/` separators. */
  @JvmField val packageRelativePath: String,
  @JvmField val isDirectory: Boolean,
) {
  val fileName: String
    get() = packageRelativePath.substringAfterLast('/')

  /** The label a plan names: the package filegroup for a directory, the source file itself for a file. */
  val label: String
    get() = if (isDirectory) "$absolutePackage:$DEV_DIST_RESOURCES_TARGET" else "$absolutePackage:$packageRelativePath"

  /** The label of the filtered filegroup of a directory. The `dev` section of the package declares it beside the package filegroup. */
  val filteredLabel: String
    get() = "$absolutePackage:${filteredResourcesTarget(packageRelativePath)}"

  /** The repository-relative prefix of a directory tree, `null` for a file. */
  val sourceTreePrefix: String?
    get() = if (isDirectory) joinResourceDestination(packagePath, packageRelativePath) else null
}

/**
 * The `withResource*` declarations of a run, resolved once per module and path.
 *
 * A run binds the layout of a plugin once per product and per platform variant, and each binding resolves the same
 * declarations against the file system. The answer depends on the module and the path alone, so one resolver per run
 * reads each declaration once. A rejection is kept too. It is thrown again with the name of the plugin that asks.
 */
@ApiStatus.Internal
class DevDistResourceSources(
  private val index: DevDistBazelIndex,
  private val outputProvider: ModuleOutputProvider,
) {
  private val resolved = ConcurrentHashMap<String, Any>()

  /**
   * Resolves one `withResource*` declaration.
   *
   * The rule: the path stays inside the Bazel package of the declaring module, and a directory holds no nested
   * package. A `..` segment is not rejected by itself: production resolves the path against the module's first
   * content root, and a module with more than one content root can legitimately need `..` to reach a later one.
   * The nearest package above the resource owns it. When that package is a
   * nested one without a module, the tool writes no `dev` section there, so its `BUILD.bazel` must declare the
   * `dev_dist_resources` filegroup by hand, and a file resource has no owner. A layout that breaks the rule
   * stops the run, and the message names the broken part. The containment check is path-based, not filesystem-based,
   * so a plugin whose resource a download fills gets the same message on every checkout.
   */
  internal fun declaredResourceSource(mainModule: String, moduleName: String, resourcePath: String): DeclaredResourceSource {
    val key = "$moduleName:$resourcePath"
    val result = resolved.get(key) ?: resolveResourceSource(moduleName, resourcePath).also { resolved.putIfAbsent(key, it) }
    return when (result) {
      is DeclaredResourceSource -> result
      is ResourceRejection -> throw DevDistUnplannableLayoutException(
        "Plugin '$mainModule' declares the resource '$resourcePath' of module '$moduleName', and ${result.detail}"
      )
      else -> error("Unexpected resolution $result")
    }
  }

  private fun resolveResourceSource(moduleName: String, resourcePath: String): Any {
    val location = index.location(moduleName)
                   ?: return ResourceRejection("the Bazel target index does not place that module")
    val module = outputProvider.findRequiredModule(moduleName)
    val contentRoot = Path.of(JpsPathUtil.urlToPath(module.contentRootsList.urls.first())).toAbsolutePath().normalize()
    val source = contentRoot.resolve(resourcePath).normalize()
    val packageDir = checkNotNull(index.packageDir(moduleName)).toAbsolutePath().normalize()
    if (source == packageDir || !source.startsWith(packageDir)) {
      return ResourceRejection("the path resolves to '$source', outside the package '$packageDir'")
    }
    val isDirectory = Files.isDirectory(source)
    if (!isDirectory && !Files.isRegularFile(source)) {
      return ResourceRejection("nothing exists at '$source'")
    }
    val tree = ResourceTree.of(packageDir = packageDir, source = source)
    if (tree.nestedPackage != null) {
      return ResourceRejection("'${tree.nestedPackage}' is a Bazel package of its own")
    }
    if (isDirectory && !tree.hasRegularFile) {
      return ResourceRejection("'$source' holds no file")
    }
    val ownerDir = tree.enclosingPackage
    val nestedPath = if (ownerDir == packageDir) "" else packageDir.relativize(ownerDir).invariantSeparatorsPathString
    val absolutePackage = when {
      nestedPath.isEmpty() -> location.absolutePackage
      location.packagePath.isEmpty() -> location.absolutePackage + nestedPath
      else -> "${location.absolutePackage}/$nestedPath"
    }
    if (nestedPath.isNotEmpty() && index.modulesInPackage(absolutePackage).isEmpty()) {
      if (!isDirectory) {
        return ResourceRejection("'$ownerDir' is a Bazel package without a module, so no dev section exports the file")
      }
      if (!declaresTargetByHand(ownerDir, DEV_DIST_RESOURCES_TARGET)) {
        return ResourceRejection(
          "'$ownerDir' is a Bazel package without a module, and its BUILD file declares no '$DEV_DIST_RESOURCES_TARGET' filegroup by hand"
        )
      }
    }
    return DeclaredResourceSource(
      module = moduleName,
      absolutePackage = absolutePackage,
      packagePath = joinResourceDestination(location.packagePath, nestedPath),
      packageRelativePath = ownerDir.relativize(source).invariantSeparatorsPathString,
      isDirectory = isDirectory,
    )
  }
}

/**
 * Whether the `BUILD.bazel` or `BUILD` of [packageDir] declares a target named [name]. A hand-written statement is
 * the only way a package without a module gets one, because the tool writes a `dev` section for a module only.
 */
private fun declaresTargetByHand(packageDir: Path, name: String): Boolean {
  val buildFile = listOf(BUILD_FILE_NAME, "BUILD").map(packageDir::resolve).firstOrNull(Files::isRegularFile) ?: return false
  return Regex("""name\s*=\s*"${Regex.escape(name)}"""").containsMatchIn(Files.readString(buildFile))
}

/** Why a `withResource*` declaration is unplannable. [DevDistUnplannableLayoutException] carries [detail] in its message. */
private class ResourceRejection(@JvmField val detail: String)

/**
 * What one walk over a resource states: the nearest directory at or above the parent of the source that is a Bazel
 * package, the first package strictly below a directory source, and whether a directory source holds a regular file.
 *
 * [enclosingPackage] is [of]'s `packageDir` when no package sits between it and the source. [nestedPackage] is `null`
 * when every file of a directory source belongs to [enclosingPackage]. The walk down stops at the first nested package,
 * so [hasRegularFile] is complete only when [nestedPackage] is `null`.
 */
private class ResourceTree(@JvmField val enclosingPackage: Path, @JvmField val nestedPackage: Path?, @JvmField val hasRegularFile: Boolean) {
  companion object {
    /** One walk from [source] up to [packageDir], then down a directory [source]. */
    fun of(packageDir: Path, source: Path): ResourceTree {
      var enclosingPackage = packageDir
      var directory = requireNotNull(source.parent)
      while (directory != packageDir) {
        if (isBazelPackage(directory)) {
          enclosingPackage = directory
          break
        }
        directory = requireNotNull(directory.parent)
      }
      if (!Files.isDirectory(source)) {
        return ResourceTree(enclosingPackage = enclosingPackage, nestedPackage = null, hasRegularFile = true)
      }
      var nestedPackage: Path? = null
      var hasRegularFile = false
      Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (dir != source && isBazelPackage(dir)) {
            nestedPackage = dir
            return FileVisitResult.TERMINATE
          }
          return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (attrs.isRegularFile) {
            hasRegularFile = true
          }
          return FileVisitResult.CONTINUE
        }
      })
      return ResourceTree(enclosingPackage = enclosingPackage, nestedPackage = nestedPackage, hasRegularFile = hasRegularFile)
    }
  }
}

/**
 * The nearest directory that is a Bazel package strictly between [packageDir] and [source], else the first one below a
 * directory [source]. `null` when every file of the resource belongs to the package of [packageDir].
 */
@ApiStatus.Internal
fun findNestedBazelPackage(packageDir: Path, source: Path): Path? {
  val tree = ResourceTree.of(packageDir = packageDir, source = source)
  return tree.enclosingPackage.takeIf { it != packageDir } ?: tree.nestedPackage
}

private fun isBazelPackage(directory: Path): Boolean {
  return Files.isRegularFile(directory.resolve(BUILD_FILE_NAME)) || Files.isRegularFile(directory.resolve("BUILD"))
}

private fun joinResourceDestination(parent: String, name: String): String {
  return listOf(parent, name).filter(String::isNotEmpty).joinToString("/")
}
