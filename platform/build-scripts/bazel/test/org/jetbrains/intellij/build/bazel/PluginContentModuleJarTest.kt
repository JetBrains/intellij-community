// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class PluginContentModuleJarTest {
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
}
