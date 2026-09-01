// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.dev.PrepackedPluginContentJar
import org.junit.jupiter.api.Test

internal class PrepackedPluginContentTest {
  private val relation = PrepackedPluginContentJar(
    pluginMainModule = "intellij.plugin",
    contentModules = listOf("intellij.plugin.content"),
    relativeOutputFile = "modules/intellij.plugin.content.jar",
  )

  @Test
  fun `accepts the member the relation names`() {
    assertThatCode {
      validatePrepackedPluginContentHandoff(
        expected = relation,
        actualContentModule = relation.contentModules.single(),
        hasModuleExclusions = false,
        hasPatchedOutput = false,
        hasPatchedDescriptor = false,
        hasGeneratedSearchableOptions = false,
        hasSeparateLibraryJar = false,
        hasLayoutPlacedModuleLibrary = false,
        isTestPluginModule = false,
      )
    }.doesNotThrowAnyException()
  }

  /**
   * The relation is found by its destination, so a second member of the same plugin can arrive at it. The packed jar
   * holds the member the relation names, so such a hand-off would drop one module and pack another one twice.
   */
  @Test
  fun `rejects another member of the plugin at this destination`() {
    assertThatThrownBy {
      validatePrepackedPluginContentHandoff(
        expected = relation,
        actualContentModule = "intellij.plugin.other",
        hasModuleExclusions = false,
        hasPatchedOutput = false,
        hasPatchedDescriptor = false,
        hasGeneratedSearchableOptions = false,
        hasSeparateLibraryJar = false,
        hasLayoutPlacedModuleLibrary = false,
        isTestPluginModule = false,
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("intellij.plugin/modules/intellij.plugin.content.jar")
      .hasMessageContaining("packs [intellij.plugin.content]")
      .hasMessageContaining("offered 'intellij.plugin.other'")
  }

  /**
   * A jar the plugin's own layout names holds several members, and the assembly offers them one at a time. So the
   * hand-off asks for membership, and `JarPackager.validatePrepackedPluginContent` is what then asserts that the whole
   * member list arrived.
   */
  @Test
  fun `accepts every member of a jar the relation names several members of`() {
    val shared = PrepackedPluginContentJar(
      pluginMainModule = "intellij.plugin",
      contentModules = listOf("intellij.plugin.rt", "intellij.plugin.rt.impl"),
      relativeOutputFile = "specifics/plugin-rt.jar",
    )
    for (member in shared.contentModules) {
      assertThatCode {
        validatePrepackedPluginContentHandoff(
          expected = shared,
          actualContentModule = member,
          hasModuleExclusions = false,
          hasPatchedOutput = false,
          hasPatchedDescriptor = false,
          hasGeneratedSearchableOptions = false,
          hasSeparateLibraryJar = false,
          hasLayoutPlacedModuleLibrary = false,
          isTestPluginModule = false,
        )
      }.doesNotThrowAnyException()
    }
    assertThatThrownBy {
      validatePrepackedPluginContentHandoff(
        expected = shared,
        actualContentModule = "intellij.plugin.other",
        hasModuleExclusions = false,
        hasPatchedOutput = false,
        hasPatchedDescriptor = false,
        hasGeneratedSearchableOptions = false,
        hasSeparateLibraryJar = false,
        hasLayoutPlacedModuleLibrary = false,
        isTestPluginModule = false,
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("packs [intellij.plugin.rt, intellij.plugin.rt.impl]")
  }

  @Test
  fun `rejects transformations owned by JarPackager`() {
    assertRejected("module exclusions", hasModuleExclusions = true)
    assertRejected("patched module output", hasPatchedOutput = true)
    assertRejected("generated searchable options", hasGeneratedSearchableOptions = true)
  }

  /**
   * The plugin's main jar takes its `META-INF/plugin.xml` through `computeSourcesForModule`, and a hand-off is the case
   * where that function never runs. So the handed-off jar would ship with no descriptor at all. Every main jar of this
   * product carries one, so the refusal is about a real population.
   *
   * One boolean covers both patch channels on purpose. Whether the text was computed into memory or produced into a
   * file changes nothing here: the hand-off drops the source either way.
   */
  @Test
  fun `rejects a jar that receives a patched descriptor`() {
    assertRejected("which receives a patched META-INF/plugin.xml", hasPatchedDescriptor = true)
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

  /**
   * The other half of the hand-off, which only a whole layout can answer. The three cases are the three ways a relation
   * and a layout can disagree once every member has been offered.
   */
  @Test
  fun `rejects a relation the layout never reached, and a member it never offered`() {
    val shared = PrepackedPluginContentJar(
      pluginMainModule = "intellij.plugin",
      contentModules = listOf("intellij.plugin.rt", "intellij.plugin.rt.impl"),
      relativeOutputFile = "specifics/plugin-rt.jar",
    )
    val expected = mapOf(shared.key to shared)

    assertThatCode {
      validatePrepackedPluginContentClaims(
        pluginMainModule = shared.pluginMainModule,
        expected = expected,
        claimed = mapOf(shared.key to shared.contentModules),
      )
    }.doesNotThrowAnyException()

    // A stale destination: the layout moved the jar, so no offer ever found the relation.
    assertThatThrownBy {
      validatePrepackedPluginContentClaims(pluginMainModule = shared.pluginMainModule, expected = expected, claimed = emptyMap())
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("of intellij.plugin does not match its descriptor/layout")
      .hasMessageContaining("missing [PrepackedPluginContentKey(pluginMainModule=intellij.plugin, relativeOutputFile=specifics/plugin-rt.jar)]")

    // The one failure only this check can see. The packing target merged the member, and the layout packs it into
    // another jar of the same plugin, so the distribution would hold the member twice.
    assertThatThrownBy {
      validatePrepackedPluginContentClaims(
        pluginMainModule = shared.pluginMainModule,
        expected = expected,
        claimed = mapOf(shared.key to listOf("intellij.plugin.rt")),
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("intellij.plugin/specifics/plugin-rt.jar packs [intellij.plugin.rt, intellij.plugin.rt.impl]")
      .hasMessageContaining("the layout offered [intellij.plugin.rt]")
  }

  private fun assertRejected(
    message: String,
    hasModuleExclusions: Boolean = false,
    hasPatchedOutput: Boolean = false,
    hasPatchedDescriptor: Boolean = false,
    hasGeneratedSearchableOptions: Boolean = false,
    hasSeparateLibraryJar: Boolean = false,
    hasLayoutPlacedModuleLibrary: Boolean = false,
    isTestPluginModule: Boolean = false,
  ) {
    assertThatThrownBy {
      validatePrepackedPluginContentHandoff(
        expected = relation,
        actualContentModule = relation.contentModules.single(),
        hasModuleExclusions = hasModuleExclusions,
        hasPatchedOutput = hasPatchedOutput,
        hasPatchedDescriptor = hasPatchedDescriptor,
        hasGeneratedSearchableOptions = hasGeneratedSearchableOptions,
        hasSeparateLibraryJar = hasSeparateLibraryJar,
        hasLayoutPlacedModuleLibrary = hasLayoutPlacedModuleLibrary,
        isTestPluginModule = isTestPluginModule,
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(message)
  }
}
