package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CustomAssetDescriptor
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import java.nio.file.Path

/**
 * Whether the classic dev build runs [owner], a resource generator, a custom asset or a layout patcher.
 * The spec of the owner states it, see [DevPluginLayoutAssetSpec.runsInClassicDev].
 */
internal fun runsInClassicDevMode(owner: DevPluginLayoutAssetOwner): Boolean = owner.devPluginLayoutAssetSpec.runsInClassicDev()

/**
 * The items of [items] that a build runs. A bundled build runs every item. The classic dev build runs an item without a
 * [DevPluginLayoutAssetSpec] and an item that [runsInClassicDevMode] keeps.
 */
internal fun <T : Any> selectForBuild(items: List<T>, classicDev: Boolean): List<T> {
  if (!classicDev) {
    return items
  }
  return items.filter { item -> (item as? DevPluginLayoutAssetOwner)?.let(::runsInClassicDevMode) ?: true }
}

@ApiStatus.Internal
class DeclaredPluginLayoutResourceGenerator(
  override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec,
  private val delegate: ResourceGenerator,
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
