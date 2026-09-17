package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.devDistSignatureOf
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
@Serializable
data class DevPluginResourceInventoryEntry(
  @JvmField val path: String,
  @JvmField val kind: String,
  @JvmField val mode: Int,
  @JvmField val symlinkTarget: String? = null,
)

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginResourceConfiguration(
  @JvmField val version: Int = 1,
  @JvmField val mainModule: String,
  @JvmField val idPrefix: String,
  @JvmField val resourceIndex: Int,
  @JvmField val moduleName: String,
  @JvmField val resourcePath: String,
  @JvmField val relativeOutputPath: String,
  @JvmField val packToZip: Boolean,
  @JvmField val inputKind: String,
  @JvmField val entries: List<DevPluginResourceInventoryEntry>,
  @JvmField val outputs: List<String>,
  @JvmField val timestampMetadata: DevPluginReference? = null,
  /** The input is a declared directory with the exact resource contents. No entry inventory is required. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val normalizedDirectory: Boolean = false,
)

internal fun DevPluginPreparationOperation.declaredOutputs(): List<String> {
  return if (isCallbackPreparation()) callbackOutputs() else resource?.outputs ?: listOf(output)
}

internal fun devPluginResourceSignature(operation: DevPluginPreparationOperation): String {
  val resource = requireNotNull(operation.resource) { "An ordinary-resource operation requires resource fields" }
  require(resource.version == 1) { "Unsupported ordinary resource version ${resource.version}" }
  require(resource.mainModule.isNotBlank() && resource.moduleName.isNotBlank() && resource.idPrefix.isNotBlank()) {
    "A resource requires module names and an ID prefix"
  }
  require(resource.resourceIndex >= 0 && operation.id == "${resource.idPrefix}:${resource.resourceIndex}") { "Invalid resource operation ID or order" }
  val path = resource.resourcePath
  require(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' || it == '\r' || it == '\n' } &&
          path.split('/').none { it.isEmpty() || it == "." } && path.substringAfterLast('/') != ".." &&
          !Path.of(path).isAbsolute && Path.of(path).normalize().invariantSeparatorsPathString == path) { "Unsafe resource lookup path '$path'" }
  if (resource.relativeOutputPath.isNotEmpty()) validatePreparationPath(resource.relativeOutputPath)
  require(resource.inputKind in setOf("file", "directory")) { "A resource requires a file or directory input kind" }
  val entries = resource.entries
  if (resource.normalizedDirectory) {
    require(resource.packToZip && resource.inputKind == "directory" && operation.input.path.isEmpty() &&
            entries.isEmpty() && resource.timestampMetadata == null) {
      "A normalized resource directory requires one directory root and archive output"
    }
  }
  else {
    require(entries.isNotEmpty() && entries.first().path.isEmpty()) { "A resource inventory requires its source root" }
    require(entries.map { it.path } == entries.map { it.path }.distinct().sorted()) { "Resource paths must be unique and sorted" }
    val byPath = entries.associateBy { it.path }
    for (entry in entries) {
      if (entry.path.isNotEmpty()) {
        validatePreparationPath(entry.path)
        require(byPath.get(entry.path.substringBeforeLast('/', ""))?.kind == "directory") { "Missing resource parent directory: ${entry.path}" }
      }
      require(entry.kind in setOf("file", "directory", "symlink") && entry.mode in 1..511) { "Unsupported resource type or mode: $entry" }
      require((entry.kind == "symlink") == (entry.symlinkTarget != null)) { "Invalid resource link facts: $entry" }
    }
    require(entries.first().kind != "symlink" && (!resource.packToZip || entries.none { it.kind == "symlink" })) {
      "Unsupported resource root or archive link"
    }
    val fileArchive = resource.packToZip && entries.first().kind == "file"
    require(fileArchive == (resource.timestampMetadata != null)) { "A file archive requires distinct timestamp metadata, and only a file archive may bind it" }
    require(resource.timestampMetadata?.artifact != operation.input.artifact) { "Timestamp metadata must be a distinct input" }
  }
  val outputs = if (resource.packToZip) listOf("${operation.id}:0")
  else entries.mapIndexedNotNull { index, entry -> if (entry.kind == "file") "${operation.id}:$index" else null }
  require(operation.output.isEmpty() && resource.outputs == outputs) { "Stale resource output IDs or order" }
  val values = buildList {
    addAll(listOf(if (resource.normalizedDirectory) "normalized-resource-directory-v1" else "original-resource-v2",
                  resource.moduleName, path, resource.relativeOutputPath, resource.packToZip.toString(),
                  operation.input.artifact, operation.input.path))
    for (entry in entries) addAll(listOf(entry.path, entry.kind, entry.mode.toString(), entry.symlinkTarget.orEmpty()))
    resource.timestampMetadata?.let { addAll(listOf("original-resource-file-time-v1", it.artifact, it.path)) }
  }
  return devDistSignatureOf(values)
}

internal fun validateDevPluginResourceOperations(operations: List<DevPluginPreparationOperation>, plan: PluginPackingPlan) {
  val resources = operations.filter { it.kind == "ordinary-resource" }.map { requireNotNull(it.resource) }
  if (resources.isEmpty()) return
  val indexes = resources.map { it.resourceIndex }
  val validIndexes = if (resources.all { it.normalizedDirectory }) indexes == indexes.distinct().sorted() else indexes == resources.indices.toList()
  require(resources.all { it.mainModule == plan.plugin && it.idPrefix == resources.first().idPrefix } && validIndexes) {
    "Stale resource layout identity or declaration order"
  }
  for (operation in operations.filter { it.kind == "ordinary-resource" }) {
    val definition = plan.preparations.single { it.id == operation.id }
    require(definition.alwaysRun == definition.outputs.isEmpty()) { "A resource without file outputs must retain its validation action" }
  }
}

internal fun compileDevPluginResources(
  operations: List<DevPluginPreparationOperation>,
  plan: PluginPackingPlan,
  catalogue: DevPluginArtifactCatalogue,
): Map<String, DevPluginPreparationAction> {
  return DevPluginResourceRecipeRuntime().compile(operations, plan, catalogue)
}
