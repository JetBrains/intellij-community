// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Path

/** The run policy of `buildPlatformSpecificPluginResources` for the declared platform generators. */
class PlatformResourceGeneratorRunTest {
  private val distribution = SupportedDistribution(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC)

  @Test
  fun `dev mode runs only a declared BUNDLED_AND_DEV generator`(@TempDir tempDir: Path) {
    val invocations = ArrayList<String>()
    val layout = createLayout(invocations)

    buildPlatformSpecificPluginResources(layout, pluginDirs(tempDir), createContext(tempDir), isDevMode = true)

    assertThat(invocations).containsExactly("bundled-and-dev")
  }

  @Test
  fun `bundled build runs every generator in declaration order`(@TempDir tempDir: Path) {
    val invocations = ArrayList<String>()
    val layout = createLayout(invocations)

    buildPlatformSpecificPluginResources(layout, pluginDirs(tempDir), createContext(tempDir), isDevMode = false)

    assertThat(invocations).containsExactly("bundled-and-dev", "bundled-only")
  }

  @Test
  fun `a plugin with no generator for the target distribution runs nothing`(@TempDir tempDir: Path) {
    val invocations = ArrayList<String>()
    val layout = createLayout(invocations)
    val otherDistribution = SupportedDistribution(OsFamily.LINUX, JvmArchitecture.aarch64, LinuxLibcImpl.GLIBC)

    buildPlatformSpecificPluginResources(layout, listOf(otherDistribution to tempDir.resolve("plugin")), createContext(tempDir), isDevMode = false)

    assertThat(invocations).isEmpty()
  }

  @Test
  fun `a bundled-only declared generator makes the layout platform-specific`() {
    val layout = PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withGeneratedPlatformResources(
        os = distribution.os,
        arch = distribution.arch,
        libc = distribution.libcImpl,
        layoutAssetSpec = DevPluginLayoutAssetSpec.OMITTED,
        run = DeclaredResourceGeneratorRun.BUNDLED_ONLY,
      ) { _, _ -> }
    }

    assertThat(layout.hasPlatformSpecificResources).isTrue()
    assertThat(layout.platformResourceGenerators.getValue(distribution).single().run).isEqualTo(DeclaredResourceGeneratorRun.BUNDLED_ONLY)
  }

  @Test
  fun `a declared generator runs in bundled and dev mode by default`() {
    val layout = PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withGeneratedPlatformResources(
        os = distribution.os,
        arch = distribution.arch,
        libc = distribution.libcImpl,
        layoutAssetSpec = DevPluginLayoutAssetSpec.OMITTED,
      ) { _, _ -> }
    }

    assertThat(layout.platformResourceGenerators.getValue(distribution).single().run).isEqualTo(DeclaredResourceGeneratorRun.BUNDLED_AND_DEV)
  }

  private fun createLayout(invocations: MutableList<String>): PluginLayout {
    return PluginLayout.pluginAuto(listOf("test.plugin")) { spec ->
      spec.withGeneratedPlatformResources(
        os = distribution.os,
        arch = distribution.arch,
        libc = distribution.libcImpl,
        layoutAssetSpec = DevPluginLayoutAssetSpec.OMITTED,
      ) { _, _ -> invocations.add("bundled-and-dev") }
      spec.withGeneratedPlatformResources(
        os = distribution.os,
        arch = distribution.arch,
        libc = distribution.libcImpl,
        layoutAssetSpec = DevPluginLayoutAssetSpec.OMITTED,
        run = DeclaredResourceGeneratorRun.BUNDLED_ONLY,
      ) { _, _ -> invocations.add("bundled-only") }
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
