// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.ClassicDevRun
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Path

/**
 * The one omission statement of a layout callback. [DevPluginLayoutAssetSpec.runsInClassicDev] decides whether the
 * classic dev build runs a resource generator, a platform resource generator, a custom asset or a layout patcher.
 * A bundled build runs every callback.
 */
class PlatformResourceGeneratorRunTest {
  private val distribution = SupportedDistribution(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC)

  private val declared = DevPluginLayoutAssetSpec(assets = listOf(DevPluginLayoutAsset(destination = "lib")))

  @Test
  fun `the spec derives the classic dev run from the omission`() {
    assertThat(DevPluginLayoutAssetSpec.OMITTED.runsInClassicDev()).isFalse()
    assertThat(declared.runsInClassicDev()).isTrue()
    assertThat(DevPluginLayoutAssetSpec.OMITTED.copy(classicDev = ClassicDevRun.RUN).runsInClassicDev()).isTrue()
    assertThat(declared.copy(classicDev = ClassicDevRun.SKIP).runsInClassicDev()).isFalse()
  }

  @Test
  fun `the classic dev build selects the ordinary generators by their spec`() {
    val layout = PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withGeneratedResources(DevPluginLayoutAssetSpec.OMITTED) { _, _ -> }
      spec.withGeneratedResources(declared) { _, _ -> }
      spec.withGeneratedResources(DevPluginLayoutAssetSpec.OMITTED.copy(classicDev = ClassicDevRun.RUN)) { _, _ -> }
      spec.withGeneratedResources(declared.copy(classicDev = ClassicDevRun.SKIP)) { _, _ -> }
      spec.withResourceTree(moduleName = "test.plugin", resourcePath = "resources", relativeOutputPath = "resources")
      spec.withLibraryResources(libraryName = "test.library", relativeOutputPath = "library")
    }
    val all = layout.resourceGenerators

    assertThat(layout.resourceGeneratorsFor(classicDev = false)).containsExactlyElementsOf(all)
    assertThat(layout.resourceGeneratorsFor(classicDev = true)).containsExactly(all[1], all[2], all[4], all[5])
  }

  @Test
  fun `the classic dev build runs a platform generator by its spec`(@TempDir tempDir: Path) {
    val invocations = ArrayList<String>()
    val layout = createPlatformLayout(invocations)

    buildPlatformSpecificPluginResources(layout, pluginDirs(tempDir), createContext(tempDir), isDevMode = true)

    assertThat(invocations).containsExactly("declared", "omitted-run")
  }

  @Test
  fun `a bundled build runs every platform generator in declaration order`(@TempDir tempDir: Path) {
    val invocations = ArrayList<String>()
    val layout = createPlatformLayout(invocations)

    buildPlatformSpecificPluginResources(layout, pluginDirs(tempDir), createContext(tempDir), isDevMode = false)

    assertThat(invocations).containsExactly("omitted", "declared", "omitted-run", "declared-skip")
  }

  @Test
  fun `a plugin with no generator for the target distribution runs nothing`(@TempDir tempDir: Path) {
    val invocations = ArrayList<String>()
    val layout = createPlatformLayout(invocations)
    val otherDistribution = SupportedDistribution(OsFamily.LINUX, JvmArchitecture.aarch64, LinuxLibcImpl.GLIBC)

    buildPlatformSpecificPluginResources(layout, listOf(otherDistribution to tempDir.resolve("plugin")), createContext(tempDir), isDevMode = false)

    assertThat(invocations).isEmpty()
  }

  @Test
  fun `an omitted platform generator makes the layout platform-specific`() {
    val layout = PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withGeneratedPlatformResources(platform = distribution, layoutAssetSpec = DevPluginLayoutAssetSpec.OMITTED) { _, _ -> }
    }

    assertThat(layout.hasPlatformSpecificResources).isTrue()
    assertThat(layout.platformResourceGeneratorsFor(distribution, classicDev = true)).isEmpty()
    assertThat(layout.platformResourceGeneratorsFor(distribution, classicDev = false)).hasSize(1)
  }

  @Test
  fun `the classic dev build selects the layout patchers by their spec`() {
    val plain: LayoutPatcher = { _, _, _ -> }
    val omitted = DeclaredPluginLayoutPatcher(DevPluginLayoutAssetSpec.OMITTED) { _, _, _ -> }
    val omittedRun = DeclaredPluginLayoutPatcher(DevPluginLayoutAssetSpec.OMITTED.copy(classicDev = ClassicDevRun.RUN)) { _, _, _ -> }
    val layout = PluginLayout.pluginAuto(listOf("test.plugin")) { }
    layout.withPatch(omitted)
    layout.withPatch(plain)
    layout.withPatch(omittedRun)

    assertThat(layout.patchersFor(classicDev = false)).containsExactly(omitted, plain, omittedRun)
    assertThat(layout.patchersFor(classicDev = true)).containsExactly(plain, omittedRun)
  }

  @Test
  fun `the classic dev build selects the custom assets by their spec`() {
    val layout = PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withCustomAsset(DevPluginLayoutAssetSpec.OMITTED) { null }
      spec.withCustomAsset(declared) { null }
      spec.withCustomAsset(distribution, DevPluginLayoutAssetSpec.OMITTED.copy(classicDev = ClassicDevRun.RUN)) { null }
    }
    val all = layout.customAssets

    assertThat(layout.customAssetsFor(classicDev = false)).containsExactlyElementsOf(all)
    assertThat(layout.customAssetsFor(classicDev = true)).containsExactly(all[1], all[2])
  }

  private fun createPlatformLayout(invocations: MutableList<String>): PluginLayout {
    return PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withGeneratedPlatformResources(distribution, DevPluginLayoutAssetSpec.OMITTED) { _, _ -> invocations.add("omitted") }
      spec.withGeneratedPlatformResources(distribution, declared) { _, _ -> invocations.add("declared") }
      spec.withGeneratedPlatformResources(distribution, DevPluginLayoutAssetSpec.OMITTED.copy(classicDev = ClassicDevRun.RUN)) { _, _ ->
        invocations.add("omitted-run")
      }
      spec.withGeneratedPlatformResources(distribution, declared.copy(classicDev = ClassicDevRun.SKIP)) { _, _ ->
        invocations.add("declared-skip")
      }
    }
  }

  private fun pluginDirs(tempDir: Path): List<Pair<SupportedDistribution, Path>> = listOf(distribution to tempDir.resolve("plugin"))

  private fun createContext(tempDir: Path): BuildContext {
    val paths = BuildPaths(
      communityHomeDirRoot = COMMUNITY_ROOT,
      buildOutputDir = tempDir,
      logDir = tempDir.resolve("log"),
      projectHome = COMMUNITY_ROOT.communityRoot,
      artifactDir = tempDir.resolve("artifacts"),
      tempDir = tempDir.resolve("temp"),
    )
    val context = mock(BuildContext::class.java)
    `when`(context.paths).thenReturn(paths)
    return context
  }
}
