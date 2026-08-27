// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.dev.PrepackedPluginContentJar
import org.junit.jupiter.api.Test

internal class PrepackedPluginContentTest {
  private val relation = PrepackedPluginContentJar(
    pluginMainModule = "intellij.plugin",
    contentModule = "intellij.plugin.content",
    relativeOutputFile = "modules/intellij.plugin.content.jar",
  )

  @Test
  fun `accepts an unchanged module at the expected path`() {
    assertThatCode {
      validatePrepackedPluginContentHandoff(
        expected = relation,
        actualRelativeOutputFile = relation.relativeOutputFile,
        hasModuleExclusions = false,
        hasPatchedOutput = false,
        hasInMemoryDescriptor = false,
        hasGeneratedSearchableOptions = false,
        hasSeparateLibraryJar = false,
        hasLayoutPlacedModuleLibrary = false,
        isTestPluginModule = false,
      )
    }.doesNotThrowAnyException()
  }

  @Test
  fun `rejects a different JarPackager placement`() {
    assertThatThrownBy {
      validatePrepackedPluginContentHandoff(
        expected = relation,
        actualRelativeOutputFile = "intellij.plugin.content.jar",
        hasModuleExclusions = false,
        hasPatchedOutput = false,
        hasInMemoryDescriptor = false,
        hasGeneratedSearchableOptions = false,
        hasSeparateLibraryJar = false,
        hasLayoutPlacedModuleLibrary = false,
        isTestPluginModule = false,
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("expected 'modules/intellij.plugin.content.jar'")
      .hasMessageContaining("selected 'intellij.plugin.content.jar'")
  }

  @Test
  fun `rejects transformations owned by JarPackager`() {
    assertRejected("module exclusions", hasModuleExclusions = true)
    assertRejected("patched module output", hasPatchedOutput = true)
    assertRejected("generated searchable options", hasGeneratedSearchableOptions = true)
  }

  /**
   * The plugin's main jar takes its `META-INF/plugin.xml` from `patchPluginXml`, which computes the text during the
   * assembly. A packing action holds no such source, so the handed-off jar would ship with no descriptor at all. Every
   * main jar of this product carries one, so the refusal is about a real population.
   */
  @Test
  fun `rejects a jar that receives a computed descriptor`() {
    assertRejected("which receives a computed META-INF/plugin.xml", hasInMemoryDescriptor = true)
  }

  /**
   * The cases a byte comparison of the module jar cannot see: the separated library jar is a *sibling* the handoff never
   * writes, and a test plugin module is packed from a different output root altogether.
   */
  @Test
  fun `rejects a module whose jar is not the whole story`() {
    assertRejected("library jar packed beside the module jar", hasSeparateLibraryJar = true)
    assertRejected("module library placed by the plugin layout", hasLayoutPlacedModuleLibrary = true)
    assertRejected("is a test plugin module", isTestPluginModule = true)
  }

  private fun assertRejected(
    message: String,
    hasModuleExclusions: Boolean = false,
    hasPatchedOutput: Boolean = false,
    hasInMemoryDescriptor: Boolean = false,
    hasGeneratedSearchableOptions: Boolean = false,
    hasSeparateLibraryJar: Boolean = false,
    hasLayoutPlacedModuleLibrary: Boolean = false,
    isTestPluginModule: Boolean = false,
  ) {
    assertThatThrownBy {
      validatePrepackedPluginContentHandoff(
        expected = relation,
        actualRelativeOutputFile = relation.relativeOutputFile,
        hasModuleExclusions = hasModuleExclusions,
        hasPatchedOutput = hasPatchedOutput,
        hasInMemoryDescriptor = hasInMemoryDescriptor,
        hasGeneratedSearchableOptions = hasGeneratedSearchableOptions,
        hasSeparateLibraryJar = hasSeparateLibraryJar,
        hasLayoutPlacedModuleLibrary = hasLayoutPlacedModuleLibrary,
        isTestPluginModule = isTestPluginModule,
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(message)
  }
}
