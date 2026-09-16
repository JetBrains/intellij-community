package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PLUGIN_ASSET_SCOPE

@ApiStatus.Internal
@Serializable
data class DevPluginExecutionRecipe(
  @JvmField val version: Int = 1,
  @JvmField val plugin: String,
  @JvmField val layoutSignature: String,
  @JvmField val assets: List<DevPluginExecutionAsset>,
  @JvmField val operations: List<DevPluginExecutionOperation>,
) {
  init {
    require(version in 1..3 && (version >= 2 || operations.none { it.kind == "copy-tree" || it.kind == "layout-tree" })) {
      "Unsupported plugin execution version: $version. A copy-tree or layout-tree operation requires version 2 or 3."
    }
  }
}

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginExecutionAsset(
  @JvmField val destination: String,
  @JvmField val producer: String,
  @JvmField val artifact: String = "",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val kind: String = "file",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val classPath: Boolean = true,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val normalizeTreeModes: Boolean = false,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val scope: String = PLUGIN_ASSET_SCOPE,
)

/**
 * One remainder operation. A `layout-tree` operation writes a tree from [layout] in the Go packer; it has no [input].
 * The other kinds are jar, copy, directory, symlink and copy-tree.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginExecutionOperation(
  @JvmField val kind: String,
  @JvmField val destination: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val scope: String = PLUGIN_ASSET_SCOPE,
  @JvmField val sources: List<DevPluginExecutionSource> = emptyList(),
  @JvmField val options: DevPluginExecutionJarOptions? = null,
  @JvmField val input: DevPluginReference? = null,
  @JvmField val target: String = "",
  @JvmField val mode: Int = 0,
  @JvmField val layout: DevPluginExecutionLayoutAssets? = null,
)

/**
 * The `layoutAssets` payload of a plan file with its inputs as catalogue references. The Go packer executes it as a
 * `layout-tree` operation or a `layout` jar source. Both producers of the recipe copy it field for field.
 */
@ApiStatus.Internal
@Serializable
data class DevPluginExecutionLayoutAssets(
  @JvmField val inputs: List<DevPluginReference>,
  @JvmField val assets: List<DevPluginLayoutAsset>,
)

@ApiStatus.Internal
@Serializable
data class DevPluginExecutionJarOptions(
  @JvmField val mergeEntities: Boolean = false,
  @JvmField val directories: String = "none",
  @JvmField val verifyCrc: Boolean = false,
)

/**
 * One jar source. An `archive` source with the `module` filter can carry [excludes], the Java globs of a module-filter
 * operation. A `layout` source adds the file entries that the Go packer writes from [layout].
 */
@ApiStatus.Internal
@Serializable
data class DevPluginExecutionSource(
  @JvmField val kind: String,
  @JvmField val input: DevPluginReference? = null,
  @JvmField val library: String = "",
  @JvmField val filter: String = "",
  @JvmField val excludes: List<String> = emptyList(),
  @JvmField val manifest: String,
  @JvmField val entries: List<DevPluginPreparedEntry> = emptyList(),
  @JvmField val overrides: List<DevPluginEntryOverride> = emptyList(),
  /** The ID of the prepared directory that owns this source. It does not supply bytes. */
  @JvmField val prepared: String = "",
  @JvmField val layout: DevPluginExecutionLayoutAssets? = null,
)

@ApiStatus.Internal
@Serializable
data class DevPluginPreparedEntry(
  @JvmField val kind: String,
  @JvmField val name: String,
  @JvmField val input: DevPluginReference? = null,
)

@ApiStatus.Internal
@Serializable
data class DevPluginEntryOverride(
  @JvmField val kind: String,
  @JvmField val name: String,
  @JvmField val input: DevPluginReference? = null,
)
