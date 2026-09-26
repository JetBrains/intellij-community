// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal class JpsModuleOutputProviderStateTest {
  @Test
  fun `reused JPS provider state creates distinct providers with shared module lookups`() {
    val module = mock(JpsModule::class.java)
    `when`(module.name).thenReturn("intellij.test.module")

    val project = mock(JpsProject::class.java)
    `when`(project.modules).thenReturn(listOf(module))

    val state = JpsModuleOutputProviderState(project)
    val productionProvider = state.createProvider(useTestCompilationOutput = false)
    val testProvider = state.createProvider(useTestCompilationOutput = true)

    assertDistinctProvidersWithSharedLookups(productionProvider, testProvider, module)
  }

  private fun assertDistinctProvidersWithSharedLookups(
    productionProvider: ModuleOutputProvider,
    testProvider: ModuleOutputProvider,
    module: JpsModule,
  ) {
    assertThat(productionProvider).isNotSameAs(testProvider)
    assertThat(productionProvider.useTestCompilationOutput).isFalse()
    assertThat(testProvider.useTestCompilationOutput).isTrue()
    assertThat(productionProvider.findRequiredModule(module.name)).isSameAs(module)
    assertThat(testProvider.findRequiredModule(module.name)).isSameAs(module)
    assertThat(productionProvider.getAllModules()).containsExactly(module)
    assertThat(testProvider.getAllModules()).containsExactly(module)
  }
}
