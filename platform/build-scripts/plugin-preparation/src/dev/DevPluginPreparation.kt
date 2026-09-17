@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.util.io.NioFiles
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.classPath.generatePluginClassPathFromOrderedAssets
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.DISTRIBUTION_ASSET_SCOPE
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.PLUGIN_ASSET_SCOPE
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.invariantSeparatorsPathString

private const val LIB_MODULE_PREFIX = "intellij.libraries."

private val preparationJson = Json {
  encodeDefaults = true
  explicitNulls = false
}

@ApiStatus.Internal
data class DevPluginPreparedSource(
  @JvmField val id: String,
  @JvmField val sources: List<DevPluginExecutionSource>,
)

@ApiStatus.Internal
fun interface DevPluginPreparationAction {
  fun prepare(context: DevPluginPreparationContext): List<DevPluginPreparedSource>
}

@ApiStatus.Internal
class DevPluginPreparationContext(
  @JvmField val definition: PluginPackingPreparation,
  private val catalogue: PreparationCatalogue,
  private val outputDirectory: Path,
  private val plugin: String? = null,
  private val layoutSignature: String? = null,
) {
  private val allowedInputs = definition.inputs.flatMapTo(HashSet()) { input ->
    catalogue.library(input)?.files?.map(DevPluginReference::artifact) ?: listOf(input)
  }
  private val outputs = LinkedHashMap<String, DevPluginArtifact>()
  private val omittedTreeRoots = HashSet<String>()
  private val inputTransportRoots = HashMap<String, Path>()

  fun readBytes(reference: DevPluginReference): ByteArray = Files.readAllBytes(inputPath(reference))

  fun inputPath(reference: DevPluginReference): Path {
    require(reference.artifact in allowedInputs) { "Preparation '${definition.id}' does not declare '${reference.artifact}'" }
    val artifact = catalogue.requireReference(reference)
    val root = Path.of(artifact.root)
    val result = if (reference.path.isEmpty()) root else root.resolve(reference.path)
    if (artifact.kind == "directory") {
      val realRoot = root.toRealPath()
      val realResult = result.toRealPath()
      if (!realResult.startsWith(realRoot)) {
        return resolveTransportInput(artifact.id, reference.path, result)
      }
    }
    return result
  }

  private fun resolveTransportInput(artifactId: String, relativePath: String, input: Path): Path {
    require(relativePath.isNotEmpty() && Files.isSymbolicLink(input)) { "Preparation input escapes '$artifactId': $relativePath" }
    val (source, root) = resolveDevPluginTransportFile(
      target = Files.readSymbolicLink(input),
      relativePath = relativePath,
      previousRoot = inputTransportRoots.get(artifactId),
      description = "Preparation input '$artifactId'",
    )
    inputTransportRoots.putIfAbsent(artifactId, root)
    return source
  }

  fun writeFile(
    output: String,
    relativePath: String,
    content: ByteArray,
    executable: Boolean = false,
  ): DevPluginReference {
    val outputIndex = definition.outputs.indexOf(output)
    require(outputIndex >= 0) { "Preparation '${definition.id}' does not declare output '$output'" }
    validatePreparationPath(relativePath)
    val root = outputDirectory.resolve(outputIndex.toString())
    val file = root.resolve(relativePath)
    Files.createDirectories(file.parent)
    Files.write(file, content, CREATE_NEW)
    if (executable) {
      if (file.fileSystem.supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
      }
      else {
        NioFiles.setExecutable(file)
      }
    }
    outputs.putIfAbsent(output, DevPluginArtifact(id = output, kind = "directory", root = root.invariantSeparatorsPathString))
    return DevPluginReference(artifact = output, path = relativePath)
  }

  fun referenceForPath(file: Path): DevPluginReference = catalogue.referenceForPath(file, allowedInputs)

  fun omitTreeRoot(output: String) {
    require(output in definition.outputs && omittedTreeRoots.add(output)) { "Invalid or repeated omitted tree root '$output'" }
  }

  /** Creates one owned directory. The callback supplies its entries without declaring their names to the planner. */
  fun prepareTree(output: String, prepare: (Path) -> Unit): DevPluginPreparedSource {
    val plugin = requireNotNull(plugin) { "Tree preparation requires plugin ownership" }
    val layoutSignature = requireNotNull(layoutSignature) { "Tree preparation requires a layout signature" }
    val outputIndex = definition.outputs.indexOf(output)
    require(outputIndex >= 0 && !outputs.containsKey(output)) { "Invalid or repeated tree output '$output'" }
    Files.createDirectories(outputDirectory)
    val root = Files.createDirectory(outputDirectory.resolve(outputIndex.toString()))
    prepare(root)
    val (mode, entries) = validatePreparedTree(root)
    val tree = DevPluginOwnedTree(version = 2, artifact = output, plugin = plugin, layoutSignature = layoutSignature, rootMode = mode, entries = entries)
    outputs.put(output, DevPluginArtifact(id = output, kind = "directory", root = root.invariantSeparatorsPathString, tree = tree))
    return DevPluginPreparedSource(output, emptyList())
  }

  internal fun validateSources(sources: List<DevPluginPreparedSource>) {
    require(sources.map(DevPluginPreparedSource::id).toSet() == definition.outputs.toSet() && sources.size == definition.outputs.size) {
      "Preparation '${definition.id}' must produce exactly its declared outputs"
    }
    val allowedReferences = allowedInputs + outputs.keys
    for (source in sources.flatMap(DevPluginPreparedSource::sources)) {
      require(source.prepared.isEmpty()) { "Preparation '${definition.id}' must not set an ownership anchor" }
      require(source.library.isEmpty()) { "Preparation '${definition.id}' must expand libraries in place" }
      for (reference in source.references()) {
        require(reference.artifact in allowedReferences) {
          "Preparation '${definition.id}' returned an undeclared input '${reference.artifact}'"
        }
      }
    }
  }

  internal fun artifacts(): Collection<DevPluginArtifact> = outputs.values

  internal fun completeOutputs() {
    val emptyOutputs = definition.outputs.filterNot(outputs::containsKey)
    for (output in emptyOutputs) {
      val root = outputDirectory.resolve(definition.outputs.indexOf(output).toString())
      Files.createDirectories(root)
      outputs.put(output, DevPluginArtifact(id = output, kind = "directory", root = root.invariantSeparatorsPathString))
    }
    for ((id, artifact) in outputs) {
      if (artifact.tree != null) continue
      val (mode, entries) = validatePreparedTree(Path.of(artifact.root))
      val tree = DevPluginOwnedTree(
        version = 2, artifact = id, plugin = requireNotNull(plugin),
        layoutSignature = requireNotNull(layoutSignature), rootMode = mode, entries = entries,
        omitRoot = id in omittedTreeRoots
      )
      outputs.put(id, artifact.copy(tree = tree))
    }
  }
}

internal fun resolveDevPluginTransportFile(
  target: Path,
  relativePath: String,
  previousRoot: Path?,
  description: String,
): Pair<Path, Path> {
  require(target.isAbsolute) { "$description is not an absolute transport link: $relativePath" }
  var root = target.normalize()
  for (part in relativePath.split('/').asReversed()) {
    require(root.fileName?.toString() == part) { "$description path conflicts with the transport link: $relativePath" }
    root = requireNotNull(root.parent) { "$description path conflicts with the transport link: $relativePath" }
  }
  require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
    "$description transport root is not a directory"
  }
  root = root.toRealPath()
  require(previousRoot == null || Files.isSameFile(previousRoot, root)) { "$description has conflicting transport roots" }

  val source = root.resolve(relativePath)
  var parent = source.parent
  while (parent != null && parent.startsWith(root)) {
    require(Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent)) {
      "$description transport parent is not a directory: $relativePath"
    }
    if (parent == root) break
    parent = parent.parent
  }
  require(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "$description transport input is not a file: $relativePath" }
  return source to root
}

@ApiStatus.Internal
data class DevPluginPreparationResult(
  @JvmField val recipeFile: Path,
  @JvmField val preparedDirectory: Path,
  @JvmField val assetTableFile: Path,
  @JvmField val classPathFile: Path,
  @JvmField val catalogueFile: Path,
  @JvmField val recipe: DevPluginExecutionRecipe,
  @JvmField val catalogue: DevPluginArtifactCatalogue,
)

/**
 * States the producer of every asset in plan order. An asset with a reusable artifact is `independent`. Every other
 * asset is `remainder`. The rows are the one ownership statement the Go packer reads.
 */
@ApiStatus.Internal
fun deriveDevPluginExecutionAssets(plan: PluginPackingPlan): List<DevPluginExecutionAsset> {
  val assets = plan.assets.map { planned ->
    DevPluginExecutionAsset(
      destination = planned.asset.destination,
      producer = if (planned.artifact == null) "remainder" else "independent",
      artifact = planned.artifact?.label ?: "",
      kind = planned.asset.kind,
      classPath = planned.asset.classPath,
      normalizeTreeModes = planned.asset.normalizeTreeModes,
      scope = planned.asset.scope,
    )
  }
  validateAssetDestinations(assets)
  for (scope in assets.map(DevPluginExecutionAsset::scope).distinct()) {
    val scopedAssets = assets.filter { it.scope == scope }
    val resolvedLinks = validateDevBuildLinks(plan.assets.mapNotNull { planned ->
      planned.asset.takeIf { it.scope == scope }?.symlinkTarget?.let { planned.asset.destination to it }
    })
    val destinations = scopedAssets.mapTo(HashSet(), DevPluginExecutionAsset::destination)
    for (planned in plan.assets.filter { it.asset.scope == scope }) {
      val target = planned.asset.symlinkTarget ?: continue
      val resolved = resolvedLinks.getValue(planned.asset.destination)
      require(resolved.isEmpty() || resolved in destinations || destinations.any { it.startsWith("$resolved/") } ||
              scopedAssets.any { it.kind == "tree" && resolved.startsWith("${it.destination}/") }) {
        "Plugin '${plan.plugin}' has an unresolved symbolic link target '${planned.asset.destination}': $target"
      }
    }
  }
  return assets
}

/**
 * Writes prepared files under [outputDirectory].
 * Serializes their roots under [catalogueOutputDirectory] so a later action can use a different execroot.
 * A Go-executed operation in [goExecutedOperations] runs no action here. Its output is compiled into the recipe, and
 * its raw inputs stay remainder inputs.
 */
@ApiStatus.Internal
fun prepareDevPlugin(
  plan: PluginPackingPlan,
  runtimeLayoutSignature: String,
  remainderInputIds: List<String>,
  catalogue: DevPluginArtifactCatalogue,
  cachedDescriptorContent: ByteArray,
  pluginDirectory: Path,
  outputDirectory: Path,
  preparationActions: Map<String, DevPluginPreparationAction> = emptyMap(),
  catalogueOutputDirectory: Path = outputDirectory,
  goExecutedOperations: List<DevPluginPreparationOperation> = emptyList(),
): DevPluginPreparationResult {
  plan.validateLayout(runtimeLayoutSignature)
  validateDevPluginPreparationOutputs(plan, catalogue)
  val goExecuted = HashMap<String, DevPluginPreparationOperation>()
  for (operation in goExecutedOperations) {
    require(isGoExecutedOperation(operation) && operation.declaredOutputs() == listOf(operation.output) && operation.id !in preparationActions) {
      "Preparation '${operation.id}' is not a Go-executed operation without an action"
    }
    require(goExecuted.putIfAbsent(operation.output, operation) == null) { "Duplicate Go-executed output '${operation.output}'" }
  }
  val assets = deriveDevPluginExecutionAssets(plan)
  require(remainderInputIds.size == remainderInputIds.toSet().size) { "Plugin '${plan.plugin}' has stale remainder inputs: duplicate IDs" }
  remainderInputIds.forEach(::validatePreparationArtifactId)
  val independent = assets.filter { it.producer == "independent" }.mapTo(HashSet(), DevPluginExecutionAsset::artifact)
  require(remainderInputIds.none { it in independent }) { "Independent artifacts must not be remainder inputs" }
  require(catalogue.artifacts.none { it.id in independent } && catalogue.libraries.none { it.id in independent }) {
    "Independent artifacts must not be preparation inputs"
  }
  val inputs = PreparationCatalogue(catalogue)
  val requiredRawInputs = plan.requiredInputs.flatMapTo(LinkedHashSet()) { input ->
    inputs.library(input)?.files?.map(DevPluginReference::artifact) ?: listOf(input)
  }
  require(
    catalogue.artifacts.mapTo(HashSet(), DevPluginArtifact::id) == requiredRawInputs &&
    catalogue.libraries.all { it.id in plan.requiredInputs }) {
    "Plugin '${plan.plugin}' has stale preparation inputs: expected=$requiredRawInputs"
  }
  val preparedSources = HashMap<String, DevPluginPreparedSource>()
  val preparedArtifacts = HashSet<String>()
  val goExecutedIds = goExecuted.values.mapTo(HashSet(), DevPluginPreparationOperation::id)
  require(plan.preparations.all { preparationActions.containsKey(it.id) || it.id in goExecutedIds }) {
    "Plugin '${plan.plugin}' has unresolved preparation actions"
  }
  for (input in catalogue.artifacts) {
    validatePreparationInputBoundary(Path.of(input.root), outputDirectory)
  }
  for (input in catalogue.artifacts) {
    validateMissingPreparationBoundary(Path.of(input.root), outputDirectory)
  }
  val outputRoot = outputDirectory.normalize()
  val preparedDirectory = outputDirectory.resolve("prepared")
  if (Files.exists(outputRoot, NOFOLLOW_LINKS)) {
    require(Files.isDirectory(outputRoot, NOFOLLOW_LINKS)) { "Preparation output is not a real directory: $outputDirectory" }
    Files.newDirectoryStream(outputRoot).use { entries ->
      require(entries.all { it.fileName.toString() == "prepared" }) { "Preparation output directory must be empty: $outputDirectory" }
    }
  }
  if (Files.exists(preparedDirectory, NOFOLLOW_LINKS)) {
    validateEmptyPreparedDirectory(preparedDirectory)
  }
  if (plan.assets.any { it.asset.kind == "tree" }) {
    val rawTrees = plan.assets.filter { it.asset.kind == "tree" }.mapTo(HashSet()) { it.asset.inputs.single() }
    for (input in catalogue.artifacts) {
      if (input.id in rawTrees) {
        require(input.kind == "directory") { "Tree input '${input.id}' requires a directory artifact" }
      }
    }
  }
  val classPath = generatePluginClassPathFromOrderedAssets(
    pluginDir = pluginDirectory,
    orderedFiles = plan.assets.mapNotNull { planned ->
      val destination = planned.asset.destination
      if (planned.asset.scope == PLUGIN_ASSET_SCOPE && planned.asset.classPath && planned.asset.kind == "file" && destination.startsWith("lib/") &&
          destination.count { it == '/' } == 1 && destination.endsWith(".jar")) {
        pluginDirectory.resolve(destination)
      }
      else {
        null
      }
    },
    cachedDescriptorContent = cachedDescriptorContent,
  )
  Files.createDirectories(outputDirectory.toAbsolutePath().parent)
  Files.createDirectories(outputDirectory)
  if (Files.exists(preparedDirectory, NOFOLLOW_LINKS)) {
    validateEmptyPreparedDirectory(preparedDirectory)
  }
  else {
    Files.createDirectory(preparedDirectory)
  }
  for ((index, preparation) in plan.preparations.withIndex()) {
    val action = preparationActions.get(preparation.id) ?: continue
    val context = DevPluginPreparationContext(preparation, inputs, preparedDirectory.resolve(index.toString()), plan.plugin, plan.layoutSignature)
    val sources = action.prepare(context)
    context.validateSources(sources)
    context.completeOutputs()
    for (artifact in context.artifacts()) {
      inputs.add(artifact)
      preparedArtifacts.add(artifact.id)
    }
    for (source in sources) {
      val executionSources = source.sources.ifEmpty { listOf(DevPluginExecutionSource(kind = "entries", manifest = "keep")) }
      val anchored = source.copy(sources = executionSources.map { it.copy(prepared = source.id) })
      check(preparedSources.putIfAbsent(source.id, anchored) == null) { "Conflicting prepared source '${source.id}'" }
    }
  }

  val operations = plan.assets.mapNotNull { planned ->
    if (planned.artifact != null) {
      return@mapNotNull null
    }
    val asset = planned.asset
    if (asset.kind == "tree") {
      goExecuted.get(asset.inputs.single())?.let { operation ->
        val layoutAssets = requireNotNull(operation.layoutAssets)
        require(operation.kind == "layout-assets" && layoutAssets.format == "tree" && layoutAssets.root == asset.destination) {
          "Tree '${asset.destination}' requires a layout-assets tree operation at its destination"
        }
        return@mapNotNull DevPluginExecutionOperation(
          kind = "layout-tree", destination = asset.destination, scope = asset.scope, mode = if (asset.normalizeTreeModes) 420 else 0,
          layout = DevPluginExecutionLayoutAssets(inputs = operation.inputs, assets = layoutAssets.assets),
        )
      }
      var input = inputs.artifact(asset.inputs.single())
      require(input.kind == "directory") { "Tree '${asset.destination}' requires a directory artifact" }
      if (input.id in preparedArtifacts && input.tree == null) {
        val (mode, entries) = validatePreparedTree(Path.of(input.root))
        input = input.copy(
          tree = DevPluginOwnedTree(
            version = 2, artifact = input.id, plugin = plan.plugin, layoutSignature = plan.layoutSignature,
            rootMode = mode, entries = entries
          )
        )
        inputs.setPreparedTree(input)
      }
      input.tree?.let { tree ->
        require(tree.version == 2 && tree.artifact == input.id && tree.plugin == plan.plugin && tree.layoutSignature == plan.layoutSignature) {
          "Stale tree metadata ownership for '${input.id}'"
        }
      }
      return@mapNotNull DevPluginExecutionOperation(
        kind = "copy-tree", destination = asset.destination, scope = asset.scope, input = DevPluginReference(input.id),
        mode = if (asset.normalizeTreeModes) 420 else 0,
      )
    }
    if (asset.kind == "directory") {
      return@mapNotNull DevPluginExecutionOperation(kind = "directory", destination = asset.destination, scope = asset.scope, mode = asset.mode)
    }
    if (asset.symlinkTarget != null) {
      return@mapNotNull DevPluginExecutionOperation(
        kind = "symlink", destination = asset.destination, scope = asset.scope, target = asset.symlinkTarget,
      )
    }
    val jarRecipe = asset.recipe
    if (jarRecipe == null) {
      require(asset.inputs.size == 1) { "Completed asset '${asset.destination}' requires one declared file" }
      val input = asset.inputs.single()
      require(input !in goExecuted) { "Completed asset '${asset.destination}' requires a prepared file, not a Go-executed output" }
      val prepared = preparedSources.get(input)
      val reference = if (prepared == null) {
        DevPluginReference(input)
      }
      else {
        val source = prepared.sources.singleOrNull()
        require(source?.kind == "entries" && source.entries.size == 1 && source.entries.single().kind == "file") {
          "Completed asset '${asset.destination}' requires one prepared file"
        }
        requireNotNull(source.entries.single().input) { "Completed asset '${asset.destination}' has no prepared file" }
      }
      DevPluginExecutionOperation(kind = "copy", destination = asset.destination, scope = asset.scope, input = reference, mode = asset.mode)
    }
    else {
      DevPluginExecutionOperation(
        kind = "jar",
        destination = asset.destination,
        scope = asset.scope,
        mode = asset.mode,
        sources = compileSources(jarRecipe, inputs, preparedSources, goExecuted, asset.destination),
        options = DevPluginExecutionJarOptions(
          mergeEntities = jarRecipe.writer.mergeEntities,
          directories = if (jarRecipe.writer.directoryEntries) "all" else "none",
        ),
      )
    }
  }
  val usedInputs = LinkedHashSet<String>()
  for (operation in operations) {
    operation.input?.let { reference ->
      if (operation.kind == "copy-tree") {
        require(reference.path.isEmpty() && inputs.artifact(reference.artifact).kind == "directory") {
          "A tree input must name one directory root"
        }
      }
      else {
        inputs.requireReference(reference)
      }
      usedInputs.add(reference.artifact)
    }
    operation.layout?.let { layout ->
      require(operation.kind == "layout-tree") { "Only a layout-tree operation carries layout assets" }
      for (reference in layout.inputs) {
        inputs.requireReference(reference)
        usedInputs.add(reference.artifact)
      }
    }
    for (source in operation.sources) {
      validateExecutionSource(source)
      if (source.prepared.isNotEmpty()) {
        require(source.prepared in preparedArtifacts && inputs.artifact(source.prepared).kind == "directory") {
          "An ownership anchor must name a prepared directory: '${source.prepared}'"
        }
        usedInputs.add(source.prepared)
      }
      for (reference in source.references()) {
        inputs.requireReference(reference)
        usedInputs.add(reference.artifact)
      }
    }
  }
  require(usedInputs == remainderInputIds.toSet()) {
    "Plugin '${plan.plugin}' has stale remainder inputs: expected=$remainderInputIds, actual=$usedInputs"
  }
  require(usedInputs.none { it in independent }) { "Independent artifacts must not be remainder inputs" }
  val remainderCatalogue = DevPluginArtifactCatalogue(artifacts = remainderInputIds.map { id ->
    val artifact = inputs.artifact(id)
    if (id in preparedArtifacts) {
      val relativeRoot = outputDirectory.relativize(Path.of(artifact.root))
      artifact.copy(root = catalogueOutputDirectory.resolve(relativeRoot).normalize().invariantSeparatorsPathString)
    }
    else {
      artifact
    }
  })
  val recipe = DevPluginExecutionRecipe(
    version = pluginPackingExecutionVersion(plan.assets.map { it.asset }),
    plugin = plan.plugin,
    layoutSignature = plan.layoutSignature,
    assets = assets,
    operations = operations,
  )
  val result = DevPluginPreparationResult(
    recipeFile = outputDirectory.resolve("recipe.json"),
    preparedDirectory = preparedDirectory,
    assetTableFile = outputDirectory.resolve("assets.json"),
    classPathFile = outputDirectory.resolve("plugin-classpath.txt"),
    catalogueFile = outputDirectory.resolve("catalogue.json"),
    recipe = recipe,
    catalogue = remainderCatalogue,
  )
  Files.writeString(result.recipeFile, preparationJson.encodeToString(recipe), CREATE_NEW)
  Files.writeString(result.assetTableFile, preparationJson.encodeToString(assets), CREATE_NEW)
  Files.writeString(result.catalogueFile, preparationJson.encodeToString(remainderCatalogue), CREATE_NEW)
  Files.write(result.classPathFile, classPath, CREATE_NEW)
  return result
}

@ApiStatus.Internal
fun validateDevPluginPreparationOutputs(plan: PluginPackingPlan, catalogue: DevPluginArtifactCatalogue) {
  for (artifact in catalogue.artifacts) {
    val tree = artifact.tree ?: continue
    require(
      artifact.kind == "directory" && tree.artifact == artifact.id && tree.plugin == plan.plugin &&
      tree.layoutSignature == plan.layoutSignature && plan.assets.any { it.asset.kind == "tree" && artifact.id in it.asset.inputs }) {
      "Stale tree metadata ownership for '${artifact.id}'"
    }
  }
  val rawIds = catalogue.artifacts.mapTo(HashSet(), DevPluginArtifact::id)
  for (library in catalogue.libraries) {
    rawIds.add(library.id)
    library.files.mapTo(rawIds, DevPluginReference::artifact)
  }
  for (preparation in plan.preparations) {
    for (output in preparation.outputs) {
      require(output !in rawIds) { "Preparation '${preparation.id}' output '$output' aliases a raw catalogue ID" }
    }
  }
}

/**
 * Compiles the jar sources of one asset. A prepared source of a Go-executed operation becomes the source the Go packer
 * executes. [goExecuted] is keyed by output. A module-filter becomes an `archive` source with the operation's excludes.
 * A layout-assets entries operation becomes a `layout` source. It counts as one meaningful source unless the prepared
 * manifest states a count. No production chain reaches this emit path today. A chain whose every operation is
 * Go-executed declares no preparation target, and the `plain` and `callback` chains hold no Go-executed operation. A
 * future `plain` or `callback` chain with one would use this path. Its test leaf is
 * `TestGoPlanDerivationMatchesKotlinPreparer` in `pluginpack_test`, which compares this compiler with the Go `planfile`
 * package field by field. Remove the two together.
 */
private fun compileSources(
  recipe: CanonicalJarRecipe,
  catalogue: PreparationCatalogue,
  preparedSources: Map<String, DevPluginPreparedSource>,
  goExecuted: Map<String, DevPluginPreparationOperation>,
  destination: String,
): List<DevPluginExecutionSource> {
  require(recipe.writer.outputName.isEmpty() || recipe.writer.outputName == destination.substringAfterLast('/')) {
    "The writer output name does not match '$destination'"
  }
  require(recipe.writer.manifest != "single-meaningful-source" || recipe.sources.none { it.kind == "prepared" && it.preparedManifest == null }) {
    "Prepared sources require an explicit manifest policy"
  }
  val preparedCounts = HashMap<JarSourceRecipe, Long>()
  for (source in recipe.sources) {
    val metadata = source.preparedManifest ?: continue
    val operation = goExecuted.get(source.input)
    if (operation != null) {
      require(metadata.sourceManifestPolicies.ifEmpty { listOf("keep") } == listOf(operation.manifest)) {
        "Prepared source '${source.input}' has stale manifest policies"
      }
      preparedCounts.put(source, metadata.originalMeaningfulSourceCount?.toLong() ?: 1L)
      continue
    }
    val prepared = requireNotNull(preparedSources.get(source.input)) { "Unresolved prepared source '${source.input}'" }
    val policiesMatch = if (metadata.sourceManifestPolicies.isEmpty()) {
      prepared.sources == listOf(DevPluginExecutionSource(kind = "entries", manifest = "keep", prepared = source.input))
    }
    else {
      prepared.sources.map { it.manifest } == metadata.sourceManifestPolicies
    }
    require(policiesMatch) {
      "Prepared source '${source.input}' has stale manifest policies"
    }
    val count = metadata.originalMeaningfulSourceCount?.toLong() ?: run {
      val patches = prepared.sources.singleOrNull()
      require(patches != null && patches.kind == "entries" && patches.entries.all { it.kind == "patch" && it.input?.artifact == source.input }) {
        "Prepared source '${source.input}' must contain only preparation-owned module patches"
      }
      patches.entries.size.toLong()
    }
    preparedCounts.put(source, count)
  }
  val meaningfulSources = recipe.sources.sumOf { source ->
    when {
      source.preparedManifest != null -> preparedCounts.getValue(source)
      "lib-module" in source.options || (source.kind == "module" && source.input.startsWith(LIB_MODULE_PREFIX)) -> 0L
      source.kind == "library" -> requireNotNull(catalogue.library(source.input)) { "Unknown library '${source.input}'" }.files.size.toLong()
      else -> 1L
    }
  }
  require(!recipe.writer.rewriteBootClassPath || destination.substringAfterLast('/').contains("intellij.platform.coverage.agent")) {
    "Coverage manifest rewriting requires the coverage agent destination"
  }
  val defaultManifest = when (recipe.writer.manifest) {
    "single-meaningful-source" -> if (meaningfulSources == 1L) "keep" else "drop"
    "keep", "drop" -> recipe.writer.manifest
    else -> error("Unknown manifest policy '${recipe.writer.manifest}'")
  }

  fun archiveSource(reference: DevPluginReference, filter: String, manifest: String): DevPluginExecutionSource {
    val artifact = catalogue.requireReference(reference)
    val sourceName = (if (reference.path.isEmpty()) artifact.root else reference.path).substringAfterLast('/')
    val effectiveManifest = if (recipe.writer.rewriteBootClassPath && sourceName.startsWith("intellij-coverage-agent")) {
      "coverage-agent"
    }
    else {
      manifest
    }
    return DevPluginExecutionSource(kind = "archive", input = reference, filter = filter, manifest = effectiveManifest)
  }
  return recipe.sources.flatMap { source ->
    require(source.options.size == source.options.toSet().size && source.options.all {
      it == "patch" || it == "lib-module" || it == "manifest=keep" || it == "manifest=drop" ||
      it == "manifest=coverage-agent" || it == "manifest=rewrite-boot-class-path"
    }) { "Source '${source.input}' requires an unsupported preparation option: ${source.options}" }
    val manifestOptions = source.options.filter { it.startsWith("manifest=") }
    require(manifestOptions.size <= 1) { "Source '${source.input}' has conflicting manifest policies" }
    val manifest = manifestOptions.singleOrNull()?.substringAfter('=') ?: defaultManifest
    if (source.kind == "prepared") {
      require(source.options.isEmpty() && source.expansion.isEmpty() && source.entry.isEmpty() && source.filter == "prepared") {
        "Prepared source '${source.input}' must materialize its options"
      }
      goExecuted.get(source.input)?.let { operation -> return@flatMap listOf(goExecutedSource(operation)) }
      val prepared = requireNotNull(preparedSources.get(source.input)) { "Unresolved prepared source '${source.input}'" }
      return@flatMap prepared.sources.map {
        if (source.preparedManifest != null && it.manifest == "single-meaningful-source") it.copy(manifest = defaultManifest) else it
      }
    }
    val filter = when (source.filter) {
      "module", "library", "all" -> source.filter
      "module-v1" -> "module"
      "library-v1" -> "library"
      "none" -> "all"
      else -> error("Custom filter '${source.filter}' requires declared preparation inputs")
    }
    when (source.kind) {
      "zip", "archive", "module" -> {
        require(source.expansion.isEmpty() && source.entry.isEmpty() && "patch" !in source.options) {
          "Archive source '${source.input}' contains entry or expansion options"
        }
        listOf(archiveSource(DevPluginReference(source.input), filter, manifest))
      }
      "library" -> {
        require(source.entry.isEmpty() && "patch" !in source.options) { "Library '${source.input}' contains entry options" }
        val library = requireNotNull(catalogue.library(source.input)) { "Unknown library '${source.input}'" }
        val expansion = library.files.map { reference ->
          if (reference.path.isEmpty()) reference.artifact else "${reference.artifact}/${reference.path}"
        }
        require(source.expansion == expansion) {
          "Library '${source.input}' has stale file order: expected=${source.expansion}, actual=$expansion"
        }
        library.files.map { reference -> archiveSource(reference, filter, manifest) }
      }
      "file" -> {
        require(source.expansion.isEmpty() && filter == "all") { "File source '${source.input}' contains filter or expansion options" }
        validatePreparationPath(source.entry)
        listOf(
          DevPluginExecutionSource(
            kind = "entries",
            manifest = manifest,
            entries = listOf(
              DevPluginPreparedEntry(
                kind = if ("patch" in source.options) "patch" else "file",
                name = source.entry,
                input = DevPluginReference(source.input),
              )
            ),
          )
        )
      }
      else -> error("Source kind '${source.kind}' requires declared preparation inputs")
    }
  }
}

/** The jar source the Go packer executes in place of a Go-executed operation's prepared output. */
private fun goExecutedSource(operation: DevPluginPreparationOperation): DevPluginExecutionSource {
  if (operation.kind == "module-filter") {
    return DevPluginExecutionSource(kind = "archive", input = operation.input, filter = "module", excludes = operation.excludes, manifest = operation.manifest)
  }
  val layoutAssets = requireNotNull(operation.layoutAssets)
  require(operation.kind == "layout-assets" && layoutAssets.format == "entries") {
    "Prepared source '${operation.output}' requires a module-filter or a layout-assets entries operation"
  }
  return DevPluginExecutionSource(
    kind = "layout", manifest = "keep", layout = DevPluginExecutionLayoutAssets(inputs = operation.inputs, assets = layoutAssets.assets),
  )
}

@ApiStatus.Internal
class PreparationCatalogue(catalogue: DevPluginArtifactCatalogue) {
  private val artifacts = LinkedHashMap<String, DevPluginArtifact>()
  private val libraries = HashMap<String, DevPluginLibrary>()
  private val roots = HashSet<Path>()

  init {
    require(catalogue.version == 1) { "Unsupported artifact catalogue version ${catalogue.version}" }
    catalogue.artifacts.forEach(::add)
    for (library in catalogue.libraries) {
      require(
        library.id.isNotBlank() && library.files.isNotEmpty() && library.files.size == library.files.toSet().size &&
        libraries.putIfAbsent(library.id, library) == null && !artifacts.containsKey(library.id)
      ) {
        "Invalid or duplicate library '${library.id}'"
      }
      library.files.forEach(::requireReference)
    }
  }

  fun add(artifact: DevPluginArtifact) {
    validatePreparationArtifactId(artifact.id)
    require(artifact.kind == "file" || artifact.kind == "directory") { "Unknown artifact root kind '${artifact.kind}'" }
    val root = Path.of(artifact.root)
    require(artifact.root.isNotEmpty() && artifact.root != "." && root == root.normalize() && roots.add(root.toAbsolutePath())) {
      "Invalid or duplicate artifact root '${artifact.root}'"
    }
    require(!libraries.containsKey(artifact.id) && artifacts.putIfAbsent(artifact.id, artifact) == null) {
      "Duplicate artifact ID '${artifact.id}'"
    }
  }

  fun artifact(id: String): DevPluginArtifact = requireNotNull(artifacts.get(id)) { "Unresolved input '$id'" }

  fun setPreparedTree(artifact: DevPluginArtifact) {
    require(artifact.copy(tree = null) == this.artifact(artifact.id).copy(tree = null) && artifact.tree != null) { "Invalid prepared tree metadata" }
    artifacts.put(artifact.id, artifact)
  }

  fun library(id: String): DevPluginLibrary? = libraries.get(id)

  fun referenceForPath(file: Path, allowedInputs: Set<String>): DevPluginReference {
    val path = file.toAbsolutePath().normalize()
    val candidates = allowedInputs.mapNotNull { id ->
      val artifact = artifact(id)
      val root = Path.of(artifact.root).toAbsolutePath().normalize()
      when {
        artifact.kind == "file" && path == root -> DevPluginReference(id)
        artifact.kind == "directory" && path != root && path.startsWith(root) ->
          DevPluginReference(id, root.relativize(path).invariantSeparatorsPathString)
        else -> null
      }
    }
    require(candidates.size == 1) { "Patch input '$file' must resolve to exactly one declared artifact" }
    return candidates.single()
  }

  fun requireReference(reference: DevPluginReference): DevPluginArtifact {
    val artifact = artifact(reference.artifact)
    if (artifact.kind == "file") {
      require(reference.path.isEmpty()) { "File input '${artifact.id}' cannot have a relative path" }
    }
    else if (reference.path.isNotEmpty()) {
      validatePreparationPath(reference.path)
    }
    artifact.tree?.let { tree ->
      require(tree.entries.any { it.relativePath == reference.path && it.type == "file" }) {
        "Prepared input '${artifact.id}' must name a metadata file: ${reference.path}"
      }
    }
    return artifact
  }
}

private fun validatePreparationArtifactId(id: String) {
  require(id.isNotBlank() && id.trim() == id && id.none { it == '\u0000' || it == '\r' || it == '\n' }) {
    "Invalid artifact ID '$id'"
  }
}

private fun DevPluginExecutionSource.references(): List<DevPluginReference> {
  return listOfNotNull(input) + entries.mapNotNull(DevPluginPreparedEntry::input) + overrides.mapNotNull(DevPluginEntryOverride::input) +
         layout?.inputs.orEmpty()
}

private fun validateExecutionSource(source: DevPluginExecutionSource) {
  require(source.manifest in setOf("keep", "drop", "coverage-agent", "rewrite-boot-class-path")) {
    "Unknown prepared manifest policy '${source.manifest}'"
  }
  require(source.library.isEmpty()) { "Prepared libraries must be expanded in place" }
  require(source.layout == null || source.kind == "layout") { "Only a layout source carries layout assets" }
  require(source.excludes.isEmpty() || source.kind == "archive" && source.filter == "module") {
    "Only an archive source with the module filter carries excludes"
  }
  when (source.kind) {
    "layout" -> {
      require(source.input == null && source.filter.isEmpty() && source.entries.isEmpty() && source.overrides.isEmpty() &&
              source.manifest == "keep" && source.layout != null) { "Invalid layout source" }
    }
    "archive" -> {
      require(source.input != null && source.entries.isEmpty() && source.filter in setOf("all", "module", "library")) {
        "Invalid prepared archive source"
      }
      source.excludes.forEach { FileSystems.getDefault().getPathMatcher("glob:$it") }
      val names = HashSet<String>()
      for (override in source.overrides) {
        validatePreparationPath(override.name)
        require(names.add(override.name) && override.name != "META-INF/MANIFEST.MF" && override.name != "META-INF/listOfEntities.txt") {
          "Conflicting or unsupported native override '${override.name}'"
        }
        require((override.kind == "replace" && override.input != null) || (override.kind == "reserve" && override.input == null)) {
          "Unknown or invalid native override '${override.kind}'"
        }
      }
    }
    "entries" -> {
      require(source.input == null && source.filter.isEmpty() && source.overrides.isEmpty()) { "Invalid prepared entry source" }
      for (entry in source.entries) {
        validatePreparationPath(entry.name)
        require(
          ((entry.kind == "file" || entry.kind == "patch") && entry.input != null) ||
          (entry.kind == "reserve" && entry.input == null)
        ) {
          "Unknown or invalid prepared entry '${entry.kind}'"
        }
      }
    }
    else -> error("Unknown prepared source kind '${source.kind}'")
  }
}

internal fun validatePreparationInputBoundary(source: Path, output: Path) {
  val sourceRoot = source.toAbsolutePath().normalize()
  val outputRoot = output.toAbsolutePath().normalize()
  require(!sourceRoot.startsWith(outputRoot) && !outputRoot.startsWith(sourceRoot)) { "Preparation output overlaps input: $source" }
  val (sourceAncestor, sourceSuffix) = existingPreparationPath(source)
  val (outputAncestor, outputSuffix) = existingPreparationPath(output)
  if (Files.isSameFile(sourceAncestor, outputAncestor)) {
    require(
      sourceSuffix.toString().isNotEmpty() && outputSuffix.toString().isNotEmpty() &&
      !sourceSuffix.startsWith(outputSuffix) && !outputSuffix.startsWith(sourceSuffix)
    ) {
      "Preparation output overlaps input: $source"
    }
  }
  for ((boundary, suffix, other) in listOf(
    Triple(sourceAncestor, sourceSuffix, outputAncestor),
    Triple(outputAncestor, outputSuffix, sourceAncestor)
  )) {
    if (suffix.toString().isNotEmpty()) continue
    var current: Path? = other
    while (current != null) {
      require(!Files.isSameFile(current, boundary)) { "Preparation output overlaps input: $source" }
      current = current.parent
    }
  }
}

private fun validateEmptyPreparedDirectory(directory: Path) {
  require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Prepared output is not a real directory: $directory" }
  Files.newDirectoryStream(directory).use { entries ->
    require(!entries.iterator().hasNext()) { "Prepared output directory must be empty: $directory" }
  }
}

internal fun validateMissingPreparationBoundary(source: Path, output: Path) {
  val (sourceAncestor, sourceSuffix) = existingPreparationPath(source)
  val (outputAncestor, outputSuffix) = existingPreparationPath(output)
  if (sourceSuffix.toString().isEmpty() || outputSuffix.toString().isEmpty() || !Files.isSameFile(sourceAncestor, outputAncestor)) return
  val probe = Files.createTempDirectory(outputAncestor, ".plugin-preparation-boundary-")
  try {
    val probeSource = Files.createDirectories(probe.resolve(sourceSuffix))
    val probeOutput = Files.createDirectories(probe.resolve(outputSuffix))
    validatePreparationInputBoundary(probeSource, probeOutput)
  }
  finally {
    Files.walk(probe).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
  }
}

private fun existingPreparationPath(path: Path): Pair<Path, Path> {
  var ancestor = path.toAbsolutePath()
  var suffix = Path.of("")
  while (true) {
    try {
      return ancestor.toRealPath() to suffix
    }
    catch (_: NoSuchFileException) {
      suffix = ancestor.fileName.resolve(suffix)
      ancestor = requireNotNull(ancestor.parent) { "Cannot resolve preparation path: $path" }
    }
  }
}

internal fun validatePreparedTree(source: Path): Pair<Int, List<DevPluginTreeEntry>> {
  require(Files.isDirectory(source, NOFOLLOW_LINKS)) { "Tree root is not a directory: $source" }
  val root = source.toRealPath()
  val unix = root.fileSystem.supportedFileAttributeViews().contains("unix")
  val entries = LinkedHashMap<String, BasicFileAttributes>()
  val identities = HashSet<Any>()
  val links = ArrayList<Pair<String, String>>()
  Files.walk(root).use { paths ->
    paths.forEach { file ->
      val attributes = Files.readAttributes(file, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
      require(attributes.isDirectory || attributes.isRegularFile || attributes.isSymbolicLink) { "Unsupported tree entry: $file" }
      if (unix) {
        require((Files.getAttribute(file, "unix:mode", NOFOLLOW_LINKS) as Int) and 0xE00 == 0) { "Unsupported tree mode: $file" }
      }
      if (!attributes.isSymbolicLink) {
        val key = attributes.fileKey()
        if (key == null) {
          require(!unix) { "Tree entry has no file identity: $file" }
        }
        else {
          require(identities.add(key)) { "Aliased tree entry: $file" }
        }
      }
      if (file != root) {
        val name = root.relativize(file).invariantSeparatorsPathString
        validatePreparationPath(name)
        entries.put(name, attributes)
        if (attributes.isSymbolicLink) {
          links.add(name to Files.readSymbolicLink(file).invariantSeparatorsPathString)
        }
      }
    }
  }
  validateDevBuildDirectorySpellings(entries.keys)
  val resolved = validateDevBuildLinks(links)
  val edges = HashMap<String, MutableList<String>>()
  for ((name, attributes) in entries) {
    if (attributes.isDirectory) {
      edges.computeIfAbsent(name.substringBeforeLast('/', "")) { ArrayList() }.add(name)
    }
  }
  for ((name, target) in links) {
    require(target.none { it == '\r' || it == '\n' }) { "Unsafe tree link: $name" }
    val destination = resolved.getValue(name)
    require(destination.isEmpty() || entries.containsKey(destination)) { "Missing tree link target: $name" }
    var current = name.substringBeforeLast('/', "")
    for (component in target.split('/')) {
      require(current.isEmpty() || entries.get(current)?.isDirectory == true) { "Tree link '$name' traverses a non-directory: $current" }
      when (component) {
        "", "." -> continue
        ".." -> {
          require(current.isNotEmpty()) { "Tree link escapes its source: $name" }
          current = current.substringBeforeLast('/', "")
        }
        else -> {
          current = if (current.isEmpty()) component else "$current/$component"
          val entry = requireNotNull(entries.get(current)) { "Missing tree link target: $current" }
          if (entry.isSymbolicLink) current = resolved.getValue(current)
        }
      }
    }
    val realTarget = root.resolve(name).toRealPath()
    require(realTarget.startsWith(root) && root.relativize(realTarget).invariantSeparatorsPathString == destination) {
      "Tree link escapes or aliases its source: $name"
    }
    if (Files.isDirectory(realTarget)) {
      edges.computeIfAbsent(name.substringBeforeLast('/', "")) { ArrayList() }.add(destination)
    }
  }
  val active = HashSet<String>()
  val visited = HashSet<String>()
  fun visit(directory: String) {
    require(active.add(directory)) { "Tree directory cycle at '$directory'" }
    if (visited.add(directory)) edges.get(directory)?.forEach(::visit)
    active.remove(directory)
  }
  visit("")
  val metadata = entries.map { (name, attributes) ->
    val file = root.resolve(name)
    val type = if (attributes.isDirectory) "directory" else if (attributes.isSymbolicLink) "symlink" else "file"
    val mode = if (type == "symlink") 0 else fileMode(file) and 0x1FF
    val target = if (type == "symlink") Files.readSymbolicLink(file).invariantSeparatorsPathString else ""
    val hash = when (type) {
      "directory" -> 0L
      "symlink" -> Hashing.xxh3_64().hashBytesToLong(target.toByteArray(Charsets.UTF_8))
      else -> {
        val stream = Hashing.xxh3_64().hashStream()
        val buffer = ByteArray(256 * 1024)
        Files.newInputStream(file).use { input ->
          while (true) {
            val count = input.readNBytes(buffer, 0, buffer.size)
            if (count == 0) break
            stream.putByteArray(if (count == buffer.size) buffer else buffer.copyOf(count))
          }
        }
        stream.asLong
      }
    }
    DevPluginTreeEntry(name, type, hash, if (type == "file") attributes.size() else 0, mode, type == "file" && mode and 0x49 != 0, target)
  }.sortedBy { it.relativePath }
  return (fileMode(root) and 0x1FF) to metadata
}

private fun validateAssetDestinations(assets: List<DevPluginExecutionAsset>) {
  for (scope in assets.map(DevPluginExecutionAsset::scope).distinct()) {
    validateDevBuildDirectorySpellings(assets.filter { it.scope == scope }.map(DevPluginExecutionAsset::destination).filter(String::isNotEmpty))
  }
  val destinations = HashSet<Pair<String, String>>()
  val directories = assets.filter { it.kind == "directory" }.mapTo(HashSet()) { it.scope to devBuildPathIdentity(it.destination) }
  for (asset in assets) {
    require(asset.scope in setOf(PLUGIN_ASSET_SCOPE, DISTRIBUTION_ASSET_SCOPE)) { "Unknown plugin asset scope '${asset.scope}'" }
    if (asset.destination.isNotEmpty()) validatePreparationPath(asset.destination)
    else require(asset.kind == "tree" && asset.scope == PLUGIN_ASSET_SCOPE) { "Only a declared tree can target the plugin root" }
    require(asset.scope != DISTRIBUTION_ASSET_SCOPE || !asset.classPath) {
      "Distribution asset '${asset.destination}' must not contribute to the plugin classpath"
    }
    require(destinations.add(asset.scope to devBuildPathIdentity(asset.destination))) { "Conflicting destination '${asset.destination}'" }
  }
  for ((scope, destination) in destinations) {
    var parent = destination.substringBeforeLast('/', "")
    while (parent.isNotEmpty()) {
      require(
        scope to parent !in destinations || scope to parent in directories ||
        assets.any { it.scope == scope && it.kind == "tree" && devBuildPathIdentity(it.destination) == parent }) {
        "Conflicting destinations '$parent' and '$destination'"
      }
      parent = parent.substringBeforeLast('/', "")
    }
  }
}

@ApiStatus.Internal
fun validatePreparationPath(path: String) {
  require(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' || it == '\r' || it == '\n' } &&
          path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe preparation path '$path'" }
}
