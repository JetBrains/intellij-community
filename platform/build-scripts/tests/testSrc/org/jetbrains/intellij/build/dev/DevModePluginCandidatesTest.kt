// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginDistribution
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * A plugin arrives as one variant for each supported `(os, arch)`, and a distribution holds one of them.
 *
 * So [devModePluginCandidates] selects a variant. These tests cover the three answers it can reach: one variant, no
 * variant, and more than one variant.
 */
class DevModePluginCandidatesTest {
  private val plugin = "intellij.example.plugin"

  @Test
  fun `one variant of each supported platform yields the variant of the target`() {
    val variants = SUPPORTED_PLATFORMS.map { (os, arch) -> osSpecificVariant(os = os, arch = arch) }

    val candidates = devModePluginCandidates(
      request = buildRequest(additionalModules = listOf(plugin)),
      context = context(variants = variants, useReleaseCycleRelatedBundlingRestrictions = false),
    )

    assertThat(candidates.map { it.bundlingRestrictions.supportedOs to it.bundlingRestrictions.supportedArch })
      .containsExactly(persistentListOf(TARGET_OS) to persistentListOf(TARGET_ARCH))
  }

  @Test
  fun `a plugin of another operating system is absent without a complaint`() {
    val macOsOnly = PluginLayout.pluginAuto(listOf(plugin)) {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    }

    val candidates = devModePluginCandidates(
      request = buildRequest(additionalModules = listOf(plugin)),
      context = context(variants = listOf(macOsOnly), useReleaseCycleRelatedBundlingRestrictions = false),
    )

    assertThat(candidates).isEmpty()
  }

  @Test
  fun `a marketplace variant is absent without a complaint`() {
    val marketplaceOnly = PluginLayout.pluginAuto(listOf(plugin)) {
      it.bundlingRestrictions.marketplace = true
    }

    val candidates = devModePluginCandidates(
      request = buildRequest(additionalModules = listOf(plugin)),
      context = context(variants = listOf(marketplaceOnly), useReleaseCycleRelatedBundlingRestrictions = false),
    )

    assertThat(candidates).isEmpty()
  }

  // IJAI-955: the release cycle dropped `intellij.air.plugin` and `intellij.devkit` from an assembly that had asked for
  // them by name. The only symptom was 149 Bazel-built jars with no destination, two layers away.
  @Test
  fun `a plugin the release cycle drops fails`() {
    val notForPublicBuilds = PluginLayout.pluginAuto(listOf(plugin)) {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
    }

    assertThatThrownBy {
      devModePluginCandidates(
        request = buildRequest(additionalModules = listOf(plugin)),
        context = context(variants = listOf(notForPublicBuilds), useReleaseCycleRelatedBundlingRestrictions = true),
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(plugin)
      .hasMessageContaining("left it out of the distribution")
      .hasMessageContaining("LINUX x64")
  }

  @Test
  fun `a plugin nobody asked for is absent without a complaint`() {
    val notForPublicBuilds = PluginLayout.pluginAuto(listOf(plugin)) {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
    }

    val candidates = devModePluginCandidates(
      request = buildRequest(additionalModules = emptyList()),
      context = context(
        variants = listOf(notForPublicBuilds),
        useReleaseCycleRelatedBundlingRestrictions = true,
        bundledPluginModules = listOf(plugin),
      ),
    )

    assertThat(candidates).isEmpty()
  }

  @Test
  fun `two variants of one plugin that cover the target fail`() {
    val targetVariant = osSpecificVariant(os = TARGET_OS, arch = TARGET_ARCH)
    val everyArchOfTwoOperatingSystems = PluginLayout.pluginAuto(listOf(plugin)) {
      it.bundlingRestrictions.supportedOs = persistentListOf(TARGET_OS, OsFamily.MACOS)
    }

    assertThatThrownBy {
      devModePluginCandidates(
        request = buildRequest(additionalModules = listOf(plugin)),
        context = context(
          variants = listOf(targetVariant, everyArchOfTwoOperatingSystems),
          useReleaseCycleRelatedBundlingRestrictions = false,
        ),
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(plugin)
      .hasMessageContaining("2 variants for LINUX x64")
      .hasMessageContaining("would overwrite each other")
  }

  private fun osSpecificVariant(os: OsFamily, arch: JvmArchitecture): PluginLayout {
    return PluginLayout.pluginAuto(listOf(plugin)) {
      it.bundlingRestrictions.supportedOs = persistentListOf(os)
      it.bundlingRestrictions.supportedArch = persistentListOf(arch)
    }
  }

  /** A request for [TARGET_OS] [TARGET_ARCH], which every test here builds for. */
  private fun buildRequest(additionalModules: List<String>): BuildRequest {
    return BuildRequest(
      platformPrefix = "Example",
      additionalModules = additionalModules,
      projectDir = COMMUNITY_ROOT.communityRoot,
      os = TARGET_OS,
      arch = TARGET_ARCH,
    )
  }

  private fun context(
    variants: List<PluginLayout>,
    useReleaseCycleRelatedBundlingRestrictions: Boolean,
    bundledPluginModules: List<String> = emptyList(),
  ): BuildContext {
    val productLayout = mock(ProductModulesLayout::class.java)
    `when`(productLayout.pluginLayouts).thenReturn(variants.toPersistentList())

    val productProperties = mock(ProductProperties::class.java)
    `when`(productProperties.productLayout).thenReturn(productLayout)

    // A release build is neither nightly nor EAP, so `NOT_FOR_PUBLIC_BUILDS` is dropped whenever the flag is on.
    val applicationInfo = mock(ApplicationInfoProperties::class.java)
    `when`(applicationInfo.isEAP).thenReturn(false)

    val context = mock(BuildContext::class.java)
    `when`(context.productProperties).thenReturn(productProperties)
    `when`(context.getBundledPluginModules()).thenReturn(bundledPluginModules)
    `when`(context.applicationInfo).thenReturn(applicationInfo)
    `when`(context.isNightlyBuild).thenReturn(false)
    `when`(context.options).thenReturn(
      BuildOptions(useReleaseCycleRelatedBundlingRestrictions = useReleaseCycleRelatedBundlingRestrictions)
    )
    return context
  }

  private companion object {
    private val TARGET_OS = OsFamily.LINUX
    private val TARGET_ARCH = JvmArchitecture.x64

    private val SUPPORTED_PLATFORMS = listOf(
      OsFamily.WINDOWS to JvmArchitecture.x64,
      OsFamily.WINDOWS to JvmArchitecture.aarch64,
      OsFamily.MACOS to JvmArchitecture.x64,
      OsFamily.MACOS to JvmArchitecture.aarch64,
      OsFamily.LINUX to JvmArchitecture.x64,
      OsFamily.LINUX to JvmArchitecture.aarch64,
    )
  }
}
