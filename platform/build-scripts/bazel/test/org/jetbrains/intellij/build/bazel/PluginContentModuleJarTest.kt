// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.writeText

internal class PluginContentModuleJarTest {
  @Test
  fun `simple plugin content module is eligible`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )

    val simple = simplePluginContentEntry(entry)
    assertEquals("intellij.example", simple?.moduleName)
    assertEquals(emptySet<String>(), simple?.libraries)
  }

  @Test
  fun `descriptor suffix is not part of the module name`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example/alternative")),
    )

    assertEquals("intellij.example", simplePluginContentEntry(entry)?.moduleName)
  }

  @Test
  fun `merged module libraries are recorded rather than vetoed`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(
        name = "intellij.example",
        libraries = mapOf("first" to emptyList(), "second" to emptyList()),
      )),
    )

    val simple = simplePluginContentEntry(entry)
    assertEquals("intellij.example", simple?.moduleName)
    assertEquals(setOf("first", "second"), simple?.libraries)
  }

  @Test
  fun `merged content is ineligible`() {
    val contentModule = RecipeModule(name = "intellij.example")
    assertNull(simplePluginContentEntry(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule, RecipeModule(name = "intellij.other")),
    )))
    assertNull(simplePluginContentEntry(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      modules = listOf(RecipeModule(name = "intellij.other")),
    )))
    // A project library is merged only by an `auto` PluginLayout, which no report states; see `simplePluginContentEntry`.
    assertNull(simplePluginContentEntry(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      projectLibraries = listOf(RecipeNamed(name = "example-library")),
    )))
    assertNull(simplePluginContentEntry(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      library = "example-library",
    )))
    assertNull(simplePluginContentEntry(RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(contentModule),
      module = "intellij.other",
    )))
  }

  @Test
  fun `noncanonical destination is ineligible`() {
    assertNull(simplePluginContentEntry(RecipeEntry(
      name = "lib/other.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )))
  }

  @Test
  fun `an os-conditional entry is ineligible`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )

    assertNull(simplePluginContentEntry(entry.copy(os = "mac")))
    assertNull(simplePluginContentEntry(entry.copy(arch = "aarch64")))
    assertNull(simplePluginContentEntry(entry.copy(libc = "musl")))
  }

  @Test
  fun `occurrences that agree on the libraries are eligible with them`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example", libraries = mapOf("first" to emptyList()))),
    )

    val candidates = foldPluginContentCandidacy(
      reports = listOf(listOf(entry), listOf(entry)),
      overrides = emptyMap(),
    )

    assertEquals(mapOf("intellij.example" to setOf("first")), candidates)
  }

  @Test
  fun `occurrences that disagree on the libraries are vetoed`() {
    val one = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example", libraries = mapOf("first" to emptyList()))),
    )
    val other = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(
        name = "intellij.example",
        libraries = mapOf("first" to emptyList(), "second" to emptyList()),
      )),
    )

    // Both orders, because the fold reads the reports in whatever order the model hands them over.
    assertEquals(emptyMap<String, Set<String>>(), foldPluginContentCandidacy(listOf(listOf(one), listOf(other)), emptyMap()))
    assertEquals(emptyMap<String, Set<String>>(), foldPluginContentCandidacy(listOf(listOf(other), listOf(one)), emptyMap()))
  }

  @Test
  fun `a taken-out library vetoes its owner whatever the report order`() {
    val simple = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )
    val takenOut = RecipeEntry(name = "lib/example-agent.jar", library = "example-agent", module = "intellij.example")

    assertEquals(emptyMap<String, Set<String>>(), foldPluginContentCandidacy(listOf(listOf(simple, takenOut)), emptyMap()))
    assertEquals(emptyMap<String, Set<String>>(), foldPluginContentCandidacy(listOf(listOf(takenOut, simple)), emptyMap()))
  }

  @JvmField
  @Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `an override line names a module and its libraries`() {
    val overrides = readOverrides(
      "# a comment\n" +
      "\n" +
      "+intellij.plain\n" +
      "+intellij.merging first second\n" +
      "-intellij.vetoed\n"
    )

    assertEquals(mapOf("intellij.plain" to emptySet(), "intellij.merging" to setOf("first", "second"), "intellij.vetoed" to null), overrides)
  }

  @Test
  fun `an absent overrides file records nothing`() {
    assertEquals(emptyMap<String, Set<String>?>(), readPluginContentCandidateOverrides(temporaryFolder.root.toPath().resolve("absent.txt")))
  }

  @Test
  fun `a line this reader cannot state is an error`() {
    // Each of these would otherwise change how a module is packed, and a jar that differs from the distribution's is
    // not noticed until class-load time. The expected clause differs per line, because every message starts with the
    // file name and asserting on that would pass for any of the three.
    for ((line, clause) in listOf(
      "intellij.unsigned" to "a line must start with",
      "+" to "a line must name a module",
      "-intellij.vetoed with-a-library" to "a `-` line records no library",
    )) {
      val failure = assertThrows(IllegalStateException::class.java) { readOverrides(line + "\n") }
      assertTrue(failure.message, failure.message!!.contains(clause))
    }
  }

  private fun readOverrides(text: String): Map<String, Set<String>?> {
    val file = temporaryFolder.root.toPath().resolve(PLUGIN_CONTENT_CANDIDATE_OVERRIDES_FILE_NAME)
    file.writeText(text)
    return readPluginContentCandidateOverrides(file)
  }

  @Test
  fun `an override decides both directions and carries the libraries`() {
    val entry = RecipeEntry(
      name = "lib/modules/intellij.example.jar",
      contentModules = listOf(RecipeModule(name = "intellij.example")),
    )

    assertEquals(
      emptyMap<String, Set<String>>(),
      foldPluginContentCandidacy(listOf(listOf(entry)), mapOf("intellij.example" to null)),
    )
    assertEquals(
      mapOf("intellij.other" to setOf("first"), "intellij.example" to emptySet()),
      foldPluginContentCandidacy(listOf(listOf(entry)), mapOf("intellij.other" to setOf("first"))),
    )
  }
}
