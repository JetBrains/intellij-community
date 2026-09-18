package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/** A semantic source that the dev-plugin generator resolves to declared Bazel inputs. */
@ApiStatus.Internal
sealed interface DevPluginLayoutAssetSource {
  data class ModuleDirectory(@JvmField val moduleName: String, @JvmField val path: String) : DevPluginLayoutAssetSource

  /** A debugger egg prepared in Kotlin from two raw checkout directories. Paths are relative to the project root. */
  data class DebuggerEgg(
    @JvmField val pydevModule: String,
    @JvmField val pydevPath: String,
    @JvmField val metadataModule: String,
    @JvmField val metadataPath: String,
    @JvmField val buildNumber: String,
    @JvmField val fileName: String,
  ) : DevPluginLayoutAssetSource

  /** Declares Jupyter configuration and frontend inputs. Paths are relative to the project root. */
  data class JupyterFrontend(
    @JvmField val configurationModule: String,
    @JvmField val remoteConfigurationPath: String,
    /** A null path disables local configuration. A missing file permits a later override. */
    @JvmField val localConfigurationPath: String?,
    @JvmField val frontendModule: String,
    @JvmField val frontendPath: String,
    /** Files and directories relative to [frontendPath], including the declarations of tool versions. */
    @JvmField val buildInputPaths: List<String>,
    @JvmField val optionalLicensePath: String,
    @JvmField val resourceDirectory: String,
    @JvmField val licenseMetadataFileName: String,
    @JvmField val skipStep: String,
    @JvmField val operation: JupyterFrontendOperation,
  ) : DevPluginLayoutAssetSource

  data class BazelTarget(
    @JvmField val label: String,
    @JvmField val kind: String,
    /** A concrete name or a dependency-property template such as `archive-${version}.zip`. */
    @JvmField val fileName: String,
    @JvmField val prefix: String? = null,
  ) : DevPluginLayoutAssetSource

  data class ProjectLibrary(
    @JvmField val name: String,
  ) : DevPluginLayoutAssetSource

  data class ModuleLibraries(
    @JvmField val modules: List<String>,
    @JvmField val allowedNames: Set<String>,
  ) : DevPluginLayoutAssetSource

  data class DependencyProperty(
    @JvmField val name: String,
    @JvmField val format: String = "plain",
  ) : DevPluginLayoutAssetSource

  data class ExternalLocalizationTree(
    @JvmField val folder: String,
    @JvmField val language: String,
  ) : DevPluginLayoutAssetSource
}

@ApiStatus.Internal
enum class JupyterFrontendOperation {
  RESOURCES_AND_LICENSES,
  LICENSES_ONLY,
}

/**
 * Declares one callback's development layout as data.
 * Source indices refer to [sources] and preserve their declaration order.
 */
@ApiStatus.Internal
data class DevPluginLayoutAssetSpec(
  @JvmField val sources: List<DevPluginLayoutAssetSource> = emptyList(),
  @JvmField val assets: List<DevPluginLayoutAsset> = emptyList(),
  @JvmField val omitted: Boolean = false,
) {
  init {
    require(!omitted || sources.isEmpty() && assets.isEmpty()) {
      "An omitted layout asset slot must not declare sources or assets"
    }
  }

  companion object {
    @JvmField
    val OMITTED: DevPluginLayoutAssetSpec = DevPluginLayoutAssetSpec(omitted = true)
  }
}

/** Exposes the typed development layout while its wrapper retains the production callback. */
@ApiStatus.Internal
interface DevPluginLayoutAssetOwner {
  val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec
}

/**
 * One file or tree contribution to a prepared plugin tree.
 * A null [transform] is a direct copy.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginLayoutAsset(
  @JvmField val destination: String,
  @JvmField val sources: List<Int> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val transform: DevPluginLayoutAssetTransform? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val mode: Int = 0,
)

/** One ordered path mapping. The first contribution to a destination wins. */
@ApiStatus.Internal
@Serializable
data class DevPluginLayoutAssetMapping(
  @JvmField val pattern: String = "**",
  @JvmField val stripComponents: Int = 0,
  @JvmField val destination: String = "",
)

/** The transform for one layout asset. Use the factory functions to create supported transforms. */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginLayoutAssetTransform(
  @JvmField val kind: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val stripComponents: Int = 0,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val text: String = "",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val mappings: List<DevPluginLayoutAssetMapping> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val excludes: List<String> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val directoryExcludes: List<String> = emptyList(),
) {
  companion object {
    fun archiveTree(
      stripComponents: Int = 0,
      mappings: List<DevPluginLayoutAssetMapping> = emptyList(),
    ): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(kind = "archive-tree", stripComponents = stripComponents, mappings = mappings)
    }

    fun gzipXmlArchive(): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(kind = "gzip-xml-archive")
    }

    fun inlineText(text: String = ""): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(kind = "inline-text", text = text)
    }

    fun treeMap(
      mappings: List<DevPluginLayoutAssetMapping>,
      excludes: List<String> = emptyList(),
      directoryExcludes: List<String> = emptyList(),
    ): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(kind = "tree-map", mappings = mappings, excludes = excludes, directoryExcludes = directoryExcludes)
    }
  }
}

/** The concrete operation payload after the generator resolves semantic sources. */
@ApiStatus.Internal
@Serializable
data class DevPluginLayoutAssetPreparation(
  @JvmField val format: String,
  @JvmField val root: String = "",
  @JvmField val assets: List<DevPluginLayoutAsset>,
)

/**
 * Validates one layout-assets payload at generation and at action time. The Go packer ports these rules to `plan.go`.
 * A `gzip-xml-archive` asset is a Kotlin preparation. It requires the `entries` format and shares its operation with
 * `gzip-xml-archive` assets only, so that one executor owns every asset of an operation ([isGoExecutedOperation]).
 */
internal fun validateDevPluginLayoutAssetPreparation(
  preparation: DevPluginLayoutAssetPreparation,
  inputs: List<DevPluginReference>,
) {
  require(preparation.format in setOf("entries", "file", "tree")) { "Unknown layout asset format '${preparation.format}'" }
  require(preparation.assets.isNotEmpty()) { "A layout asset preparation requires assets" }
  if (preparation.format == "file" || preparation.format == "tree" && preparation.root.isNotEmpty()) {
    validatePreparationPath(preparation.root)
  }
  else if (preparation.format == "entries") {
    require(preparation.root.isEmpty()) { "An entry preparation must not declare a tree root" }
  }
  for (asset in preparation.assets) {
    val transform = asset.transform
    if (asset.destination.isEmpty()) {
      require(preparation.format == "tree" || preparation.format == "entries" && transform?.kind in setOf("gzip-xml-archive", "tree-map")) {
        "Only a tree or mapped entry asset can use its output root"
      }
    }
    else {
      validatePreparationPath(asset.destination)
    }
    require(asset.mode == 0 || asset.mode in 1..511) { "Unsupported layout asset mode ${asset.mode}" }
    require(asset.sources.all { it in inputs.indices }) { "A layout asset has an invalid source index: ${asset.sources}" }
    if (transform == null) {
      require(asset.sources.size == 1) { "A direct layout asset requires one source" }
      continue
    }
    require(transform.kind in setOf("archive-tree", "gzip-xml-archive", "inline-text", "tree-map")) {
      "Unknown layout asset transform '${transform.kind}'"
    }
    require(preparation.format != "file" || transform.kind == "inline-text") { "A file layout asset allows a plain copy or inline text only" }
    require(transform.stripComponents >= 0) { "A layout asset strip count must not be negative" }
    require(transform.kind == "tree-map" || transform.excludes.isEmpty() && transform.directoryExcludes.isEmpty()) {
      "Only a tree-map transform accepts exclusions"
    }
    for (pattern in transform.excludes + transform.directoryExcludes) {
      require(pattern.isNotEmpty()) { "A layout asset exclusion requires a pattern" }
      FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
    for (mapping in transform.mappings) {
      require(mapping.pattern.isNotEmpty() && mapping.stripComponents >= 0) { "A layout asset mapping requires a pattern and a valid strip count" }
      if (mapping.destination.isNotEmpty()) validatePreparationPath(mapping.destination)
      FileSystems.getDefault().getPathMatcher("glob:${mapping.pattern}")
    }
    when (transform.kind) {
      "archive-tree" -> require(preparation.format == "tree" && asset.sources.size == 1 && transform.text.isEmpty()) {
        "An archive-tree transform requires one archive and a tree output"
      }
      "gzip-xml-archive" -> require(preparation.format == "entries" && asset.sources.isNotEmpty() && transform.stripComponents == 0 &&
                                              transform.text.isEmpty() && transform.mappings.isEmpty()) {
        "A gzip-xml-archive transform requires ordered archive inputs and an entries output"
      }
      "inline-text" -> require(asset.sources.isEmpty() && transform.stripComponents == 0 && transform.mappings.isEmpty() &&
                                       transform.text.none { it == '\r' || it == '\n' }) {
        "An inline-text transform requires text without a newline and no inputs"
      }
      "tree-map" -> require(asset.sources.isNotEmpty() && transform.stripComponents == 0 && transform.text.isEmpty() &&
                                    transform.mappings.isNotEmpty()) {
        "A tree-map transform requires ordered tree inputs and mappings"
      }
    }
  }
  val gzipAssets = preparation.assets.count { it.transform?.kind == "gzip-xml-archive" }
  require(gzipAssets == 0 || gzipAssets == preparation.assets.size) {
    "A gzip-xml-archive asset shares its operation with gzip-xml-archive assets only"
  }
  require(preparation.format != "file" || preparation.assets.size == 1) { "A file layout asset preparation requires one asset" }
}

/**
 * Compiles the Kotlin action of a layout-assets operation that stays a Kotlin preparation. That is a
 * `gzip-xml-archive` entries operation, or a `file` operation with one plain file copy or one inline text.
 * [compileDevPluginPreparationActions] skips a Go-executed operation ([isGoExecutedOperation]).
 */
internal fun compileDevPluginLayoutAssetAction(operation: DevPluginPreparationOperation): DevPluginPreparationAction {
  val preparation = requireNotNull(operation.layoutAssets)
  validateDevPluginLayoutAssetPreparation(preparation, operation.inputs)
  require(preparation.format == "entries" || preparation.format == "file") {
    "Layout asset preparation '${operation.id}' with the format '${preparation.format}' is executed by the Go packer"
  }
  return DevPluginPreparationAction { context -> listOf(prepareLayoutAssetEntries(operation, preparation, context)) }
}

private fun prepareLayoutAssetEntries(
  operation: DevPluginPreparationOperation,
  preparation: DevPluginLayoutAssetPreparation,
  context: DevPluginPreparationContext,
): DevPluginPreparedSource {
  val entries = ArrayList<DevPluginPreparedEntry>()
  val destinations = HashSet<String>()
  val writer = LayoutAssetEntryWriter { path, content, mode ->
    validatePreparationPath(path)
    if (!destinations.add(path)) return@LayoutAssetEntryWriter
    val reference = context.writeFile(
      output = operation.output,
      relativePath = "entries/${entries.size}",
      content = content,
      executable = mode and 0x49 != 0,
    )
    entries.add(DevPluginPreparedEntry(kind = "file", name = path, input = reference))
  }
  for (asset in preparation.assets) {
    val references = asset.sources.map(operation.inputs::get)
    val transform = asset.transform
    when (transform?.kind) {
      null -> copyLayoutAssetFile(context.inputPath(references.single()), asset.destination, asset.mode, writer)
      "gzip-xml-archive" -> gzipLayoutAssetXmlArchives(references.map(context::inputPath), asset.destination, writer)
      "inline-text" -> writer.file(asset.destination, transform.text.toByteArray(Charsets.UTF_8), asset.mode.takeIf { it != 0 } ?: 420)
      else -> error("Layout asset transform '${transform.kind}' is executed by the Go packer")
    }
  }
  return DevPluginPreparedSource(operation.output, listOf(DevPluginExecutionSource(kind = "entries", manifest = "keep", entries = entries)))
}

/** Writes one file entry of a jar layout asset. The first contribution to a destination wins. */
private fun interface LayoutAssetEntryWriter {
  fun file(path: String, content: ByteArray, mode: Int)
}

private fun copyLayoutAssetFile(source: Path, destination: String, mode: Int, writer: LayoutAssetEntryWriter) {
  require(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "A jar layout asset requires a regular file: $source" }
  writer.file(destination, Files.readAllBytes(source), effectiveLayoutAssetMode(source, mode))
}

/** Reads the XML entries of zip or jar archives in central-directory order and writes each one as `<path>.gzip`. */
private fun gzipLayoutAssetXmlArchives(archives: List<Path>, destination: String, writer: LayoutAssetEntryWriter) {
  for (archive in archives) {
    val name = archive.fileName.toString().lowercase()
    require(name.endsWith(".zip") || name.endsWith(".jar")) { "A gzip-xml-archive transform reads a zip or jar archive: $archive" }
    @Suppress("DEPRECATION")
    ZipFile(archive).use { zip ->
      val entries = zip.entries
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val path = entry.name.removeSuffix("/")
        if (path.isEmpty()) continue
        validatePreparationPath(path)
        require(!entry.isUnixSymlink) { "Unexpected file '$path' in $archive" }
        if (entry.isDirectory) continue
        require(path.endsWith(".xml")) { "Unexpected file '$path' in $archive" }
        val content = zip.getInputStream(entry).use { it.readAllBytes() }
        val bytes = ByteArrayOutputStream()
        FastGzipOutputStream(bytes).use { it.write(content) }
        writer.file(joinLayoutAssetPath(destination, "$path.gzip"), bytes.toByteArray(), 420)
      }
    }
  }
}

private fun joinLayoutAssetPath(first: String, second: String): String {
  return when {
    first.isEmpty() -> second
    second.isEmpty() -> first
    else -> "$first/$second"
  }
}

private fun effectiveLayoutAssetMode(path: Path, override: Int): Int {
  if (override != 0) return override
  return fileMode(path) and 0x1FF
}

private class FastGzipOutputStream(output: ByteArrayOutputStream) : GZIPOutputStream(output) {
  init {
    def.setLevel(Deflater.BEST_SPEED)
  }
}
