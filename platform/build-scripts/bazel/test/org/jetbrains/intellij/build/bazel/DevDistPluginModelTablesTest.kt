// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.writeText

internal class DevDistPluginModelTablesTest {
  @JvmField
  @Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `one file states the three tables`() {
    val tables = read(
      "# a comment\n" +
      "\n" +
      "[$CONTENT_VETOES_SECTION]\n" +
      "intellij.vetoed\n" +
      "\n" +
      "[$CONTENT_CANDIDATE_OVERRIDES_SECTION]\n" +
      "+intellij.plain\n" +
      "+intellij.merging first second\n" +
      "-intellij.not.a.candidate\n" +
      "\n" +
      "[$DESCRIPTOR_POPULATION_SECTION]\n" +
      "intellij.one.layout\n" +
      "intellij.per.platform/mac\n" +
      "intellij.per.platform/win\n"
    )

    assertEquals(setOf("intellij.vetoed"), tables.contentModuleJarVetoes)
    assertEquals(
      mapOf("intellij.plain" to emptySet(), "intellij.merging" to setOf("first", "second"), "intellij.not.a.candidate" to null),
      tables.contentCandidateOverrides,
    )
    assertEquals(
      mapOf("intellij.one.layout" to listOf(""), "intellij.per.platform" to listOf("mac", "win")),
      tables.descriptorPopulation,
    )
  }

  @Test
  fun `an absent file states nothing`() {
    val tables = readDevDistPluginModelTables(temporaryFolder.root.toPath().resolve("absent.txt"))

    assertEquals(emptySet<String>(), tables.contentModuleJarVetoes)
    assertEquals(emptyMap<String, Set<String>?>(), tables.contentCandidateOverrides)
    assertEquals(emptyMap<String, List<String>>(), tables.descriptorPopulation)
  }

  @Test
  fun `an empty section states an empty table`() {
    val tables = read("[$CONTENT_VETOES_SECTION]\n[$CONTENT_CANDIDATE_OVERRIDES_SECTION]\n[$DESCRIPTOR_POPULATION_SECTION]\n")

    assertEquals(emptySet<String>(), tables.contentModuleJarVetoes)
    assertEquals(emptyMap<String, Set<String>?>(), tables.contentCandidateOverrides)
    assertEquals(emptyMap<String, List<String>>(), tables.descriptorPopulation)
  }

  @Test
  fun `a row this reader cannot state is an error`() {
    // A present file is read strictly, because each of these changes how a module is packed or whether a plugin gets a
    // leaf, and neither is noticed until class-load time. The expected clause differs per row, because every message
    // starts with the file name and asserting on that would pass for any of them.
    for ((text, clause) in listOf(
      "[$CONTENT_CANDIDATE_OVERRIDES_SECTION]\nintellij.unsigned\n" to "a row must start with",
      "[$CONTENT_CANDIDATE_OVERRIDES_SECTION]\n+\n" to "a row must name a module",
      "[$CONTENT_CANDIDATE_OVERRIDES_SECTION]\n-intellij.vetoed with-a-library\n" to "a `-` row records no library",
      // A section the producer renamed and this reader does not know. Dropping it silently would state a whole table
      // the other way, which is the class of defect one declared file exists to remove.
      "[content_veto]\nintellij.vetoed\n" to "is not a section of this file",
      "intellij.vetoed\n" to "is above the first section header",
    )) {
      val failure = assertThrows(IllegalStateException::class.java) { read(text) }
      assertTrue(failure.message, failure.message!!.contains(clause))
    }
  }

  private fun read(text: String): DevDistPluginModelTables {
    val file = temporaryFolder.root.toPath().resolve(PLUGIN_MODEL_TABLES_FILE_NAME)
    file.writeText(text)
    return readDevDistPluginModelTables(file)
  }
}
