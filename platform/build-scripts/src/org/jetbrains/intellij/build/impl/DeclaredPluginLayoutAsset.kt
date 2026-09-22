package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CustomAssetDescriptor
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import java.nio.file.Path

/**
 * States when a declared platform resource generator runs.
 * The dev-distribution generator plans the declared tree in both cases.
 */
enum class DeclaredResourceGeneratorRun {
  /** Runs in a bundled build and in classic dev mode. */
  BUNDLED_AND_DEV,

  /** Runs in a bundled build only. Classic dev mode skips it. */
  BUNDLED_ONLY,
}

internal class DeclaredPluginLayoutResourceGenerator(
  override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec,
  private val delegate: ResourceGenerator,
  @JvmField val run: DeclaredResourceGeneratorRun = DeclaredResourceGeneratorRun.BUNDLED_AND_DEV,
) : DevPluginLayoutAssetOwner, ResourceGenerator {
  override fun invoke(targetDirectory: Path, context: BuildContext) {
    delegate(targetDirectory, context)
  }
}

internal class DeclaredPluginLayoutCustomAsset(
  override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec,
  delegate: CustomAssetDescriptor,
) : DevPluginLayoutAssetOwner, CustomAssetDescriptor by delegate

internal class DeclaredPluginLayoutPatcher(
  override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec,
  private val delegate: LayoutPatcher,
) : DevPluginLayoutAssetOwner, (ModuleOutputPatcher, PlatformLayout, BuildContext) -> Unit {
  override fun invoke(moduleOutputPatcher: ModuleOutputPatcher, platformLayout: PlatformLayout, context: BuildContext) {
    delegate(moduleOutputPatcher, platformLayout, context)
  }
}
