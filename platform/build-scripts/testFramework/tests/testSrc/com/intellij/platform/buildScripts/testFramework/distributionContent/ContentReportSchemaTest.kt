// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProjectLibraryEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * Two canonical classes state what a build packs. [FileEntry] states one file of a distribution, and
 * [PluginContentReport] states one plugin's layout. The checked-in `module-content.yaml` reports and a distribution
 * build's `content-report.zip` both come from them.
 *
 * The JPS-to-Bazel converter reads them back through two narrow schemas of its own:
 *
 * - `RecipeEntry`/`RecipeModule`/`RecipeNamed` in
 *   `community/platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/contentModuleJar.kt` mirror the three
 *   entry classes.
 * - `ReportedPlugin` in `pluginContentReportZip.kt` of the same directory mirrors [PluginContentReport]. That class is the
 *   envelope of one per-plugin report in the zip.
 *
 * Both narrow schemas decode with `recipeYaml`, which sets `strictMode = false`. A field a narrow schema fails to declare
 * is dropped in silence instead of reported. That is how `module:` once went missing from the converter's view of a
 * plugin's members. The converter then under-declared the dev-distribution input manifest, and `//build:idea_air_dist`
 * failed at assembly time.
 *
 * This test is the enforcement for that asymmetry. It fails, and it names the field, as soon as a canonical class and its
 * narrow schema stop agreeing. A *rename* also fails here. A canonical reader survives a rename, because the same code
 * writes and reads the report, while the narrow schema's now-dead field reads nothing.
 *
 * It compares field-name sets, and it scans no report. A rename fails on the first run whatever the corpus holds. The 784
 * checked-in `module-content.yaml` reports also carry no `productModules` and no `productEmbeddedModules`, so a scan would
 * enforce nothing about those two.
 *
 * The plugin envelope has no checked-in corpus to scan at all. IJAI-955 retired `plugin-content.yaml`, and a per-plugin
 * report now reaches the converter only in `content-report.zip`, which a distribution build writes. So a field-name
 * comparison is the only enforcement a test can give that envelope. `readPluginContentReportZips` adds a second layer at
 * run time. It refuses a zip that names no plugin, and a plugin that carries no main module.
 *
 * ### Why a narrow schema is mirrored here rather than compared descriptor-to-descriptor
 *
 * The two sides cannot be compiled together in any build. The converter is a *separate Bazel module* -
 * `module(name = "jps_to_bazel")` in `community/platform/build-scripts/bazel/MODULE.bazel`, which `community/.bazelignore`
 * excludes from the community workspace - and it gets the platform as published Maven artifacts (`@j2b_maven`), which do
 * not carry `intellij.platform.distributionContent`. The converter's own JPS module is skipped by the
 * converter itself (`BazelBuildFileGenerator.computeModuleList`), so it has no generated Bazel target for anything here to
 * depend on either, and a Maven artifact would pin the schema to a *released* platform rather than to the source that
 * writes the reports. So the enforcement lives on this side, where the canonical descriptor - the half that actually
 * changes - is the real thing, and each narrow schema is mirrored below.
 *
 * When this test fails, fix the converter file the message names first, and this mirror second.
 */
class ContentReportSchemaTest {
  @Test
  fun `narrow schemas cover every canonical report field`() {
    assertAll(
      { checkSchema(FILE_ENTRY) },
      { checkSchema(MODULE_ENTRY) },
      { checkSchema(PROJECT_LIBRARY_ENTRY) },
      { checkSchema(PLUGIN_CONTENT_REPORT) },
    )
  }

  private fun checkSchema(schema: NarrowSchema) {
    val canonical = schema.canonical.descriptor.elementNames.toSet()
    val problems = ArrayList<String>()

    // Reported together, because a rename shows up as one of each and fixing only the half you were shown is how the
    // divergence survives: `module` renamed to `ownerModule` is `ownerModule` unaccounted *and* `module` stale.
    val unaccounted = canonical - schema.modeled - schema.ignored.keys
    if (unaccounted.isNotEmpty()) {
      problems.add(
        "${schema.canonicalName} declares ${unaccounted.sorted()}, which ${schema.narrowName} in ${schema.narrowFile}" +
        " neither models nor deliberately ignores. Because `recipeYaml` sets `strictMode = false`, the converter reads" +
        " those fields as absent instead of failing on them. Either declare them in ${schema.narrowName} and fold them" +
        " into the reader that uses it, or add them to this test's `ignored` set with the reason they cannot matter to" +
        " a Bazel label."
      )
    }

    val stale = (schema.modeled + schema.ignored.keys) - canonical
    if (stale.isNotEmpty()) {
      problems.add(
        "${schema.narrowName} in ${schema.narrowFile} (or this test's mirror of it) names ${stale.sorted()}, which" +
        " ${schema.canonicalName} no longer declares - a renamed or removed field. The canonical reader still works," +
        " because the same code writes and reads it, while ${schema.narrowName} now silently reads nothing there." +
        " Rename it in ${schema.narrowFile} too, not only in this test."
      )
    }

    check(problems.isEmpty()) { problems.joinToString(separator = "\n\n") }
  }
}

private class NarrowSchema(
  @JvmField val canonical: KSerializer<*>,
  @JvmField val canonicalName: String,
  @JvmField val narrowName: String,
  /** The converter file that declares [narrowName], so a failure names the file to fix. */
  @JvmField val narrowFile: String,
  /** The canonical fields the narrow schema declares, i.e. the field names of [narrowName] itself. */
  @JvmField val modeled: Set<String>,
  /** The canonical fields the narrow schema deliberately does not declare, each with the reason it cannot matter. */
  @JvmField val ignored: Map<String, String>,
)

private val FILE_ENTRY = NarrowSchema(
  canonical = FileEntry.serializer(),
  canonicalName = "FileEntry",
  narrowName = "RecipeEntry",
  narrowFile = "contentModuleJar.kt",
  // `os`/`arch`/`libc` are modeled rather than ignored although no report the converter reads carries one on an entry: a
  // report is an OS superset (`collectPluginContentCategoryFailures` and `readPluginContentReportZips` both union the
  // per-OS variants of one main module), so an entry that did carry one would be read as unconditional, and
  // `simplePluginContentEntry` hands only unconditional jars off to a Bazel target. Declaring them turns that from a
  // silent misread into a veto.
  modeled = setOf("name", "os", "arch", "libc", "modules", "contentModules", "projectLibraries", "library", "module"),
  ignored = mapOf(
    // Written only onto the synthetic `name: plugins` entry of a *product platform* content report
    // (`writeProductModules`), where they list the product layout's own modules and its `intellij.moduleSets.*`
    // references. The plan generator reads them from that baseline into the platform fragment's payload
    // (`devDistPlanGenerator.kt`, the `entry.name == "plugins"` branch), not into any plugin's content. They are
    // product-level membership, not plugin membership, so a plugin's content target must not claim them; 0 of the 784
    // checked-in `module-content.yaml` reports carry either field.
    "productModules" to "product-level modules and module-set references of a platform report, not plugin content",
    "productEmbeddedModules" to "product-level embedded modules of a platform report, not plugin content",
    // The jar file names and sizes behind `library:`. The converter derives a library's jars from the JPS model and the
    // converted target graph (`getLibraryByJpsIdentity` -> `libraryJarTargets`), because a file name is not a label.
    "files" to "jar file names and sizes; jars are derived from the JPS model, not from the report",
    // Why the build included something. Provenance for the human reviewing a report diff; it names no member and no jar.
    "reason" to "inclusion provenance for review, names no member and no jar",
    // Written only onto the synthetic `name: plugins` entry of a platform content report, as the plugin index. A single
    // `PluginContentReport` is the unit the converter reads, one target per plugin, and `ReportedPlugin` mirrors it.
    "bundled" to "plugin index of a platform report; one PluginContentReport is the unit read here",
    "nonBundled" to "plugin index of a platform report; one PluginContentReport is the unit read here",
    // Both are written only by the executed-recipe report of a dev-distribution fragment (`DevDistRecipe`), never by
    // `buildJarContentReport`, so 0 of the reports the converter reads carry either. `kind` says how the
    // build produced an output - jar written, directory referenced, file reused - and `sources` states the one ordered
    // cross-kind source list, both of which are properties of an *assembly run*. The converter's question is the
    // opposite one: given a report, which Bazel target may pack this jar. It answers that from `modules`,
    // `contentModules` and the JPS model, and a recipe field would tell it about a run it never took.
    "kind" to "how a dev fragment produced an output; written by no report read here",
    "sources" to "the executed ordered source list of a dev fragment; written by no report read here",
  ),
)

private val MODULE_ENTRY = NarrowSchema(
  canonical = ModuleEntry.serializer(),
  canonicalName = "ModuleEntry",
  narrowName = "RecipeModule",
  narrowFile = "contentModuleJar.kt",
  modeled = setOf("name", "libraries"),
  ignored = mapOf(
    "size" to "module output size, a build measurement",
    "reason" to "inclusion provenance for review, names no member and no jar",
  ),
)

private val PROJECT_LIBRARY_ENTRY = NarrowSchema(
  canonical = ProjectLibraryEntry.serializer(),
  canonicalName = "ProjectLibraryEntry",
  narrowName = "RecipeNamed",
  narrowFile = "contentModuleJar.kt",
  modeled = setOf("name"),
  ignored = mapOf(
    "files" to "jar file names and sizes; jars are derived from the JPS model, not from the report",
    "dependentModules" to "which modules use the library, review information only",
    "reason" to "inclusion provenance for review, names no member and no jar",
  ),
)

// The envelope of one per-plugin report, and the only one of the four with no checked-in corpus behind it: the converter
// reads it from `content-report.zip`, which a distribution build writes. `ReportedPlugin` declares every field, so the
// `ignored` map is empty. A rename on this side is the expensive one. `readPluginContentReportZips` writes
// `dev_dist_plugin_content_population.txt` from `mainModule`, so a `mainModule` that read as absent would empty that file
// and drop every content leaf with it. That reader now refuses an empty main module at run time, and this row is the check
// that fails first, before any build reaches the reader.
private val PLUGIN_CONTENT_REPORT = NarrowSchema(
  canonical = PluginContentReport.serializer(),
  canonicalName = "PluginContentReport",
  narrowName = "ReportedPlugin",
  narrowFile = "pluginContentReportZip.kt",
  modeled = setOf("mainModule", "os", "arch", "content"),
  ignored = emptyMap(),
)
