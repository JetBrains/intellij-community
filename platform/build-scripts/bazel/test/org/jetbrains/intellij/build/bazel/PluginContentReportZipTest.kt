// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

/**
 * The union [readPluginContentReportZips] performs, over three paths: two products, the two lists of one product's
 * report, and the target-platform variants of one product.
 *
 * The union was the arc's one untested branch. No checked-in report carried `os`, `arch` or `libc`, so nothing exercised
 * it, and the first real report did. These cases state the semantics the real reports then depended on: a member two
 * products pack differently keeps no packing target, and a plugin two products pack identically is not a disagreement.
 *
 * The measured case behind [`a member one product co-packs is vetoed for both`] is the one that matters most. IDEA
 * Ultimate's report alone asks to drop 8 plugins' `vetoed_members` and `raw_members` rows, and the union of seven
 * products keeps every one of them. Those rows are right, and this is the rule that makes them right.
 */
internal class PluginContentReportZipTest {
  @Rule
  @JvmField
  val tempFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `two products unite the entries of one plugin`() {
    val first = writeReportZip(
      "first", ReportedPlugin(
      mainModule = "intellij.example.plugin",
      content = listOf(entryFor("intellij.first")),
    ))
    val second = writeReportZip(
      "second", ReportedPlugin(
      mainModule = "intellij.example.plugin",
      content = listOf(entryFor("intellij.second")),
    ))

    val reports = readPluginContentReportZips(listOf(first, second))

    assertEquals(setOf("intellij.example.plugin"), reports.keys)
    assertEquals(
      listOf("lib/modules/intellij.first.jar", "lib/modules/intellij.second.jar"),
      reports.getValue("intellij.example.plugin").map { it.name },
    )
  }

  @Test
  fun `an entry two products report identically is read once`() {
    val shared = entryFor("intellij.shared")
    val first = writeReportZip("first", ReportedPlugin(mainModule = "intellij.example.plugin", content = listOf(shared)))
    val second = writeReportZip("second", ReportedPlugin(mainModule = "intellij.example.plugin", content = listOf(shared)))

    val reports = readPluginContentReportZips(listOf(first, second))

    // The duplicate collapses, so a plugin several products pack the same way costs nothing and states no disagreement.
    assertEquals(listOf(shared), reports.getValue("intellij.example.plugin"))
  }

  @Test
  fun `per-OS variants of one product unite`() {
    val zip = writeReportZip(
      "one",
      ReportedPlugin(mainModule = "intellij.example.plugin", os = "mac", content = listOf(entryFor("intellij.mac"))),
      ReportedPlugin(mainModule = "intellij.example.plugin", os = "linux", content = listOf(entryFor("intellij.linux"))),
    )

    val reports = readPluginContentReportZips(listOf(zip))

    assertEquals(
      listOf("lib/modules/intellij.linux.jar", "lib/modules/intellij.mac.jar"),
      reports.getValue("intellij.example.plugin").map { it.name }.sorted(),
    )
  }

  @Test
  fun `a plugin in both lists of one zip unites`() {
    val shared = entryFor("intellij.shared")
    val extra = entryFor("intellij.extra")
    // The second union path, and the commoner one. 119 Ultimate plugins and 25 DataGrip plugins stand in the bundled
    // list and in the non-bundled list of their own product's report, so one zip reports one plugin twice.
    val zip = writeReportZip(
      name = "one",
      bundled = listOf(ReportedPlugin(mainModule = "intellij.example.plugin", content = listOf(shared))),
      nonBundled = listOf(ReportedPlugin(mainModule = "intellij.example.plugin", content = listOf(shared, extra))),
    )

    val reports = readPluginContentReportZips(listOf(zip))

    assertEquals(setOf("intellij.example.plugin"), reports.keys)
    assertEquals(listOf(shared, extra), reports.getValue("intellij.example.plugin"))
  }

  @Test
  fun `a member one product co-packs is vetoed for both`() {
    // One product gives the member its own `lib/modules/<module>.jar`, which is a packing target the converter may serve.
    val alone = writeReportZip(
      "alone", ReportedPlugin(
      mainModule = "intellij.first.plugin",
      content = listOf(entryFor("intellij.member")),
    ))
    // The other packs it beside a second content module. `simplePluginContentEntry` refuses that entry, so the fold
    // vetoes every module the entry names.
    val coPacked = writeReportZip(
      "co-packed", ReportedPlugin(
      mainModule = "intellij.second.plugin",
      content = listOf(RecipeEntry(
        name = "lib/modules/intellij.bundle.jar",
        contentModules = listOf(RecipeModule(name = "intellij.member"), RecipeModule(name = "intellij.other")),
      )),
    ))

    val aloneOnly = foldPluginContentCandidacy(
      reports = readPluginContentReportZips(listOf(alone)).values.toList(),
      overrides = emptyMap(),
    )
    val united = foldPluginContentCandidacy(
      reports = readPluginContentReportZips(listOf(alone, coPacked)).values.toList(),
      overrides = emptyMap(),
    )

    // Read alone, the first product's report gives the member a jar. This is the answer a partial read produces, and it
    // is why a residue written from one product drops the row the other product needs.
    assertEquals(emptySet<String>(), aloneOnly.get("intellij.member"))
    // Read together, the member has no packing target at all. The veto wins whatever order the reports arrive in, so
    // adding a product can only take a target away, never invent one.
    assertNull(united.get("intellij.member"))
  }

  @Test
  fun `a report naming no plugin is refused`() {
    val empty = writeRawReportZip("empty", "bundled-plugins.yaml" to "[]", "non-bundled-plugins.yaml" to "[]")

    val failure = assertThrows(IllegalStateException::class.java) { readPluginContentReportZips(listOf(empty)) }
    assertTrue(failure.message, failure.message!!.contains("names no plugin"))
  }

  @Test
  fun `a plugin with no main module is refused`() {
    // The damage the refusal exists for: a renamed field of the platform's `PluginContentReport`. `recipeYaml` runs
    // with `strictMode = false`, so `mainModule` decodes as its default and every plugin arrives unnamed. The writer
    // would then state an empty population and take 356 content leaves with it, and no other check would object.
    val renamed = writeRawReportZip(
      "renamed",
      "bundled-plugins.yaml" to "- pluginMainModule: \"intellij.example.plugin\"\n  content: []\n",
      "non-bundled-plugins.yaml" to "[]",
    )

    val failure = assertThrows(IllegalStateException::class.java) { readPluginContentReportZips(listOf(renamed)) }
    assertTrue(failure.message, failure.message!!.contains("no main module"))
  }

  @Test
  fun `a zip that holds no per-plugin entry is refused`() {
    // A zip of the wrong build step, or of a build whose plugin report step was off. The reader names the entry it
    // wanted, because a zip with no per-plugin entry reads as a report of a product that packs no plugin.
    val other = writeRawReportZip("other", "content-report.yaml" to "[]")

    val failure = assertThrows(IllegalStateException::class.java) { readPluginContentReportZips(listOf(other)) }
    assertTrue(failure.message, failure.message!!.contains("holds no bundled-plugins.yaml"))
  }

  @Test
  fun `naming no report at all is refused`() {
    val failure = assertThrows(IllegalStateException::class.java) { readPluginContentReportZips(emptyList()) }
    assertTrue(failure.message, failure.message!!.contains("--content-report"))
  }

  /** One member at its conventional own jar, which is the shape [simplePluginContentEntry] accepts. */
  private fun entryFor(moduleName: String): RecipeEntry {
    return RecipeEntry(
      name = "lib/modules/$moduleName.jar",
      contentModules = listOf(RecipeModule(name = moduleName)),
    )
  }

  /**
   * A report zip of the shape a distribution build writes, with every plugin bundled.
   *
   * Serialized through `recipeYaml` rather than hand-written yaml, so the fixture cannot drift from the reader's own
   * schema. `ContentReportSchemaTest` is what pins that schema against the platform's.
   */
  private fun writeReportZip(name: String, vararg plugins: ReportedPlugin): Path {
    return writeReportZip(name = name, bundled = plugins.toList(), nonBundled = emptyList())
  }

  private fun writeReportZip(name: String, bundled: List<ReportedPlugin>, nonBundled: List<ReportedPlugin>): Path {
    val file = tempFolder.newFile("$name-content-report.zip").toPath()
    ZipOutputStream(file.outputStream()).use { out ->
      for ((entryName, plugins) in listOf("bundled-plugins.yaml" to bundled, "non-bundled-plugins.yaml" to nonBundled)) {
        out.putNextEntry(ZipEntry(entryName))
        out.write(recipeYaml.encodeToString(ListSerializer(ReportedPlugin.serializer()), plugins).toByteArray())
        out.closeEntry()
      }
    }
    return file
  }

  /**
   * A zip with the entries stated verbatim, for a report the reader has to refuse.
   *
   * Hand-written where [writeReportZip] serializes, because a damaged report is exactly what the schema cannot produce.
   */
  private fun writeRawReportZip(name: String, vararg entries: Pair<String, String>): Path {
    val file = tempFolder.newFile("$name-content-report.zip").toPath()
    ZipOutputStream(file.outputStream()).use { out ->
      for ((entryName, text) in entries) {
        out.putNextEntry(ZipEntry(entryName))
        out.write(text.toByteArray())
        out.closeEntry()
      }
    }
    return file
  }
}
