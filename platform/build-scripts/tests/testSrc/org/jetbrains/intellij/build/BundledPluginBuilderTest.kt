// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.createDistributionBuilderState
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.testBuildBundledPluginsForAllPlatforms
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.lang.reflect.Method
import java.nio.file.Path

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
    val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot)
    val failureMessage = "platform build failed"

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        val context = createBuildContext(COMMUNITY_ROOT.communityRoot, productProperties)
        val state = createDistributionBuilderState(context)
        val buildPlatformJob = CompletableDeferred<List<DistributionFileEntry>>().also {
          it.completeExceptionally(IllegalStateException(failureMessage))
        }

        testBuildBundledPluginsForAllPlatforms(
          state = state,
          pluginLayouts = emptySet(),
          buildPlatformJob = buildPlatformJob,
          descriptorCacheContainer = state.platformLayout.descriptorCacheContainer,
          context = context,
        )
      }
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessage(failureMessage)
  }

  private fun collectOsSpecificTasks(
    pluginDirs: List<Pair<SupportedDistribution, Path>>,
    pluginLayouts: Collection<PluginLayout>,
    context: BuildContext,
  ): List<OsSpecificTaskDescription> {
    val tasks = collectOsSpecificBundledPluginBuildTasksMethod.invoke(null, pluginDirs, pluginLayouts, context) as List<*>
    return tasks.map { task ->
      val taskClass = checkNotNull(task).javaClass
      @Suppress("UNCHECKED_CAST")
      val dist = taskClass.getField("dist").get(task) as SupportedDistribution
      @Suppress("UNCHECKED_CAST")
      val plugins = taskClass.getField("plugins").get(task) as List<PluginLayout>
      OsSpecificTaskDescription(dist = dist, pluginModules = plugins.map { it.mainModule })
    }
  }

  private data class OsSpecificTaskDescription(
    val dist: SupportedDistribution,
    val pluginModules: List<String>,
  )

  companion object {
    private val collectOsSpecificBundledPluginBuildTasksMethod: Method = Class
      .forName("org.jetbrains.intellij.build.impl.plugins.BundledPluginBuilderKt")
      .getDeclaredMethod("collectOsSpecificBundledPluginBuildTasks", List::class.java, Collection::class.java, BuildContext::class.java)
  }
}
