// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.ModuleEntry
import com.intellij.platform.distributionContent.testFramework.ProjectLibraryEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * The checked-in `plugin-content.yaml` and `module-content.yaml` reports are written from [FileEntry] and read back by a
 * second, independent narrow schema - `RecipeEntry`/`RecipeModule`/`RecipeNamed` in
 * `community/platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/contentModuleJar.kt` - whose `recipeYaml`
 * sets `strictMode = false`, so a field it fails to declare is silently dropped instead of reported. That is how `module:`
 * once went missing from the JPS-to-Bazel converter's view of a plugin's members, which under-declared the
 * dev-distribution input manifest and failed `//build:idea_air_dist` at assembly time.
 *
 * This test is the enforcement for that asymmetry: it fails, naming the field, as soon as [FileEntry] and the narrow
 * schema stop agreeing - including on a *rename*, which the canonical reader survives (the same code writes and reads it)
 * while the narrow schema's now-dead field silently reads nothing.
 *
 * It compares field-name sets rather than scanning the 1233 reports on purpose: a rename fails on the first run whatever
 * the reports happen to contain, and 0 reports use `productModules`/`productEmbeddedModules` today, so a scan would
 * enforce nothing about them.
 *
 * ### Why the narrow schema is mirrored here rather than compared descriptor-to-descriptor
 *
 * The two classes cannot be compiled together in any build. The converter is a *separate Bazel module* -
 * `module(name = "jps_to_bazel")` in `community/platform/build-scripts/bazel/MODULE.bazel`, which `community/.bazelignore`
 * excludes from the community workspace - and it gets the platform as published Maven artifacts (`@j2b_maven`), which do
 * not carry `intellij.platform.distributionContent.testFramework`. The converter's own JPS module is skipped by the
 * converter itself (`BazelBuildFileGenerator.computeModuleList`), so it has no generated Bazel target for anything here to
 * depend on either, and a Maven artifact would pin the schema to a *released* platform rather than to the source that
 * writes the reports. So the enforcement lives on this side, where [FileEntry]'s own descriptor - the half that actually
 * changes - is the real thing, and the narrow schema is mirrored below.
 *
 * When this test fails, fix `contentModuleJar.kt` first and this mirror second.
 */
class ContentReportSchemaTest {
  @Test
  fun `narrow recipe schema covers every canonical report field`() {
    assertAll(
      { checkSchema(FILE_ENTRY) },
      { checkSchema(MODULE_ENTRY) },
      { checkSchema(PROJECT_LIBRARY_ENTRY) },
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
        "${schema.canonicalName} declares ${unaccounted.sorted()}, which ${schema.narrowName} in contentModuleJar.kt" +
        " neither models nor deliberately ignores. Because `recipeYaml` sets `strictMode = false`, the converter reads" +
        " those fields as absent instead of failing on them. Either declare them in ${schema.narrowName} and fold them" +
        " into the reader that uses it, or add them to this test's `ignored` set with the reason they cannot matter to" +
        " a Bazel label."
      )
    }

    val stale = (schema.modeled + schema.ignored.keys) - canonical
    if (stale.isNotEmpty()) {
      problems.add(
        "${schema.narrowName} in contentModuleJar.kt (or this test's mirror of it) names ${stale.sorted()}, which" +
        " ${schema.canonicalName} no longer declares - a renamed or removed field. The canonical reader still works," +
        " because the same code writes and reads it, while ${schema.narrowName} now silently reads nothing there." +
        " Rename it in contentModuleJar.kt too, not only in this test."
      )
    }

    check(problems.isEmpty()) { problems.joinToString(separator = "\n\n") }
  }
}

private class NarrowSchema(
  @JvmField val canonical: KSerializer<*>,
  @JvmField val canonicalName: String,
  @JvmField val narrowName: String,
  /** The canonical fields the narrow schema declares, i.e. the field names of [narrowName] itself. */
  @JvmField val modeled: Set<String>,
  /** The canonical fields the narrow schema deliberately does not declare, each with the reason it cannot matter. */
  @JvmField val ignored: Map<String, String>,
)

private val FILE_ENTRY = NarrowSchema(
  canonical = FileEntry.serializer(),
  canonicalName = "FileEntry",
  narrowName = "RecipeEntry",
  // `os`/`arch`/`libc` are modeled rather than ignored although no checked-in plugin or module report carries one: a
  // report is an OS superset (`collectPluginContentCategoryFailures` unions the per-OS variants), so an entry that did
  // carry one would be read as unconditional, and `simplePluginContentModuleName` hands only unconditional jars off to
  // a Bazel target. Declaring them turns that from a silent misread into a veto.
  modeled = setOf("name", "os", "arch", "libc", "modules", "contentModules", "projectLibraries", "library", "module"),
  ignored = mapOf(
    // Written only onto the synthetic `name: plugins` entry of a *product platform* content report
    // (`writeProductModules`), where they list the product layout's own modules and its `intellij.moduleSets.*`
    // references. The plan generator reads them from that baseline into the platform fragment's payload
    // (`devDistPlanGenerator.kt`, the `entry.name == "plugins"` branch), not into any plugin's content. They are
    // product-level membership, not plugin membership, so a plugin's content target must not claim them; 0 of the 1233
    // checked-in plugin and module reports carry either field.
    "productModules" to "product-level modules and module-set references of a platform report, not plugin content",
    "productEmbeddedModules" to "product-level embedded modules of a platform report, not plugin content",
    // The jar file names and sizes behind `library:`. The converter derives a library's jars from the JPS model and the
    // converted target graph (`getLibraryByJpsIdentity` -> `libraryJarTargets`), because a file name is not a label.
    "files" to "jar file names and sizes; jars are derived from the JPS model, not from the report",
    // Why the build included something. Provenance for the human reviewing a report diff; it names no member and no jar.
    "reason" to "inclusion provenance for review, names no member and no jar",
    // Written only onto the synthetic `name: plugins` entry of a platform content report, as the list of plugin main
    // modules. Each plugin's own report is the unit the converter reads, one target per report.
    "bundled" to "plugin index of a platform report; each plugin's own report is the unit read here",
    "nonBundled" to "plugin index of a platform report; each plugin's own report is the unit read here",
  ),
)

private val MODULE_ENTRY = NarrowSchema(
  canonical = ModuleEntry.serializer(),
  canonicalName = "ModuleEntry",
  narrowName = "RecipeModule",
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
  modeled = setOf("name"),
  ignored = mapOf(
    "files" to "jar file names and sizes; jars are derived from the JPS model, not from the report",
    "dependentModules" to "which modules use the library, review information only",
    "reason" to "inclusion provenance for review, names no member and no jar",
  ),
)
