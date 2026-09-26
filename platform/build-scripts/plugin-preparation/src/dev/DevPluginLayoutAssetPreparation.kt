package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems

/**
 * A semantic source that the dev-plugin generator resolves to declared Bazel inputs.
 * A source names a label and never a pinned version. The dev-launch extension writes each pinned value into its own
 * repository, so a version bump changes no plan file.
 */
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
    /** A concrete name without a version. */
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

  data class ModuleLibrary(
    @JvmField val module: String,
    @JvmField val name: String,
  ) : DevPluginLayoutAssetSource

  data class ExternalLocalizationTree(
    @JvmField val folder: String,
    @JvmField val language: String,
  ) : DevPluginLayoutAssetSource

  /**
   * One CIDR dependency archive, as a `<name>-dependencies.json` file under `CIDR/` declares it for one platform and architecture.
   * [name] is the `name` of that configuration, such as `cmake` or `LLDBFrontend`. [platform] is a CIDR platform
   * token: `any`, `cygwin`, `linux`, `mac`, `win`, or `wsl`. [arch] is a CIDR architecture token: `any`, `aarch64`,
   * `x64`, or `x86`. The generator binds the source to the `dev_launch_cidr_<name>_<platform>_<arch>` repository
   * that `build/dev_launch_dependencies.bzl` declares from the same configuration. The archive is a `.zip` for `win`
   * and a `.tar.gz` otherwise. An asset extracts it with an `archive-tree` transform, whose `includes` carry the
   * `filePatterns` and whose `executables` carry the `executablePatterns` of the configuration entry.
   */
  data class CidrDependency(
    @JvmField val name: String,
    @JvmField val platform: String,
    @JvmField val arch: String,
  ) : DevPluginLayoutAssetSource

  /**
   * The RustRover native helper archive. One archive holds the helper of every platform under `<os>/<arch>/`, where
   * [os] is an `OsFamily.dirName` (`mac`, `linux`, `win`) and [arch] a `JvmArchitecture` name. The generator binds
   * the source to the `dev_launch_rust_native_helper` repository. An asset selects its platform with an
   * `archive-tree` mapping whose pattern is the `<os>/<arch>/` prefix followed by a double star.
   */
  data class RustNativeHelper(
    @JvmField val os: String,
    @JvmField val arch: String,
  ) : DevPluginLayoutAssetSource

  /**
   * A directory below `out/bundle-plugins` that a local backend build may create. The dev distribution declares its
   * files when present and writes an empty tree when absent. Both states have different action keys.
   */
  data class OptionalLocalDirectory(
    @JvmField val path: String,
  ) : DevPluginLayoutAssetSource

  data class GdScriptSdk(
    @JvmField val version: String,
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
 *
 * [hostPlatforms] names the `HOST_PLATFORMS` entries the asset serves, such as `darwin_aarch64`. An empty list serves
 * every platform. The generator keeps the asset in the plan of a named platform and drops it from every other plan,
 * so a source only such assets use is never bound there. The payload never carries the list: the plan of one platform
 * is already selected when the payload is written.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginLayoutAsset(
  @JvmField val destination: String,
  @JvmField val sources: List<Int> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val transform: DevPluginLayoutAssetTransform? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val mode: Int = 0,
  @Transient @JvmField val hostPlatforms: List<String> = emptyList(),
)

/** One ordered path mapping. The first contribution to a destination wins. */
@ApiStatus.Internal
@Serializable
data class DevPluginLayoutAssetMapping(
  @JvmField val pattern: String = "**",
  @JvmField val stripComponents: Int = 0,
  @JvmField val destination: String = "",
)

/**
 * The transform for one layout asset. Use the factory functions to create supported transforms.
 *
 * [includes] belong to `archive-tree`: ordered java.nio globs over the stripped entry path before mapping. A pattern
 * with a leading `!` excludes. The last matching pattern decides an entry. An entry no pattern matches is written when
 * every pattern excludes, and dropped otherwise. These are the `filePatterns` rules of a CIDR dependency.
 * [executables] belong to `archive-tree` and `tree-map`: java.nio globs over the same path, or over the
 * source-relative path of a tree. A regular file that matches gets the executable bits.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginLayoutAssetTransform(
  @JvmField val kind: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val stripComponents: Int = 0,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val mappings: List<DevPluginLayoutAssetMapping> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val excludes: List<String> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val directoryExcludes: List<String> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val includes: List<String> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val executables: List<String> = emptyList(),
) {
  companion object {
    fun archiveTree(
      stripComponents: Int = 0,
      mappings: List<DevPluginLayoutAssetMapping> = emptyList(),
      includes: List<String> = emptyList(),
      executables: List<String> = emptyList(),
    ): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(
        kind = "archive-tree",
        stripComponents = stripComponents,
        mappings = mappings,
        includes = includes,
        executables = executables,
      )
    }

    fun gzipXmlArchive(): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(kind = "gzip-xml-archive")
    }

    fun treeMap(
      mappings: List<DevPluginLayoutAssetMapping>,
      excludes: List<String> = emptyList(),
      directoryExcludes: List<String> = emptyList(),
      executables: List<String> = emptyList(),
    ): DevPluginLayoutAssetTransform {
      return DevPluginLayoutAssetTransform(
        kind = "tree-map",
        mappings = mappings,
        excludes = excludes,
        directoryExcludes = directoryExcludes,
        executables = executables,
      )
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
 * Validates one layout-assets payload at generation time. The Go packer ports these rules to `plan.go`.
 * A `gzip-xml-archive` asset requires the `entries` format.
 */
internal fun validateDevPluginLayoutAssetPreparation(
  preparation: DevPluginLayoutAssetPreparation,
  inputs: List<DevPluginReference>,
) {
  require(preparation.format in setOf("entries", "tree")) { "Unknown layout asset format '${preparation.format}'" }
  require(preparation.assets.isNotEmpty()) { "A layout asset preparation requires assets" }
  if (preparation.format == "tree" && preparation.root.isNotEmpty()) {
    validatePreparationPath(preparation.root)
  }
  else if (preparation.format == "entries") {
    require(preparation.root.isEmpty()) { "An entry preparation must not declare a tree root" }
  }
  for (asset in preparation.assets) {
    val transform = asset.transform
    require(asset.hostPlatforms.isEmpty()) { "A layout asset payload must not name host platforms: ${asset.destination}" }
    if (asset.destination.isEmpty()) {
      // An entry asset writes its output root when every entry brings its own relative path: a mapped tree, an
      // extracted archive, a gzip archive, or a copied directory. The Go packer checks the directory kind.
      require(preparation.format == "tree" ||
              preparation.format == "entries" && transform?.kind in setOf("archive-tree", "gzip-xml-archive", "tree-map", null)) {
        "Only a tree, a mapped entry asset, an extracted archive, a gzip archive, or a copied directory can use its output root"
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
    require(transform.kind in setOf("archive-tree", "gzip-xml-archive", "tree-map")) {
      "Unknown layout asset transform '${transform.kind}'"
    }
    require(transform.stripComponents >= 0) { "A layout asset strip count must not be negative" }
    require(transform.kind == "tree-map" || transform.excludes.isEmpty() && transform.directoryExcludes.isEmpty()) {
      "Only a tree-map transform accepts exclusions"
    }
    for (pattern in transform.excludes + transform.directoryExcludes) {
      require(pattern.isNotEmpty()) { "A layout asset exclusion requires a pattern" }
      FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
    require(transform.kind == "archive-tree" || transform.includes.isEmpty()) { "Only an archive-tree transform accepts includes" }
    for (pattern in transform.includes) {
      val glob = pattern.removePrefix("!")
      require(glob.isNotEmpty()) { "A layout asset include requires a pattern" }
      FileSystems.getDefault().getPathMatcher("glob:$glob")
    }
    require(transform.kind in setOf("archive-tree", "tree-map") || transform.executables.isEmpty()) {
      "Only an archive-tree or a tree-map transform accepts executable patterns"
    }
    for (pattern in transform.executables) {
      require(pattern.isNotEmpty()) { "A layout asset executable pattern requires a pattern" }
      FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
    for (mapping in transform.mappings) {
      require(mapping.pattern.isNotEmpty() && mapping.stripComponents >= 0) { "A layout asset mapping requires a pattern and a valid strip count" }
      if (mapping.destination.isNotEmpty()) validatePreparationPath(mapping.destination)
      FileSystems.getDefault().getPathMatcher("glob:${mapping.pattern}")
    }
    when (transform.kind) {
      "archive-tree" -> require(asset.sources.size == 1) {
        "An archive-tree transform requires one archive"
      }
      "gzip-xml-archive" -> require(preparation.format == "entries" && asset.sources.isNotEmpty() && transform.stripComponents == 0 &&
                                              transform.mappings.isEmpty()) {
        "A gzip-xml-archive transform requires ordered archive inputs and an entries output"
      }
      "tree-map" -> require(asset.sources.isNotEmpty() && transform.stripComponents == 0 && transform.mappings.isNotEmpty()) {
        "A tree-map transform requires ordered tree inputs and mappings"
      }
    }
  }
}
