package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class DevPluginReference(
  @JvmField val artifact: String,
  @JvmField val path: String = "",
)

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginArtifact(
  @JvmField val id: String,
  @JvmField val kind: String,
  @JvmField val root: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val tree: DevPluginOwnedTree? = null,
)

/**
 * Records the logical tree before transport. Catalogue version 1 carries this optional version 2 extension.
 * Recipe version 2 reserves its subtree. Absent metadata leaves version 1 catalogue bytes unchanged.
 * The producer owns this metadata. It is not a sandbox boundary against a malicious catalogue.
 * The writer recreates genuine links without following their transported nodes. Only declared regular files can use transport links.
 * The writer checks each regular payload against its size and hash, then restores the recorded modes and empty directories.
 */
@ApiStatus.Internal
@Serializable
data class DevPluginOwnedTree(
  @JvmField val version: Int,
  @JvmField val artifact: String,
  @JvmField val plugin: String,
  @JvmField val layoutSignature: String,
  @JvmField val rootMode: Int,
  @JvmField val entries: List<DevPluginTreeEntry>,
  /** Omits the destination root when this tree has no entries. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val omitRoot: Boolean = false,
) {
  init {
    require(version == 2 && artifact.isNotBlank() && plugin.isNotBlank() && layoutSignature.isNotBlank() && rootMode in 0..511 &&
            (!omitRoot || entries.isEmpty())) {
      "Invalid version or ownership in prepared tree metadata"
    }
  }
}

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginTreeEntry(
  @JvmField val relativePath: String,
  @JvmField val type: String,
  @JvmField val hash: Long,
  @JvmField val size: Long,
  @JvmField val mode: Int,
  @JvmField val executable: Boolean,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val symlinkTarget: String = "",
)

@ApiStatus.Internal
@Serializable
data class DevPluginLibrary(
  @JvmField val id: String,
  @JvmField val files: List<DevPluginReference>,
)

@ApiStatus.Internal
@Serializable
data class DevPluginArtifactCatalogue(
  @JvmField val version: Int = 1,
  @JvmField val artifacts: List<DevPluginArtifact>,
  @JvmField val libraries: List<DevPluginLibrary> = emptyList(),
)
