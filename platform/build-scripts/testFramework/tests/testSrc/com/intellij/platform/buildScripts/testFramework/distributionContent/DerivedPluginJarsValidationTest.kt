// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.ModuleLibraryFile
import com.intellij.platform.distributionContent.PluginContentReport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** [compareDerivedPluginJars] over hand-built reports and derived records, one case per rule the comparison states. */
class DerivedPluginJarsValidationTest {
  private val plugin = "intellij.sample"

  private fun record(vararg jars: DerivedJar): Map<String, List<DerivedJar>> = mapOf(plugin to jars.toList())

  private fun report(vararg entries: FileEntry, os: String? = null): PluginContentReport {
    return PluginContentReport(mainModule = plugin, os = os, content = entries.toList())
  }

  private fun module(name: String, vararg libraries: String): ModuleEntry {
    return ModuleEntry(name = name, libraries = libraries.associateWith { listOf(ModuleLibraryFile(name = "$it.jar")) })
  }

  private fun jar(path: String, vararg members: String, libraries: List<String>? = null): DerivedJar {
    return DerivedJar(relativeOutputFile = path, members = members.toList(), libraries = libraries)
  }

  @Test
  fun `identical jars report nothing`() {
    val comparison = compareDerivedPluginJars(
      records = record(
        jar("sample.jar", plugin),
        jar("modules/intellij.sample.a.jar", "intellij.sample.a", libraries = listOf("lib-a")),
      ),
      reports = listOf(report(
        FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))),
        FileEntry(name = "lib/modules/intellij.sample.a.jar", contentModules = listOf(module("intellij.sample.a", "lib-a"))),
        // A bare library jar names no module, so the derivation states none and the comparison skips it.
        FileEntry(name = "lib/some-library.jar", library = "some-library"),
      )),
    )
    assertThat(comparison.failures).isEmpty()
    assertThat(comparison.measured).isEmpty()
    assertThat(comparison.unrecorded).isEmpty()
  }

  @Test
  fun `the members of a jar are compared as one set whichever list the report puts them in`() {
    val comparison = compareDerivedPluginJars(
      records = record(jar("sample.jar", "intellij.sample.a", plugin)),
      reports = listOf(report(FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin), module("intellij.sample.a"))))),
    )
    assertThat(comparison.failures).isEmpty()
  }

  @Test
  fun `a plugin with no row is unrecorded, not a failure, and leaves the table alone`() {
    val comparison = compareDerivedPluginJars(
      records = emptyMap(),
      reports = listOf(report(FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))))),
      divergences = mapOf(plugin to setOf("sample.jar")),
    )
    assertThat(comparison.failures).isEmpty()
    assertThat(comparison.unrecorded).containsExactly(plugin)
    assertThat(comparison.measured).isEqualTo(mapOf(plugin to setOf("sample.jar")))
  }

  @Test
  fun `a packed jar the derivation does not state is reported`() {
    val comparison = compareDerivedPluginJars(
      records = record(jar("sample.jar", plugin)),
      reports = listOf(report(
        FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))),
        FileEntry(name = "lib/extra.jar", modules = listOf(module("intellij.sample.extra"))),
      )),
    )
    assertThat(comparison.failures.single().error.message).contains("packed, not derived: extra.jar [intellij.sample.extra]")
    assertThat(comparison.measured).isEqualTo(mapOf(plugin to setOf("extra.jar")))
  }

  @Test
  fun `a derived jar packed nowhere in this product is reported unless its members are packed elsewhere`() {
    val records = record(
      jar("sample.jar", plugin),
      jar("sample-frontend.jar", "intellij.sample.frontend"),
    )
    // Another product's jar: the member sits in this product's main jar, so the absent jar is held out. The main jar
    // then holds one member more than derived, and that is what is reported.
    val heldOut = compareDerivedPluginJars(
      records = records,
      reports = listOf(report(FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin)), contentModules = listOf(module("intellij.sample.frontend"))))),
    )
    assertThat(heldOut.failures.single().error.message)
      .contains("sample.jar: members only derived: ; only packed: intellij.sample.frontend")
      .doesNotContain("derived, not packed")
    // The member is packed nowhere here, so the absent jar is reported.
    val reported = compareDerivedPluginJars(
      records = records,
      reports = listOf(report(FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))))),
    )
    assertThat(reported.failures.single().error.message).contains("derived, not packed: sample-frontend.jar [intellij.sample.frontend]")
  }

  @Test
  fun `a wrapper module counts as packed where its library is a bare jar`() {
    val reports = listOf(report(
      FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))),
      FileEntry(name = "lib/x.jar", library = "x", module = "intellij.libraries.x"),
    ))
    // The derivation gives the wrapper a jar of its own, and the build wrote only its library.
    assertThat(compareDerivedPluginJars(
      records = record(jar("sample.jar", plugin), jar("modules/intellij.libraries.x.jar", "intellij.libraries.x")),
      reports = reports,
    ).failures).isEmpty()
    // The derivation co-packs the wrapper into the main jar, and the build wrote only its library.
    assertThat(compareDerivedPluginJars(
      records = record(jar("sample.jar", "intellij.libraries.x", plugin)),
      reports = reports,
    ).failures).isEmpty()
  }

  @Test
  fun `a library difference is reported only where the record states the set`() {
    val comparison = compareDerivedPluginJars(
      records = record(
        jar("sample.jar", plugin),
        jar("modules/intellij.sample.a.jar", "intellij.sample.a", libraries = listOf("lib-a")),
      ),
      reports = listOf(report(
        FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin, "main-lib"))),
        FileEntry(name = "lib/modules/intellij.sample.a.jar", contentModules = listOf(module("intellij.sample.a", "lib-a", "lib-b"))),
      )),
    )
    assertThat(comparison.failures.single().error.message)
      .contains("modules/intellij.sample.a.jar: libraries only derived: ; only packed: lib-b")
      .doesNotContain("main-lib")
  }

  @Test
  fun `a known divergence is measured and not reported`() {
    val comparison = compareDerivedPluginJars(
      records = record(jar("sample.jar", plugin)),
      reports = listOf(report(
        FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))),
        FileEntry(name = "lib/extra.jar", modules = listOf(module("intellij.sample.extra"))),
      )),
      divergences = mapOf(plugin to setOf("extra.jar")),
    )
    assertThat(comparison.failures).isEmpty()
    assertThat(comparison.measured).isEqualTo(mapOf(plugin to setOf("extra.jar")))
  }

  @Test
  fun `per-OS variants of one plugin are unioned before the comparison`() {
    val comparison = compareDerivedPluginJars(
      records = record(
        jar("sample.jar", plugin),
        jar("modules/intellij.sample.mac.jar", "intellij.sample.mac"),
      ),
      reports = listOf(
        report(FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))), os = "linux"),
        report(
          FileEntry(name = "lib/sample.jar", modules = listOf(module(plugin))),
          FileEntry(name = "lib/modules/intellij.sample.mac.jar", contentModules = listOf(module("intellij.sample.mac"))),
          os = "macos",
        ),
      ),
    )
    assertThat(comparison.failures).isEmpty()
  }

  private val table = """
    |# the class of the first plugin
    |[intellij.a]
    |a.jar
    |modules/intellij.a.extra.jar
    |
    |# the class of the second plugin
    |[intellij.b]
    |b.jar
    |""".trimMargin()

  @Test
  fun `a table states its rows and the comment block of each section`() {
    val parsed = readDivergenceTable(table)
    assertThat(parsed.jarsByPlugin).isEqualTo(mapOf(
      "intellij.a" to setOf("a.jar", "modules/intellij.a.extra.jar"),
      "intellij.b" to setOf("b.jar"),
    ))
    assertThat(parsed.comments).isEqualTo(mapOf(
      "intellij.a" to listOf("# the class of the first plugin"),
      "intellij.b" to listOf("# the class of the second plugin"),
    ))
  }

  @Test
  fun `the measured divergences of a table write it back unchanged`() {
    val parsed = readDivergenceTable(table)
    assertThat(writeDivergenceTable(comments = parsed.comments, divergences = parsed.jarsByPlugin)).isEqualTo(table)
  }

  @Test
  fun `a row the run no longer measures leaves the table, and the last row takes its comment block with it`() {
    val parsed = readDivergenceTable(table)
    val written = writeDivergenceTable(comments = parsed.comments, divergences = mapOf("intellij.a" to setOf("a.jar")))
    assertThat(written).isEqualTo("""
      |# the class of the first plugin
      |[intellij.a]
      |a.jar
      |""".trimMargin())
  }

  @Test
  fun `a new divergence gets a row of its own, and the comment block of every other section stays`() {
    val parsed = readDivergenceTable(table)
    val written = writeDivergenceTable(
      comments = parsed.comments,
      divergences = parsed.jarsByPlugin + mapOf("intellij.c" to setOf("c.jar")),
    )
    assertThat(written).isEqualTo(table + """
      |
      |[intellij.c]
      |c.jar
      |""".trimMargin())
  }

  @Test
  fun `an absent table is an empty one, and one measured row writes the first section`() {
    val parsed = readDivergenceTable("")
    assertThat(parsed.jarsByPlugin).isEmpty()
    assertThat(writeDivergenceTable(comments = parsed.comments, divergences = mapOf(plugin to setOf("sample.jar"))))
      .isEqualTo("[$plugin]\nsample.jar\n")
  }

  @Test
  fun `the sections and the rows of one measurement are written in name order`() {
    val written = writeDivergenceTable(
      comments = emptyMap(),
      divergences = linkedMapOf(
        "intellij.b" to linkedSetOf("modules/z.jar", "a.jar"),
        "intellij.a" to setOf("a.jar"),
      ),
    )
    assertThat(written).isEqualTo("[intellij.a]\na.jar\n\n[intellij.b]\na.jar\nmodules/z.jar\n")
  }
}
