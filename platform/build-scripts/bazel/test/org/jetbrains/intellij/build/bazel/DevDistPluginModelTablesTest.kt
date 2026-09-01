// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `one file states the five tables`() {
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
      "intellij.per.platform/win\n" +
      "\n" +
      "[$PLUGIN_JAR_PLACEMENT_SECTION]\n" +
      "intellij.renamed\tRenamed Plugin\trenamed.jar\n" +
      "\n" +
      "[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\n" +
      "intellij.deviating\tdescriptor\tMETA-INF/Extra.xml\tcommunity/demo/resources/META-INF/Extra.xml\n" +
      "intellij.deviating\tlibrary_descriptor\tMETA-INF/lib.xml\tintellij.libraries.demo\tdemo-library\n" +
      "intellij.deviating\trefused_content_module\tintellij.deviating/refused\n" +
      "intellij.deviating\tseparate_jar\tintellij.deviating/apart\n" +
      "intellij.deviating\tmarker\tmarker:<!-- PLACEHOLDER -->:<incompatible-with>a b</incompatible-with>\n" +
      "intellij.deviating\tversion_suffix\t-IJ\n" +
      "intellij.deviating\tno_embedding\n" +
      "intellij.deviating\texact_version\n" +
      "intellij.deviating\tretain_product_descriptor\n" +
      "intellij.per.platform/mac\tseparate_jar\tintellij.per.platform/apart\n"
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
    // A directory holding a space is why the row is tab separated: `intellij.javaee.jpa.jpb.model` places `JPA Model`.
    val placement = tables.pluginJarPlacement.getValue("intellij.renamed")
    assertEquals("Renamed Plugin", placement.directory)
    assertEquals("renamed.jar", placement.mainJarName)
    // Every field of one key, from nine rows, and the second key states that a variant carries its own residue.
    val residue = tables.pluginDescriptorResidue.getValue("intellij.deviating").getValue("intellij.deviating")
    assertEquals(listOf("META-INF/Extra.xml"), residue.descriptors.map { it.loadPath })
    assertEquals(listOf("community/demo/resources/META-INF/Extra.xml"), residue.descriptors.map { it.path })
    assertEquals(listOf("intellij.libraries.demo"), residue.libraryDescriptors.map { it.module })
    assertEquals(listOf("demo-library"), residue.libraryDescriptors.map { it.library })
    assertEquals(listOf("intellij.deviating/refused"), residue.refusedContentModules)
    assertEquals(listOf("intellij.deviating/apart"), residue.separateJar)
    assertEquals(listOf("marker:<!-- PLACEHOLDER -->:<incompatible-with>a b</incompatible-with>"), residue.markers)
    assertEquals("-IJ", residue.versionSuffix)
    assertTrue(residue.noEmbedding)
    assertTrue(residue.exactVersion)
    assertTrue(residue.retainProductDescriptor)
    assertEquals(
      listOf("intellij.per.platform/apart"),
      tables.pluginDescriptorResidue.getValue("intellij.per.platform").getValue("intellij.per.platform/mac").separateJar,
    )
  }

  @Test
  fun `a key of one flag row states that flag and every other default`() {
    // A key states one row per fact, so most keys state a few of the nine fields. Every other field has to read as the
    // default a key the section does not name reads as, or a plugin would lose a row the patch needs.
    val tables = read("[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\nintellij.scrambled\tno_embedding\n")

    val residue = tables.pluginDescriptorResidue.getValue("intellij.scrambled").getValue("intellij.scrambled")
    assertTrue(residue.noEmbedding)
    assertEquals(emptyList<DescriptorRow>(), residue.descriptors)
    assertEquals(emptyList<String>(), residue.refusedContentModules)
    assertEquals(emptyList<String>(), residue.separateJar)
    assertEquals(emptyList<String>(), residue.markers)
    assertEquals("", residue.versionSuffix)
    assertFalse(residue.exactVersion)
    assertFalse(residue.retainProductDescriptor)
  }

  @Test
  fun `a plugin with no row takes the convention`() {
    val placement = pluginJarPlacementConvention("intellij.clouds.docker.impl")

    assertEquals("clouds-docker-impl", placement.directory)
    assertEquals("clouds-docker-impl.jar", placement.mainJarName)
  }

  @Test
  fun `an absent file states nothing`() {
    val tables = readDevDistPluginModelTables(temporaryFolder.root.toPath().resolve("absent.txt"))

    assertEquals(emptySet<String>(), tables.contentModuleJarVetoes)
    assertEquals(emptyMap<String, Set<String>?>(), tables.contentCandidateOverrides)
    assertEquals(emptyMap<String, List<String>>(), tables.descriptorPopulation)
    assertEquals(emptyMap<String, PluginJarPlacement>(), tables.pluginJarPlacement)
    assertEquals(emptyMap<String, Map<String, DescriptorResidueSection>>(), tables.pluginDescriptorResidue)
  }

  @Test
  fun `an empty section states an empty table`() {
    val tables = read(
      "[$CONTENT_VETOES_SECTION]\n[$CONTENT_CANDIDATE_OVERRIDES_SECTION]\n" +
      "[$DESCRIPTOR_POPULATION_SECTION]\n[$PLUGIN_JAR_PLACEMENT_SECTION]\n" +
      "[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\n"
    )

    assertEquals(emptySet<String>(), tables.contentModuleJarVetoes)
    assertEquals(emptyMap<String, Set<String>?>(), tables.contentCandidateOverrides)
    assertEquals(emptyMap<String, List<String>>(), tables.descriptorPopulation)
    assertEquals(emptyMap<String, PluginJarPlacement>(), tables.pluginJarPlacement)
    assertEquals(emptyMap<String, Map<String, DescriptorResidueSection>>(), tables.pluginDescriptorResidue)
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
      // A two-token placement row would read the jar name as absent, and the recipe would then name a jar the
      // distribution does not hold.
      "[$PLUGIN_JAR_PLACEMENT_SECTION]\nintellij.renamed\tRenamed\n" to "a row states",
      "[$PLUGIN_JAR_PLACEMENT_SECTION]\nintellij.renamed\tRenamed\tone.jar\nintellij.renamed\tOther\ttwo.jar\n" to
        "has two placement rows",
      // A field the writer renamed and this reader does not know. The two spellings are in two Bazel modules, so no
      // compiler pins them to each other and a dropped field would be a descriptor row the patch never reads.
      "[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\nintellij.deviating\tdirectory_name\tRenamed\n" to
        "is not a field of this section",
      // A `descriptor` row with one token would read the answering file as absent, and the include would go unfollowed.
      "[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\nintellij.deviating\tdescriptor\tMETA-INF/Extra.xml\n" to
        "takes 2 tokens after the field name",
      // A flag row with a token is the same class of mistake read from the other side.
      "[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\nintellij.deviating\tno_embedding\ttrue\n" to
        "takes 0 tokens after the field name",
      "[$PLUGIN_DESCRIPTOR_RESIDUE_SECTION]\nintellij.deviating\n" to "a row states",
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
