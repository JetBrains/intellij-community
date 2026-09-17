package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CustomAssetDescriptor
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import java.nio.file.Path

internal class DeclaredPluginLayoutResourceGenerator(
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
