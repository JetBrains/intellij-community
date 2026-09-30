// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.dev.isPluginApplicable as isDevModePluginApplicable
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.PRODUCT_DESCRIPTOR_META_PATH
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.createTestDistributionBuilderState
import org.jetbrains.intellij.build.impl.plugins.collectOsSpecificBundledPluginBuildTasks
import org.jetbrains.intellij.build.impl.plugins.writeBundledPluginInfoAfterScramble
import org.jetbrains.intellij.build.impl.testBuildBundledPluginsForAllPlatforms
import org.jetbrains.intellij.build.impl.testLayoutBundledPlugins
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class BundledPluginBuilderTest {
  @Test
  fun collectOsSpecificBundledPluginBuildTasksIsStableForInputOrder() {
    val windowsDist = SupportedDistribution(OsFamily.WINDOWS, JvmArchitecture.x64, WindowsLibcImpl.DEFAULT)
    val linuxDist = SupportedDistribution(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC)
    val pluginDirs = listOf(
      windowsDist to Path.of("windows"),
      linuxDist to Path.of("linux"),
    )
    val commonPlugin = PluginLayout.pluginAuto(listOf("a.plugin"))
    val windowsOnlyPlugin = PluginLayout.pluginAuto(listOf("z.plugin")) {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.WINDOWS)
    }
    val linuxOnlyPlugin = PluginLayout.pluginAuto(listOf("m.plugin")) {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX)
    }
    val applicationInfo = mock(ApplicationInfoProperties::class.java)
    val context = mock(BuildContext::class.java)
    `when`(applicationInfo.isEAP).thenReturn(false)
    `when`(context.options).thenReturn(BuildOptions())
    `when`(context.applicationInfo).thenReturn(applicationInfo)
    `when`(context.isNightlyBuild).thenReturn(false)
    `when`(context.shouldBuildDistributionForOS(OsFamily.WINDOWS, JvmArchitecture.x64)).thenReturn(true)
    `when`(context.shouldBuildDistributionForOS(OsFamily.LINUX, JvmArchitecture.x64)).thenReturn(true)

    val first = collectOsSpecificTasks(
      pluginDirs = pluginDirs,
      pluginLayouts = linkedSetOf(windowsOnlyPlugin, commonPlugin, linuxOnlyPlugin),
      context = context,
    )
    val second = collectOsSpecificTasks(
      pluginDirs = pluginDirs,
      pluginLayouts = linkedSetOf(linuxOnlyPlugin, windowsOnlyPlugin, commonPlugin),
      context = context,
    )

    assertThat(first).isEqualTo(second)
    assertThat(first).containsExactly(
      OsSpecificTaskDescription(windowsDist, listOf("z.plugin")),
      OsSpecificTaskDescription(linuxDist, listOf("m.plugin")),
    )
  }

  @Test
  fun failedPlatformJobIsPropagatedBeforePluginInfoIsWritten() {
    val failureMessage = "platform build failed"

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        val (context, state) = createMinimalBundledPluginBuildState()
        testBuildBundledPluginsForAllPlatforms(
          state = state,
          pluginLayouts = emptySet(),
          platformEntriesProvider = { throw IllegalStateException(failureMessage) },
          descriptorCacheContainer = state.platformLayout.descriptorCacheContainer,
          context = context,
          includeAdditionalPlugins = false,
        )
      }
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessage(failureMessage)
  }

  @Test
  fun layoutOnlyWithoutAdditionalPluginsCompletes() {
    runBlocking(Dispatchers.Default) {
      val (context, state) = createMinimalBundledPluginBuildState()

      val result = withTimeout(5.seconds) {
        testLayoutBundledPlugins(
          state = state,
          pluginLayouts = emptySet(),
          descriptorCacheContainer = state.platformLayout.descriptorCacheContainer,
          context = context,
          includeAdditionalPlugins = false,
        )
      }

      assertThat(result.descriptors).isEmpty()
      assertThat(result.additionalPlugins).isNull()
    }
  }

  @Test
  fun `dev mode plugin applicability uses requested target os`() {
    val macOnlyPlugin = PluginLayout.pluginAuto(listOf("mac.only.plugin")) {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    }
    val applicationInfo = mock(ApplicationInfoProperties::class.java)
    val context = mock(BuildContext::class.java)
    `when`(applicationInfo.isEAP).thenReturn(false)
    `when`(context.options).thenReturn(BuildOptions())
    `when`(context.applicationInfo).thenReturn(applicationInfo)
    `when`(context.isNightlyBuild).thenReturn(false)

    assertThat(
      isDevModePluginApplicable(
        bundledMainModuleNames = setOf(macOnlyPlugin.mainModule),
        plugin = macOnlyPlugin,
        os = OsFamily.MACOS,
        arch = JvmArchitecture.x64,
        context = context,
      )
    ).isTrue()

    assertThat(
      isDevModePluginApplicable(
        bundledMainModuleNames = setOf(macOnlyPlugin.mainModule),
        plugin = macOnlyPlugin,
        os = OsFamily.LINUX,
        arch = JvmArchitecture.x64,
        context = context,
      )
    ).isFalse()
  }

  @Test
  fun `a plugin that is not for public builds survives without release-cycle restrictions`() {
    val internalPlugin = PluginLayout.pluginAuto(listOf("internal.plugin")) {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
    }
    val applicationInfo = mock(ApplicationInfoProperties::class.java)
    val context = mock(BuildContext::class.java)
    `when`(applicationInfo.isEAP).thenReturn(true)
    `when`(context.applicationInfo).thenReturn(applicationInfo)
    `when`(context.isNightlyBuild).thenReturn(false)

    `when`(context.options).thenReturn(BuildOptions(useReleaseCycleRelatedBundlingRestrictions = false))
    assertThat(
      isDevModePluginApplicable(
        bundledMainModuleNames = setOf(internalPlugin.mainModule),
        plugin = internalPlugin,
        os = OsFamily.MACOS,
        arch = JvmArchitecture.x64,
        context = context,
      )
    ).isTrue()

    // what a shipped build does is unchanged
    `when`(context.options).thenReturn(BuildOptions())
    assertThat(
      isDevModePluginApplicable(
        bundledMainModuleNames = setOf(internalPlugin.mainModule),
        plugin = internalPlugin,
        os = OsFamily.MACOS,
        arch = JvmArchitecture.x64,
        context = context,
      )
    ).isFalse()
  }

  @Test
  fun `plugin-classpath variants share the prefix and differ in the plugin count`() {
    val (context, state) = createMinimalBundledPluginBuildState()
    `when`(context.productProperties).thenReturn(mock(ProductProperties::class.java))
    `when`(context.outputProvider).thenReturn(mock(ModuleOutputProvider::class.java))
    val descriptorCache = state.platformLayout.descriptorCacheContainer
    val productDescriptor = "<idea-plugin><id>test.product</id></idea-plugin>".encodeToByteArray()
    descriptorCache.forPlatform(state.platformLayout).put(PRODUCT_DESCRIPTOR_META_PATH, productDescriptor)
    val commonDir = Path.of("plugins", "common")
    val windowsDir = Path.of("plugins", "windows")
    descriptorCache.forPlugin(commonDir).put(PLUGIN_XML_RELATIVE_PATH, "<idea-plugin><id>common</id></idea-plugin>".encodeToByteArray())
    descriptorCache.forPlugin(windowsDir).put(PLUGIN_XML_RELATIVE_PATH, "<idea-plugin><id>windows</id></idea-plugin>".encodeToByteArray())

    writeBundledPluginInfoAfterScramble(
      state = state,
      isUpdateFromSources = false,
      descriptors = listOf(
        PluginBuildResult(mainModule = "common.plugin", dir = commonDir, os = null, arch = null, distribution = emptyList()),
        PluginBuildResult(mainModule = "windows.plugin", dir = windowsDir, os = OsFamily.WINDOWS, arch = JvmArchitecture.x64, distribution = emptyList()),
      ),
      additionalPlugins = null,
      layoutsOfPluginsToScramble = emptyMap(),
      descriptorCacheContainer = descriptorCache,
      context = context,
    )

    val contents = mockingDetails(context).invocations
      .filter { it.method.name == "addDistFile" }
      .map { it.getArgument<DistFile>(0) }
      .associate { SupportedDistribution(it.os!!, it.arch!!, it.libcImpl!!) to it.content as InMemoryDistFileContent }
    assertThat(contents.keys).containsExactlyElementsOf(SUPPORTED_DISTRIBUTIONS)

    val windows = contents.getValue(SupportedDistribution(OsFamily.WINDOWS, JvmArchitecture.x64, WindowsLibcImpl.DEFAULT)).readAllBytes()
    val linux = contents.getValue(SupportedDistribution(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC)).readAllBytes()
    // the format byte, the descriptor size and the product descriptor precede the plugin count
    val descriptorSize = ByteBuffer.wrap(linux, 1, Int.SIZE_BYTES).getInt()
    val prefixSize = 1 + Int.SIZE_BYTES + descriptorSize
    assertThat(windows.copyOfRange(0, prefixSize)).isEqualTo(linux.copyOfRange(0, prefixSize))
    assertThat(ByteBuffer.wrap(windows, prefixSize, Short.SIZE_BYTES).getShort()).isEqualTo(2.toShort())
    assertThat(ByteBuffer.wrap(linux, prefixSize, Short.SIZE_BYTES).getShort()).isEqualTo(1.toShort())
    // the common classpath follows the count, and the Windows variant appends its specific classpath
    val linuxTail = linux.copyOfRange(prefixSize + Short.SIZE_BYTES, linux.size)
    assertThat(windows.copyOfRange(prefixSize + Short.SIZE_BYTES, prefixSize + Short.SIZE_BYTES + linuxTail.size)).isEqualTo(linuxTail)
    assertThat(windows.size).isGreaterThan(linux.size)
    assertThat(contents.filterKeys { it.os != OsFamily.WINDOWS || it.arch != JvmArchitecture.x64 }.values.distinct()).hasSize(1)
  }

  private fun createMinimalBundledPluginBuildState(): Pair<BuildContext, DistributionBuilderState> {
    val applicationInfo = mock(ApplicationInfoProperties::class.java)
    val context = mock(BuildContext::class.java)
    val tempDir = Path.of(System.getProperty("java.io.tmpdir"), "bundled-plugin-builder-test")
    val paths = BuildPaths(
      communityHomeDirRoot = COMMUNITY_ROOT,
      buildOutputDir = tempDir.resolve("build-output"),
      logDir = tempDir.resolve("log"),
      projectHome = COMMUNITY_ROOT.communityRoot,
      artifactDir = tempDir.resolve("artifact"),
      tempDir = tempDir.resolve("temp"),
    )

    `when`(applicationInfo.majorReleaseDate).thenReturn("20260101")
    `when`(context.applicationInfo).thenReturn(applicationInfo)
    `when`(context.options).thenReturn(BuildOptions())
    `when`(context.paths).thenReturn(paths)
    `when`(context.proprietaryBuildTools).thenReturn(ProprietaryBuildTools.DUMMY)

    return context to createTestDistributionBuilderState(context)
  }

  private fun collectOsSpecificTasks(
    pluginDirs: List<Pair<SupportedDistribution, Path>>,
    pluginLayouts: Collection<PluginLayout>,
    context: BuildContext,
  ): List<OsSpecificTaskDescription> {
    return collectOsSpecificBundledPluginBuildTasks(pluginDirs, pluginLayouts, context).map { task ->
      OsSpecificTaskDescription(dist = task.dist, pluginModules = task.plugins.map { it.mainModule })
    }
  }

  private data class OsSpecificTaskDescription(
    val dist: SupportedDistribution,
    val pluginModules: List<String>,
  )

}
