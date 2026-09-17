package org.jetbrains.intellij.build.dev

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.devDistSignature
import org.jetbrains.intellij.build.impl.commonModuleExcludes
import org.jetbrains.intellij.build.impl.createModuleSourcesNamesFilter
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.defaultLibrarySourcesNamesFilter
import org.jetbrains.intellij.build.io.readZipFile
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

@ApiStatus.Internal
@Serializable
data class DevPluginNativeOverride(
  @JvmField val kind: String,
  @JvmField val name: String,
  @JvmField val input: DevPluginReference? = null,
)

internal fun devPluginNativeArchiveOperationSignature(operation: DevPluginPreparationOperation): String {
  val digest = requireNotNull(operation.archiveSha256)
  val signature = devPluginPreparationOperationSignature(operation.copy(archiveSha256 = null), version = 1)
  val configuration = "native-production-archive-v1\n$signature\n$digest"
  return devDistSignature { putString(configuration) }
}

internal fun DevPluginPreparationContext.prepareNativeExtraction(
  output: String,
  input: DevPluginReference,
  entry: String,
): DevPluginPreparedSource {
  require(output in definition.outputs) { "Preparation '${definition.id}' does not declare output '$output'" }
  validatePreparationPath(entry)
  var content: ByteArray? = null
  readZipFile(inputPath(input)) { name, dataSupplier ->
    if (name == entry) {
      require(content == null) { "Duplicate native entry '$entry' in '${input.artifact}'" }
      val buffer = dataSupplier()
      content = ByteArray(buffer.remaining()).also(buffer::get)
    }
    ZipEntryProcessorResult.CONTINUE
  }
  val bytes = requireNotNull(content) { "Missing native entry '$entry' in '${input.artifact}'" }
  val reference = writeFile(output, "native/$entry", bytes)
  return DevPluginPreparedSource(output, listOf(DevPluginExecutionSource(
    kind = "entries", manifest = "keep", entries = listOf(DevPluginPreparedEntry(kind = "file", name = entry, input = reference)),
  )))
}

/** Executes the production native selection rules without adding them to the minimal preparation tool. */
@ApiStatus.Internal
interface DevPluginPresignedNativeRuntime {
  fun compile(operation: DevPluginPreparationOperation, plan: PluginPackingPlan): DevPluginPreparationAction
}

internal fun compileDevPluginPresignedNativeAction(
  operation: DevPluginPreparationOperation,
  plan: PluginPackingPlan,
): DevPluginPreparationAction {
  return loadBuildScriptsRuntime<DevPluginPresignedNativeRuntime>("DevPluginPresignedNativeRecipeRuntime", "Presigned native preparation")
    .compile(operation, plan)
}

@ApiStatus.Internal
fun DevPluginPreparationContext.prepareNativeArchive(
  output: String,
  input: DevPluginReference,
  manifest: String,
  filter: String,
  overrides: List<DevPluginNativeOverride>,
  archiveSha256: String? = null,
): DevPluginPreparedSource {
  require(output in definition.outputs) { "Preparation '${definition.id}' does not declare output '$output'" }
  validateDevPluginNativeOverrides(filter, overrides)
  val archive = if (archiveSha256 == null) input else {
    val content = readBytes(input)
    require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) == archiveSha256) {
      "Native inventory is stale for '${input.artifact}'; the source archive changed"
    }
    writeFile(output, "archive/source.jar", content)
  }
  val archivePath = if (archiveSha256 == null) inputPath(input) else {
    Path.of(artifacts().single { it.id == output }.root).resolve(archive.path)
  }
  val requiredEntries = overrides.mapTo(LinkedHashSet(), DevPluginNativeOverride::name)
  val foundEntries = LinkedHashSet<String>()
  readZipFile(archivePath) { name, _ ->
    if (name in requiredEntries) {
      require(foundEntries.add(name)) { "Duplicate native entry '$name' in '${input.artifact}'" }
    }
    ZipEntryProcessorResult.CONTINUE
  }
  val missing = requiredEntries - foundEntries
  require(missing.isEmpty()) { "Missing native entries $missing in '${input.artifact}'" }
  if (archiveSha256 != null) {
    require(foundEntries.toList() == overrides.map(DevPluginNativeOverride::name)) {
      "Native entries have stale order in '${input.artifact}': expected=$requiredEntries, actual=$foundEntries"
    }
  }
  val replacements = overrides.map { it.input?.let(::readBytes) }
  val preparedOverrides = overrides.mapIndexed { index, override ->
    val content = replacements.get(index)
    val reference = content?.let { writeFile(output, "replacements/$index", it) }
    DevPluginEntryOverride(override.kind, override.name, reference)
  }
  return DevPluginPreparedSource(output, listOf(DevPluginExecutionSource(
    kind = "archive", input = archive, manifest = manifest, filter = filter, overrides = preparedOverrides,
  )))
}

internal fun validateDevPluginNativeOverrides(filter: String, overrides: List<DevPluginNativeOverride>) {
  val includes = when (filter) {
    "module" -> createModuleSourcesNamesFilter(commonModuleExcludes)
    "library" -> ::defaultLibrarySourcesNamesFilter
    "all" -> { _: String -> true }
    else -> throw IllegalArgumentException("Unknown native source filter '$filter'")
  }
  require(overrides.isNotEmpty()) { "A native-archive operation requires overrides" }
  val names = HashSet<String>()
  for (override in overrides) {
    validatePreparationPath(override.name)
    require(names.add(override.name) && override.name != "META-INF/MANIFEST.MF" && override.name != "META-INF/listOfEntities.txt") {
      "Conflicting or unsupported native override '${override.name}'"
    }
    require((override.kind == "replace" && override.input != null) || (override.kind == "reserve" && override.input == null)) {
      "Unknown or invalid native override '${override.kind}'"
    }
    require(includes(override.name)) { "Native override '${override.name}' is excluded by the '$filter' source filter" }
  }
}
