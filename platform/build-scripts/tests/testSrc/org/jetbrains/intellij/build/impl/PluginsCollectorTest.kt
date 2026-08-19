// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PluginsCollectorTest {
  @Test
  fun `plugin is compatible when required module is included in its layout`() {
    val mainModule = "test.plugin"
    val includedModule = "test.plugin.content"
    val layout = PluginLayout.pluginAuto(listOf(mainModule, includedModule))
    val descriptor = PluginDescriptor(
      id = "test.plugin.id",
      description = null,
      declaredModules = emptySet(),
      requiredDependencies = setOf(includedModule),
      incompatiblePlugins = emptySet(),
      optionalDependencies = emptyList(),
      mainModule = mainModule,
      pluginLayouts = listOf(layout),
    )

    val compatible = isPluginCompatible(
      plugin = descriptor,
      availableModulesAndPlugins = HashSet(),
      nonCheckedModules = HashMap(),
      bundledPluginIds = emptySet(),
    )

    assertThat(compatible).isTrue()
  }
}
