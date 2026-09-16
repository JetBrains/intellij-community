// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.ProjectLibraryEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * One canonical class states what a build packs: [FileEntry] states one file of a distribution. The checked-in
 * `module-content.yaml` reports and the executed plan a dev-distribution fragment writes both come from it.
 *
 * The JPS-to-Bazel converter reads it back through a narrow schema of its own: `ExecutedPlanEntry` in
 * `community/platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/devDistPluginJarPlan.kt` mirrors
 * [FileEntry], and `RecipeModule`/`RecipeNamed` in `contentModuleJar.kt` of the same directory mirror the two nested
 * entry classes.
 *
 * The converter no longer reads a checked-in `module-content.yaml`: the dev-dist tables state a content module's jar.
 *
 * The narrow schemas decode with `recipeYaml`, which sets `strictMode = false`. A field a narrow schema fails to declare
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
      { checkSchema(MODULE_ENTRY) },
      { checkSchema(PROJECT_LIBRARY_ENTRY) },
      { checkSchema(EXECUTED_PLAN_ENTRY) },
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

// The narrow schema over `FileEntry`, for a dev-distribution fragment's executed `<fragment>.plan.yaml`. Its question is
// where a jar went and whose module output it holds, so it declares `kind` and ignores the membership detail a content
// target gets from the dev-dist tables.
private val EXECUTED_PLAN_ENTRY = NarrowSchema(
  canonical = FileEntry.serializer(),
  canonicalName = "FileEntry",
  narrowName = "ExecutedPlanEntry",
  narrowFile = "devDistPluginJarPlan.kt",
  modeled = setOf("name", "kind", "modules", "contentModules"),
  ignored = mapOf(
    // The plan's own selectors. A fragment assembles one target platform, so every row of one plan carries the same
    // three values and none of them tells two rows apart.
    "os" to "one plan is one target platform, so this is constant over its rows",
    "arch" to "one plan is one target platform, so this is constant over its rows",
    "libc" to "one plan is one target platform, so this is constant over its rows",
    // The packer's half of the recipe. `./build/dev-dist.cmd replay` packs a fragment's own source list again and
    // compares the bytes, so the ordered list already has a gate; this comparison covers the other half.
    "sources" to "the executed ordered source list, which the replay gate reproduces byte for byte",
    // Membership facts a content target needs and a jar's identity does not. A jar name plus its two module lists is
    // what a per-jar packing action declares, and the libraries inside a member's jar come from the member.
    "projectLibraries" to "the libraries in a jar, which a per-jar action gets from its members",
    "library" to "a bare library jar's library, which a per-jar action gets from its members",
    "module" to "the module owning `library`, which a per-jar action gets from its members",
    "productModules" to "product-level modules of a platform report; a fragment plan carries none",
    "productEmbeddedModules" to "product-level embedded modules of a platform report; a fragment plan carries none",
    "files" to "jar file names and sizes, a measurement of the run",
    "reason" to "inclusion provenance for review, names no member and no jar",
    "bundled" to "plugin index of a platform report; a fragment plan carries none",
    "nonBundled" to "plugin index of a platform report; a fragment plan carries none",
  ),
)
