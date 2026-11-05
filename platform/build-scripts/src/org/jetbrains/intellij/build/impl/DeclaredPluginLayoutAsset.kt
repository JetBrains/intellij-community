package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CustomAssetDescriptor
import org.jetbrains.intellij.build.dev.LayoutAssetOwner
import org.jetbrains.intellij.build.dev.LayoutAssetSpec
import java.nio.file.Path

/**
 * Whether the classic dev build runs [owner], a resource generator, a custom asset or a layout patcher.
 * The spec of the owner states it, see [LayoutAssetSpec.runsInClassicDev].
 */
internal fun runsInClassicDevMode(owner: LayoutAssetOwner): Boolean = owner.layoutAssetSpec.runsInClassicDev()

/**
 * The items of [items] that a build runs. A bundled build runs every item. The classic dev build runs an item without a
 * [LayoutAssetSpec] and an item that [runsInClassicDevMode] keeps.
 */
internal fun <T : Any> selectForBuild(items: List<T>, classicDev: Boolean): List<T> {
  if (!classicDev) {
    return items
  }
  return items.filter { item -> (item as? LayoutAssetOwner)?.let(::runsInClassicDevMode) ?: true }
}

@ApiStatus.Internal
class DeclaredPluginLayoutResourceGenerator(
  override val layoutAssetSpec: LayoutAssetSpec,
  private val delegate: ResourceGenerator,
) : LayoutAssetOwner, ResourceGenerator {
  override fun invoke(targetDirectory: Path, context: BuildContext) {
    delegate(targetDirectory, context)
  }
}

internal class DeclaredPluginLayoutCustomAsset(
  override val layoutAssetSpec: LayoutAssetSpec,
  delegate: CustomAssetDescriptor,
) : LayoutAssetOwner, CustomAssetDescriptor by delegate

internal class DeclaredPluginLayoutPatcher(
  override val layoutAssetSpec: LayoutAssetSpec,
  private val delegate: LayoutPatcher,
) : LayoutAssetOwner, (ModuleOutputPatcher, PlatformLayout, BuildContext) -> Unit {
  override fun invoke(moduleOutputPatcher: ModuleOutputPatcher, platformLayout: PlatformLayout, context: BuildContext) {
    delegate(moduleOutputPatcher, platformLayout, context)
  }
}
