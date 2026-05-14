// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetSourceLabels
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.plugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class DiscoveryResultTest {
  @Test
  fun `community module sets include platform plugin source`() {
    val communityModuleSet = moduleSet("community.base") {
      module("community.module")
    }
    val communityPlatformPlugin = plugin("structureView") {
      module("structure.module")
    }
    val coreModuleSet = moduleSet("core.base") {
      module("core.module")
    }
    val ultimateModuleSet = moduleSet("ultimate.base") {
      module("ultimate.module")
    }

    val discoveryResult = DiscoveryResult(
      moduleSetsByLabel = mapOf(
        ModuleSetSourceLabels.COMMUNITY to listOf(communityModuleSet),
        ModuleSetSourceLabels.COMMUNITY_PLATFORM_PLUGINS to listOf(communityPlatformPlugin),
        ModuleSetSourceLabels.CORE to listOf(coreModuleSet),
        ModuleSetSourceLabels.ULTIMATE to listOf(ultimateModuleSet),
      ),
      products = emptyList(),
      testProductSpecs = emptyList(),
      moduleSetSources = emptyMap(),
    )

    assertThat(discoveryResult.communityModuleSets).containsExactly(communityModuleSet, communityPlatformPlugin)
    assertThat(discoveryResult.coreModuleSets).containsExactly(coreModuleSet)
    assertThat(discoveryResult.ultimateModuleSets).containsExactly(ultimateModuleSet)
  }
}
