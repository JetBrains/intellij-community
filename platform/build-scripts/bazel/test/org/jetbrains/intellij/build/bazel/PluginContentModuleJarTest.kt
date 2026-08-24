// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class PluginContentModuleJarTest {
  @JvmField
  @Rule
  val tempDir: TemporaryFolder = TemporaryFolder()

  @Test
  fun `simple plugin content module is eligible`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )

    assertEquals("intellij.example", simplePluginContentModuleName(entry))
  }

  @Test
  fun `descriptor suffix is not part of the module name`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example/alternative")),
    )

    assertEquals("intellij.example", simplePluginContentModuleName(entry))
  }

  @Test
  fun `merged content is ineligible`() {
    val contentModule = RecipeModule(name = "intellij.example")
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule, RecipeModule(name = "intellij.other")),
    )))
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      modules = listOf(RecipeModule(name = "intellij.other")),
    )))
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      projectLibraries = listOf(RecipeNamed(name = "example-library")),
    )))
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      library = "example-library",
    )))
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      module = "intellij.other",
    )))
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule.copy(libraries = mapOf("runtime" to emptyList()))),
    )))
  }

  @Test
  fun `noncanonical destination is ineligible`() {
    assertNull(simplePluginContentModuleName(RecipeEntry(
      name = "lib/other.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )))
  }

  @Test
  fun `candidate index accepts identical occurrences and rejects a conflicting one`() {
    writeReport(
      directory = "first",
      text = """
        - name: lib/modules/intellij.shared.jar
          contentModules:
            - name: intellij.shared
        - name: lib/modules/intellij.conflicting.jar
          contentModules:
            - name: intellij.conflicting
      """,
    )
    writeReport(
      directory = "second",
      text = """
        - name: lib/modules/intellij.shared.jar
          contentModules:
            - name: intellij.shared
        - name: lib/combined.jar
          contentModules:
            - name: intellij.conflicting
            - name: intellij.other
      """,
    )

    assertEquals(setOf("intellij.shared"), indexPluginContentModuleJarCandidates(tempDir.root.toPath()))
  }

  private fun writeReport(directory: String, text: String) {
    tempDir.root.toPath().resolve(directory).createDirectories().resolve("plugin-content.yaml").writeText(text.trimIndent())
  }
}
