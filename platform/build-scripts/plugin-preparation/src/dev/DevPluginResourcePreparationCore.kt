@file:Suppress("DestructuringDeclaration", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.devDistSignatureOf
import org.jetbrains.intellij.build.impl.writeResourceArchiveImpl
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString


/**
 * [resourcePath] preserves the logical lookup from the module's first content root, including leading parent segments.
 * [input] independently binds that lookup to a declared source artifact, not a compiled output.
 * Catalogue roots identify provider artifacts and may be transport aliases.
 * Genuine source links must remain beneath a declared directory root, not become provider roots.
 */
@ApiStatus.Internal
data class DevPluginResourceSource(
  @JvmField val moduleName: String,
  @JvmField val resourcePath: String,
  @JvmField val input: DevPluginReference,
  @JvmField val entries: List<DevPluginResourceEntry>,
  @JvmField val timestampMetadata: DevPluginReference? = null,
)

/** Reads only the declared source path. The caller must keep these inputs stable during preparation. */
@ApiStatus.Internal
fun captureDevPluginResourceSource(
  catalogue: DevPluginArtifactCatalogue,
  moduleName: String,
  resourcePath: String,
  input: DevPluginReference,
  timestampMetadata: DevPluginReference? = null,
): DevPluginResourceSource {
  validateResourceLookupPath(resourcePath)
  val artifact = PreparationCatalogue(catalogue).requireReference(input)
  val source = resourceInputPath(artifact, input)
  val result = DevPluginResourceSource(moduleName, resourcePath, input, resourceInventory(source), timestampMetadata)
  validateDevPluginResourceTimestampBinding(result, PreparationCatalogue(catalogue))
  return result
}

@ApiStatus.Internal
data class DevPluginResourceSpec(
  @JvmField val moduleName: String,
  @JvmField val resourcePath: String,
  @JvmField val relativeOutputPath: String,
  @JvmField val packToZip: Boolean,
)

@ApiStatus.Internal
data class DevPluginResourceEffect(
  @JvmField val preparation: PluginPackingPreparation,
  @JvmField val assets: List<PluginPackingAsset>,
)

@ApiStatus.Internal
data class DevPluginResourceGap(@JvmField val key: String, @JvmField val detail: String)

@ApiStatus.Internal
class DevPluginResourcePreparationCore(
  private val mainModule: String,
  resources: List<DevPluginResourceSpec>,
  catalogue: DevPluginArtifactCatalogue,
  sources: List<DevPluginResourceSource>,
  private val idPrefix: String = "original-resource",
) {
  private val resources = resources.toList()
  private val sources = sources.map { it.copy(entries = it.entries.toList()) }
  private val artifacts = PreparationCatalogue(catalogue)
  private val effects = LinkedHashMap<String, DevPluginResourceEffect>()
  @JvmField val requirements: List<DevPluginResourceGap>

  init {
    require(idPrefix.isNotBlank()) { "A resource preparation requires an ID prefix" }
    require(resources.size == sources.size) { "Declare every resource source in layout order" }
    val gaps = ArrayList<DevPluginResourceGap>()
    val directories = HashMap<String, PluginPackingAsset>()
    for ((index, resource) in resources.withIndex()) {
      val source = this.sources[index]
      validateResourceLookupPath(resource.resourcePath)
      if (resource.relativeOutputPath.isNotEmpty()) validatePreparationPath(resource.relativeOutputPath)
      require(source.moduleName == resource.moduleName && source.resourcePath == resource.resourcePath) {
        "Resource source $index does not match the original layout"
      }
      artifacts.requireReference(source.input)
      validateResourceEntries(source.entries)
      validateDevPluginResourceTimestampBinding(source, artifacts)
      require(source.timestampMetadata == null || resource.packToZip) { "Only a file archive can bind timestamp metadata" }
      val root = source.entries.first()
      require(root.kind != "symlink") { "Root resource links require separate production semantics" }
      require(!resource.packToZip || source.entries.none { it.kind == "symlink" }) { "Resource archive links are unsupported" }
      if (resource.packToZip && root.kind == "file" && source.timestampMetadata == null) {
        gaps.add(DevPluginResourceGap(
          "resource:$index",
          "File resource archive '${resource.resourcePath}' requires an explicit original timestamp input in the content-keyed build graph",
        ))
        continue
      }
      val id = "$idPrefix:$index"
      val assets = if (resource.packToZip) {
        val destination = if (root.kind == "file") {
          joinResourcePath(resource.relativeOutputPath, resource.resourcePath.substringAfterLast('/'))
        }
        else resource.relativeOutputPath
        listOf(PluginPackingAsset(destination, listOf("$id:0"), classPath = false))
      }
      else {
        source.entries.mapIndexedNotNull { entryIndex, entry ->
          val name = if (root.kind == "file") resource.resourcePath.substringAfterLast('/') else entry.path
          val destination = joinResourcePath(resource.relativeOutputPath, name)
          if (destination.isEmpty()) return@mapIndexedNotNull null
          PluginPackingAsset(
            destination = destination,
            inputs = if (entry.kind == "file") listOf("$id:$entryIndex") else emptyList(),
            mode = if (entry.kind == "directory") 493 else entry.mode,
            symlinkTarget = entry.symlinkTarget,
            kind = if (entry.kind == "directory") "directory" else "file",
            classPath = false,
          )
        }
      }
      val retainedAssets = assets.filter { asset ->
        if (asset.kind != "directory") true
        else {
          val previous = directories.putIfAbsent(asset.destination, asset)
          require(previous == null || previous == asset) { "Conflicting resource directory metadata: ${asset.destination}" }
          previous == null
        }
      }
      val outputs = retainedAssets.flatMap { it.inputs }
      val inputs = listOfNotNull(source.input.artifact, source.timestampMetadata?.artifact)
      val definition = PluginPackingPreparation(id, inputs, outputs, signature(index), alwaysRun = outputs.isEmpty())
      require(definition.outputs.none { output -> catalogue.artifacts.any { it.id == output } || catalogue.libraries.any { it.id == output } }) {
        "A resource output must not alias a declared input"
      }
      effects.put("resource:$index", DevPluginResourceEffect(preparation = definition, assets = retainedAssets))
    }
    requirements = gaps
    validateResourceAssets(effects.values.flatMap { it.assets })
  }

  fun requireEffects(): Map<String, DevPluginResourceEffect> {
    check(requirements.isEmpty()) { requirements.joinToString { "${it.key}: ${it.detail}" } }
    return effects.toMap()
  }

  fun recipeOperations(): List<DevPluginPreparationOperation> {
    requireEffects()
    return snapshotDevPluginPreparationRecipe(DevPluginPreparationRecipe(2, resources.mapIndexed { index, resource ->
      val source = sources[index]
      val definition = effects.getValue("resource:$index").preparation
      DevPluginPreparationOperation(
        id = definition.id, kind = "ordinary-resource", input = source.input, output = "", manifest = "keep",
        resource = DevPluginResourceConfiguration(
          mainModule = mainModule, idPrefix = idPrefix, resourceIndex = index, moduleName = resource.moduleName,
          resourcePath = resource.resourcePath, relativeOutputPath = resource.relativeOutputPath, packToZip = resource.packToZip,
          inputKind = artifacts.requireReference(source.input).kind,
          entries = source.entries.map { DevPluginResourceInventoryEntry(it.path, it.kind, it.mode, it.symlinkTarget) },
          outputs = definition.outputs, timestampMetadata = source.timestampMetadata,
        ),
      ).also { require(devPluginPreparationOperationSignature(it, 2) == definition.modelSignature) { "The resource recipe signature changed" } }
    })).operations
  }

  /** Binds source facts to a plan. Source trees are checked again when each action runs. */
  fun compileActions(
    plan: PluginPackingPlan,
    catalogue: DevPluginArtifactCatalogue,
  ): Map<String, DevPluginPreparationAction> {
    requireEffects()
    require(plan.plugin == mainModule) { "Resource preparations belong to '$mainModule'" }
    validateDevPluginPreparationOutputs(plan, catalogue)
    validateResourceAssets(plan.assets.map { it.asset })
    val expectedAssets = effects.values.flatMap { it.assets }
    val destinations = expectedAssets.mapTo(HashSet()) { it.scope to it.destination }
    require(plan.assets.map { it.asset }.filter { it.scope to it.destination in destinations } == expectedAssets) {
      "Stale resource asset order or metadata"
    }
    val runtimeCatalogue = PreparationCatalogue(catalogue)
    val actions = LinkedHashMap<String, DevPluginPreparationAction>()
    for ((index, source) in sources.withIndex()) {
      val effect = effects.getValue("resource:$index")
      val definition = effect.preparation
      require(plan.preparations.singleOrNull { it.id == definition.id } == definition) { "Stale resource preparation '${definition.id}'" }
      val artifact = runtimeCatalogue.requireReference(source.input)
      validateDevPluginResourceTimestampBinding(source, runtimeCatalogue)
      source.timestampMetadata?.let { metadata ->
        require(metadata.artifact in plan.requiredInputs) { "Timestamp metadata must be a required raw input" }
      }
      require(artifact.kind == artifacts.requireReference(source.input).kind) { "The resource artifact kind changed" }
      checkInventory(source, resourceInputPath(artifact, source.input))
      actions.put(definition.id, DevPluginPreparationAction { context ->
        require(context.definition == definition) { "Stale resource action '${definition.id}'" }
        val path = resourceInputPath(artifact, source.input)
        require(context.inputPath(source.input).toRealPath() == path) { "The resource catalogue changed" }
        checkInventory(source, path)
        val prepared = if (resources[index].packToZip) {
          withResourceScratch { scratch ->
            val archive = if (source.entries.first().kind == "file") {
              val metadata = requireNotNull(source.timestampMetadata)
              val metadataArtifact = runtimeCatalogue.requireReference(metadata)
              val metadataPath = resourceInputPath(metadataArtifact, metadata)
              require(context.inputPath(metadata).toRealPath() == metadataPath) { "The timestamp catalogue changed" }
              withDevPluginResourceTimestamp(source, path, metadataPath, scratch) { staged ->
                writeResourceArchiveImpl(staged, Files.createDirectory(scratch.resolve("archive")))
              }.also {
                require(resourceInputPath(artifact, source.input) == path &&
                        resourceInputPath(metadataArtifact, metadata) == metadataPath) { "The resource catalogue changed during preparation" }
              }
            }
            else writeResourceArchiveImpl(path, scratch.resolve("resource.zip"))
            require(resourceMode(archive) == effect.assets.single().mode) { "The resource archive mode differs from the declared mode" }
            listOf(completedResource(context, definition.outputs.single(), Files.readAllBytes(archive)))
          }
        }
        else {
          validateResourceLinks(path, source.entries)
          source.entries.mapIndexedNotNull { entryIndex, entry ->
            if (entry.kind != "file") null
            else completedResource(context, "${definition.id}:$entryIndex", Files.readAllBytes(path.resolve(entry.path)))
          }
        }
        checkInventory(source, path)
        prepared
      })
    }
    return actions
  }

  private fun signature(index: Int): String {
    val resource = resources[index]
    val source = sources[index]
    val values = buildList {
      add("original-resource-v2")
      add(resource.moduleName)
      add(resource.resourcePath)
      add(resource.relativeOutputPath)
      add(resource.packToZip.toString())
      add(source.input.artifact)
      add(source.input.path)
      for (entry in source.entries) {
        add(entry.path)
        add(entry.kind)
        add(entry.mode.toString())
        add(entry.symlinkTarget.orEmpty())
      }
      source.timestampMetadata?.let { metadata ->
        add(DEV_PLUGIN_RESOURCE_TIMESTAMP_SCHEMA)
        add(metadata.artifact)
        add(metadata.path)
      }
    }
    return devDistSignatureOf(values)
  }
}


private fun validateResourceLookupPath(path: String) {
  require(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' || it == '\r' || it == '\n' } &&
          path.split('/').none { it.isEmpty() || it == "." } && path.substringAfterLast('/') != ".." &&
          Path.of(path).normalize().invariantSeparatorsPathString == path) { "Unsafe resource lookup path '$path'" }
}


private fun checkInventory(source: DevPluginResourceSource, path: Path) {
  require(resourceInventory(path) == source.entries) { "Stale resource path, type, mode, or link inventory for '${source.resourcePath}'" }
  validateResourceLinks(path, source.entries)
}


private fun joinResourcePath(parent: String, name: String): String = listOf(parent, name).filter { it.isNotEmpty() }.joinToString("/")
