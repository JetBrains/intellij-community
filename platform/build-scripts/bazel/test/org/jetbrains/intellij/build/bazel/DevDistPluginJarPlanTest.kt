// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.writeText

/**
 * The jar-plan comparison, over hand-written plans.
 *
 * The plan text is written out rather than serialized through a writer, unlike the report fixtures of
 * `PluginContentReportZipTest`: what these cases assert is that this side reads what `DevDistRecipe` writes, and a
 * fixture built from this side's own schema could not fail on a field it does not declare.
 */
internal class DevDistPluginJarPlanTest {
  @JvmField
  @Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `a jar the two sides describe the same way is identical`() {
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: plugins/demo/lib/demo.jar\n" +
        "  modules:\n" +
        "  - name: intellij.demo\n" +
        "  contentModules:\n" +
        "  - name: intellij.demo.core\n" +
        "  kind: jar\n"
      ),
      derived = listOf(mainJar(modules = listOf("intellij.demo"), contentModules = listOf("intellij.demo.core"))),
    )

    assertEquals(listOf("plugins/demo/lib/demo.jar"), comparison.identical)
    assertEquals(emptyList<PlanJarDifference>(), comparison.differing)
    assertEquals(emptyList<String>(), comparison.derivedOnly)
  }

  @Test
  fun `a member only one side names is one difference per field`() {
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: plugins/demo/lib/demo.jar\n" +
        "  modules:\n" +
        "  - name: intellij.demo\n" +
        "  kind: jar\n"
      ),
      derived = listOf(mainJar(modules = listOf("intellij.demo", "intellij.demo.rt"), contentModules = emptyList())),
    )

    val difference = comparison.differing.single()
    assertEquals("modules", difference.field)
    assertEquals(listOf("intellij.demo.rt"), difference.onlyDerived)
    assertEquals(emptyList<String>(), difference.onlyExecuted)
  }

  @Test
  fun `a contentModules key naming a descriptor states the module`() {
    // `intellij.foo/bar` is one member shipped under another descriptor, and both sides have to read it as
    // `intellij.foo`. Reading the raw key would report every such member as a difference.
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: plugins/demo/lib/demo.jar\n" +
        "  modules:\n" +
        "  - name: intellij.demo\n" +
        "  contentModules:\n" +
        "  - name: intellij.demo.core/intellij.demo.other\n" +
        "  kind: jar\n"
      ),
      derived = listOf(mainJar(modules = listOf("intellij.demo"), contentModules = listOf("intellij.demo.core"))),
    )

    assertEquals(listOf("plugins/demo/lib/demo.jar"), comparison.identical)
  }

  @Test
  fun `a jar the layout names and the residue does not is its own hold-out class`() {
    // The `PluginLayout.withModule(name, jarName)` case: the run puts the member in `demo-rt.jar` and the derivation
    // co-packs it into the main jar, because `extra_members` carries no jar name. One missing field, counted once here
    // and once as the main jar's own difference.
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: plugins/demo/lib/demo-rt.jar\n" +
        "  modules:\n" +
        "  - name: intellij.demo.rt\n" +
        "  kind: jar\n"
      ),
      derived = listOf(mainJar(modules = listOf("intellij.demo", "intellij.demo.rt"), contentModules = emptyList())),
    )

    assertEquals(
      mapOf(PlanHoldOutReason.UNSTATED_MEMBER_JAR_NAME to listOf("plugins/demo/lib/demo-rt.jar")),
      comparison.heldOut,
    )
  }

  @Test
  fun `a jar whose members the derivation does not hold is held out under the other reason`() {
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: plugins/demo/lib/other.jar\n" +
        "  modules:\n" +
        "  - name: intellij.unknown\n" +
        "  kind: jar\n"
      ),
      derived = listOf(mainJar(modules = listOf("intellij.demo"), contentModules = emptyList())),
    )

    assertEquals(mapOf(PlanHoldOutReason.NO_DERIVED_JAR to listOf("plugins/demo/lib/other.jar")), comparison.heldOut)
  }

  @Test
  fun `a platform jar and an unplaced plugin are held out with their own reasons`() {
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: lib/app.jar\n" +
        "  kind: jar\n" +
        "- name: plugins/other/lib/other.jar\n" +
        "  kind: jar\n"
      ),
      derived = listOf(mainJar(modules = listOf("intellij.demo"), contentModules = emptyList())),
    )

    assertEquals(
      mapOf(
        PlanHoldOutReason.NOT_A_PLUGIN_JAR to listOf("lib/app.jar"),
        PlanHoldOutReason.UNPLACED_PLUGIN_DIRECTORY to listOf("plugins/other/lib/other.jar"),
      ),
      comparison.heldOut,
    )
  }

  @Test
  fun `two plugins deriving one jar name make it ambiguous`() {
    // `intellij.java.plugin` and `language-server.plugins.java` both place `plugins/java/`, and no distribution holds
    // both. The population is the union over the products, so this side sees both.
    val comparison = comparePluginJarPlan(
      executed = read("- name: plugins/demo/lib/demo.jar\n  kind: jar\n"),
      derived = listOf(
        mainJar(modules = listOf("intellij.demo"), contentModules = emptyList()),
        mainJar(modules = listOf("other.demo"), contentModules = emptyList()),
      ),
    )

    assertEquals(mapOf(PlanHoldOutReason.AMBIGUOUS_JAR_NAME to listOf("plugins/demo/lib/demo.jar")), comparison.heldOut)
    assertEquals(emptyList<String>(), comparison.derivedOnly)
  }

  @Test
  fun `a handed-over jar the fragment did not pack is not a difference`() {
    // A `content_module_jar` target packs it, so a fragment's plan holds no row for it, and
    // `./build/dev-dist.cmd jars` is that jar's own byte gate.
    val comparison = comparePluginJarPlan(
      executed = read("- name: plugins/demo/lib/demo.jar\n  kind: jar\n"),
      derived = listOf(
        mainJar(modules = emptyList(), contentModules = emptyList()),
        DerivedPluginJar(
          name = "plugins/demo/lib/modules/intellij.demo.core.jar",
          modules = emptyList(),
          contentModules = listOf("intellij.demo.core"),
          isHandedOver = true,
        ),
      ),
    )

    assertEquals(emptyList<String>(), comparison.derivedOnly)
  }

  @Test
  fun `a jar the derivation names and this fragment packs nowhere is reported`() {
    val comparison = comparePluginJarPlan(
      executed = read("- name: plugins/demo/lib/demo.jar\n  kind: jar\n"),
      derived = listOf(
        mainJar(modules = emptyList(), contentModules = emptyList()),
        DerivedPluginJar(
          name = "plugins/demo/lib/modules/intellij.demo.core.jar",
          modules = emptyList(),
          contentModules = listOf("intellij.demo.core"),
          isHandedOver = false,
        ),
      ),
    )

    assertEquals(listOf("plugins/demo/lib/modules/intellij.demo.core.jar"), comparison.derivedOnly)
  }

  @Test
  fun `a file that is not a fragment plan is refused`() {
    // Both refusals name what the file would otherwise be read as: an empty comparison that looks like agreement.
    for ((text, clause) in listOf(
      "[]\n" to "states no output",
      "- name: plugins/demo/lib/console.groovy\n  kind: link\n" to "and no `jar` among them",
    )) {
      val failure = assertThrows(IllegalStateException::class.java) { read(text) }
      assertTrue(failure.message, failure.message!!.contains(clause))
    }
  }

  private fun mainJar(modules: List<String>, contentModules: List<String>): DerivedPluginJar = DerivedPluginJar(
    name = "plugins/demo/lib/demo.jar",
    modules = modules,
    contentModules = contentModules,
    isHandedOver = false,
    isMainJar = true,
  )

  private fun read(text: String): Map<String, ExecutedPlanEntry> {
    val file = temporaryFolder.root.toPath().resolve("fragment.plan.yaml")
    file.writeText(text)
    return readExecutedPlanJars(file)
  }
}
