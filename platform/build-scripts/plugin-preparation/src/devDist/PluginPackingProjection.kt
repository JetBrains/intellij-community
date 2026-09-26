package org.jetbrains.intellij.build.devDist

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation

/**
 * The plan file of one complex plugin. The preparer recomputes [layoutSignature] from the other fields and refuses a
 * stale file. [operations] holds the preparation operations with their options. Its IDs are the IDs of [preparations].
 * [plan] ignores [operations]. The preparer compiles them.
 *
 * The file states a reused jar as a module asset only. The chain hands the reused modules to the packer, so the file
 * holds no label of a `content_module_jar` target and no second spelling of the reuse.
 *
 * For a chain with `preparation = "none"` the file is also the contract of `plugin-remainder-packer --projection`.
 * Its Go decoder, the `planfile` package, is strict. It rejects an unknown field and a duplicate key. It rejects every
 * operation kind or transform outside the Go-executed set.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PluginPackingProjection(
  @EncodeDefault(EncodeDefault.Mode.ALWAYS) @JvmField val version: Int = 1,
  @JvmField val plugin: String,
  @JvmField val variant: String,
  @JvmField val layoutSignature: String,
  /** Encoded in the compact form; see `PluginPackingProjectionEncoding.kt`. */
  @Serializable(with = CompactPluginPackingAssetsSerializer::class) @JvmField val assets: List<PluginPackingAsset>,
  @JvmField val preparations: List<PluginPackingPreparation> = emptyList(),
  @JvmField val preparationRoots: List<String> = emptyList(),
  @JvmField val operations: List<DevPluginPreparationOperation> = emptyList(),
) {
  /**
   * The plan with the reuse decision. [reusableArtifacts] are the jars the chain reuses; every one must match an
   * asset by recipe and mode, and a module may occur once.
   */
  fun plan(reusableArtifacts: Collection<ReusableJarArtifact> = emptyList()): PluginPackingPlan {
    val executionVersion = pluginPackingExecutionVersion(assets)
    require(version == executionVersion) {
      "Unsupported plugin projection version: $version. These assets require version $executionVersion."
    }
    val result = planPluginPacking(
      plugin = plugin,
      variant = variant,
      assets = assets,
      preparations = preparations,
      preparationRoots = preparationRoots,
      artifacts = reusableArtifacts,
    )
    result.validateLayout(layoutSignature)
    val usedModules = result.assets.mapNotNull { it.artifact?.module }.toSet()
    require(reusableArtifacts.size == usedModules.size && reusableArtifacts.map(ReusableJarArtifact::module).toSet() == usedModules) {
      "Plugin '$plugin' has duplicate or unused reusable artifacts. Regenerate the dev distribution declarations."
    }
    return result
  }
}
