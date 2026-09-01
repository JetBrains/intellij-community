// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.writeText

/**
 * The jar-plan comparison over hand-written plans, and the jar derivation over hand-stated facts.
 *
 * The plan text is written out rather than serialized through a writer, unlike the report fixtures of
 * `PluginContentReportZipTest`: what these cases assert is that this side reads what `DevDistRecipe` writes, and a
 * fixture built from this side's own schema could not fail on a field it does not declare.
 *
 * The derivation cases call the four functions that hold the rules with no project model behind them:
 * [deriveMemberJarPath] for where a member's jar goes, [deriveMemberJar] for the offer on top of that path,
 * [memberJarRows] for the rows the residue then needs, and [composeDerivedPluginJars] for the jar set the answers
 * compose into.
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
    // co-packs it into the main jar, because no `member_jars` row states the jar. One missing row, counted once here
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
  fun `a stated member jar derives the jar and shrinks the main jar`() {
    // The repair of the case above: the `member_jars` row states where the layout puts the member, so the derivation
    // names that jar and the main jar stops holding the member. Both jars then match the run.
    val comparison = comparePluginJarPlan(
      executed = read(
        "- name: plugins/demo/lib/demo-rt.jar\n" +
        "  modules:\n" +
        "  - name: intellij.demo.rt\n" +
        "  kind: jar\n" +
        "- name: plugins/demo/lib/demo.jar\n" +
        "  modules:\n" +
        "  - name: intellij.demo\n" +
        "  kind: jar\n"
      ),
      derived = derive(
        memberNames = listOf("intellij.demo.rt"),
        memberJars = mapOf("intellij.demo.rt" to setOf("demo-rt.jar")),
      ),
    )

    assertEquals(listOf("plugins/demo/lib/demo-rt.jar", "plugins/demo/lib/demo.jar"), comparison.identical)
    assertEquals(emptyMap<PlanHoldOutReason, List<String>>(), comparison.heldOut)
  }

  @Test
  fun `two members sharing one stated path group into one derived jar`() {
    val derived = derive(
      memberNames = listOf("intellij.demo.rt", "intellij.demo.core"),
      memberJars = mapOf("intellij.demo.rt" to setOf("shared.jar"), "intellij.demo.core" to setOf("shared.jar")),
      closureMembers = setOf("intellij.demo.core"),
    )

    val jar = derived.single { it.name == "plugins/demo/lib/shared.jar" }
    // The closure decides which of the two lists a member reaches, the same split an offered jar takes.
    assertEquals(listOf("intellij.demo.rt"), jar.modules)
    assertEquals(listOf("intellij.demo.core"), jar.contentModules)
    assertFalse(jar.isHandedOver)
    assertEquals(listOf("intellij.demo"), derived.single { it.isMainJar }.modules)
  }

  @Test
  fun `a stated path naming the main jar keeps the member there`() {
    // `intellij.spring.customNs` sits in the plugin's main jar and in a jar of its own, so its row states both.
    val derived = derive(
      memberNames = listOf("intellij.demo.ns"),
      memberJars = mapOf("intellij.demo.ns" to setOf("demo.jar", "customNs/customNs.jar")),
      closureMembers = setOf("intellij.demo.ns"),
    )

    assertEquals(
      listOf("plugins/demo/lib/customNs/customNs.jar", "plugins/demo/lib/demo.jar"),
      derived.map { it.name }.sorted(),
    )
    val mainJar = derived.single { it.isMainJar }
    assertEquals(listOf("intellij.demo"), mainJar.modules)
    assertEquals(listOf("intellij.demo.ns"), mainJar.contentModules)
  }

  @Test
  fun `a member with a derived jar and no stated row takes that jar`() {
    val derived = derive(
      memberNames = listOf("intellij.demo.core"),
      derivedJars = mapOf("intellij.demo.core" to "modules/intellij.demo.core.jar"),
      closureMembers = setOf("intellij.demo.core"),
    )

    val jar = derived.single { it.name == "plugins/demo/lib/modules/intellij.demo.core.jar" }
    assertEquals(listOf("intellij.demo.core"), jar.contentModules)
    assertEquals(listOf("intellij.demo"), derived.single { it.isMainJar }.modules)
  }

  @Test
  fun `a derived jar that is the main jar keeps the member there`() {
    // The answer `deriveMemberJarPath` gives a member the plugin co-packs. The main jar holds it, and no second jar of
    // that name is derived.
    val derived = derive(
      memberNames = listOf("intellij.demo.core"),
      derivedJars = mapOf("intellij.demo.core" to "demo.jar"),
      closureMembers = setOf("intellij.demo.core"),
    )

    assertEquals(listOf("plugins/demo/lib/demo.jar"), derived.map { it.name })
    val mainJar = derived.single { it.isMainJar }
    assertEquals(listOf("intellij.demo"), mainJar.modules)
    assertEquals(listOf("intellij.demo.core"), mainJar.contentModules)
  }

  @Test
  fun `a stated row wins over the member's own derived jar`() {
    // The invariant the flat `withModule` deviation rests on. `deriveMemberJarPath` answers a path for every member, so
    // a member the layout names a jar for holds both answers, and the row has to state the whole set.
    val derived = derive(
      memberNames = listOf("intellij.demo.core"),
      memberJars = mapOf("intellij.demo.core" to setOf("demo-rt.jar")),
      derivedJars = mapOf("intellij.demo.core" to "modules/intellij.demo.core.jar"),
      closureMembers = setOf("intellij.demo.core"),
    )

    assertEquals(listOf("plugins/demo/lib/demo-rt.jar", "plugins/demo/lib/demo.jar"), derived.map { it.name })
    assertEquals(
      listOf("intellij.demo.core"),
      derived.single { it.name == "plugins/demo/lib/demo-rt.jar" }.contentModules,
    )
    val mainJar = derived.single { it.isMainJar }
    assertEquals(listOf("intellij.demo"), mainJar.modules)
    assertEquals(emptyList<String>(), mainJar.contentModules)
  }

  @Test
  fun `a member whose module library has no name keeps its path and loses only the offer`() {
    // An unnamed library with no single jar; see `distributionLibraryName`. It is a module library all the same, so
    // `needsSeparateJar` reads `true` and the path is the member's own jar. Only the offer goes.
    val unnameable = deriveMemberJar(libraries = null)

    assertEquals("modules/$MEMBER.jar", unnameable.relativeOutputFile)
    assertNull(unnameable.offer)
    // The same member with a library this generator can name offers that jar, which is what makes the case above a loss
    // of the offer alone.
    val nameable = deriveMemberJar(libraries = setOf("demo-library"))

    assertEquals("modules/$MEMBER.jar", nameable.relativeOutputFile)
    assertEquals(setOf("demo-library"), nameable.offer!!.libraries)
  }

  @Test
  fun `a derived jar with no member name derives no jar`() {
    // The precondition of `memberNames`: the caller already dropped a member with a jar and no module. The path alone
    // derives no jar, and it puts the member in no main jar either.
    val derived = derive(
      memberNames = emptyList(),
      derivedJars = mapOf("intellij.demo.core" to "modules/intellij.demo.core.jar"),
    )

    assertEquals(listOf("plugins/demo/lib/demo.jar"), derived.map { it.name })
    val mainJar = derived.single { it.isMainJar }
    assertEquals(listOf("intellij.demo"), mainJar.modules)
    assertEquals(emptyList<String>(), mainJar.contentModules)
  }

  @Test
  fun `a vetoed member keeps its conventional jar and needs no row`() {
    // The veto says which producer packs the jar, and the convention says where the jar goes. So a vetoed member at its
    // conventional path is fully derived, and a `member_jars` row would only restate the convention.
    val path = derivePath(
      residue = PluginContentResidue(vetoedMembers = setOf(MEMBER)),
      hasPackageAttribute = true,
      mergesLibraries = true,
    )

    assertEquals("modules/$MEMBER.jar", path)
    assertEquals(emptyMap<String, List<String>>(), rows(path, "lib/modules/$MEMBER.jar"))
  }

  @Test
  fun `an embedded member that merges libraries derives the lib root jar`() {
    // `computeEmbeddedOutputJarPath` states `lib/<module>.jar` whatever the jar merges. No packing target may serve such
    // a jar, and the path is derived all the same.
    val path = derivePath(loadingRule = EMBEDDED_LOADING_RULE, hasPackageAttribute = true, mergesLibraries = true)

    assertEquals("$MEMBER.jar", path)
    assertEquals(emptyMap<String, List<String>>(), rows(path, "lib/$MEMBER.jar"))
  }

  @Test
  fun `an embedded member with the marker goes into the plugin main jar`() {
    val path = derivePath(loadingRule = EMBEDDED_LOADING_RULE, packIntoPluginJar = true, hasPackageAttribute = true)

    assertEquals("demo.jar", path)
    assertEquals(emptyMap<String, List<String>>(), rows(path, "lib/demo.jar"))
  }

  @Test
  fun `a separate_jars member gets no second row for the same jar`() {
    // Without the row this member is co-packed, so the row is what moves it. The jar it names is then the derived one,
    // and `member_jars` states nothing more.
    val path = derivePath(residue = PluginContentResidue(separateJars = setOf(MEMBER)), hasPackageAttribute = true)

    assertEquals("modules/$MEMBER.jar", path)
    assertEquals(emptyMap<String, List<String>>(), rows(path, "lib/modules/$MEMBER.jar"))
  }

  @Test
  fun `a member in two jars states both`() {
    // `intellij.spring.customNs`. The layout names one jar and the plugin co-packs the member as well, so the row holds
    // both names. A containment rule would drop the main jar and lose the member there.
    assertEquals(
      mapOf(MEMBER to listOf("customNs/customNs.jar", "demo.jar")),
      rows("demo.jar", "lib/demo.jar", "lib/customNs/customNs.jar"),
    )
  }

  @Test
  fun `a jar the layout names replaces the derived one in the row`() {
    // The `frontend-split/` case: the derivation states the member's own jar and the run packs it somewhere else.
    assertEquals(
      mapOf(MEMBER to listOf("frontend-split/demo-frontend.jar")),
      rows("modules/$MEMBER.jar", "lib/frontend-split/demo-frontend.jar"),
    )
  }

  @Test
  fun `an entry outside the plugin lib directory contributes no jar`() {
    // `plugins/Groovy/lib/agent/gragent.jar` is placed rather than packed, and a report entry naming no `lib/` path
    // states no jar of a member.
    assertEquals(emptyMap<String, List<String>>(), rows("demo.jar", "agent/gragent.jar"))
  }

  @Test
  fun `a written member_jars row comes back as the jar set the derivation reads`() {
    // The written file and the parser, and not a hand-written text: a serial name the writer's key does not match would
    // decode as an empty map, because `recipeYaml` runs with `strictMode = false`.
    val section = ContentResidueSection(
      memberJars = mapOf("intellij.demo.rt" to listOf("demo-rt.jar"), "intellij.demo.ns" to listOf("ns/ns.jar", "demo.jar")),
    )
    val file = temporaryFolder.root.toPath().resolve("dev-dist.yaml")
    file.writeText(composeDevDistResidueText(content = section, existing = file)!!)

    val parsed = parseDevDistResidue(file)!!.content!!
    assertEquals(section.memberJars, parsed.memberJars)
    assertEquals(
      mapOf("intellij.demo.rt" to setOf("demo-rt.jar"), "intellij.demo.ns" to setOf("ns/ns.jar", "demo.jar")),
      parsed.toResidue().memberJars,
    )
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

  /** One demo plugin's jars, from the facts [composeDerivedPluginJars] takes and a fixed placement. */
  private fun derive(
    memberNames: List<String>,
    memberJars: Map<String, Set<String>> = emptyMap(),
    derivedJars: Map<String, String> = emptyMap(),
    closureMembers: Set<String> = emptySet(),
  ): List<DerivedPluginJar> = composeDerivedPluginJars(
    libDir = "plugins/demo/lib/",
    mainJarName = "demo.jar",
    mainModule = "intellij.demo",
    memberNames = memberNames,
    derivedJars = derivedJars,
    handedOverMembers = emptySet(),
    closureMembers = closureMembers,
    memberJars = memberJars,
  )

  /** [deriveMemberJar] for [MEMBER] under the demo plugin's `demo.jar`, as a member the convention gives its own jar. */
  private fun deriveMemberJar(libraries: Set<String>?): DerivedMemberJar = deriveMemberJar(
    moduleName = MEMBER,
    loadingRule = null,
    packIntoPluginJar = false,
    hasPackageAttribute = true,
    libraries = libraries,
    isStated = false,
    residue = PluginContentResidue.NONE,
    mainJarName = "demo.jar",
  )

  /** [deriveMemberJarPath] for [MEMBER] under the demo plugin's `demo.jar`, with the convention's own defaults. */
  private fun derivePath(
    loadingRule: String? = null,
    packIntoPluginJar: Boolean = false,
    hasPackageAttribute: Boolean = false,
    mergesLibraries: Boolean = false,
    residue: PluginContentResidue = PluginContentResidue.NONE,
  ): String = deriveMemberJarPath(
    moduleName = MEMBER,
    loadingRule = loadingRule,
    packIntoPluginJar = packIntoPluginJar,
    hasPackageAttribute = hasPackageAttribute,
    mergesLibraries = mergesLibraries,
    residue = residue,
    mainJarName = "demo.jar",
  )

  /**
   * The `member_jars` rows for [MEMBER], with [derivedPath] as its derived jar and [entryNames] as the report's entries.
   *
   * Every entry names [MEMBER] alone, which is what the two sets of [memberJarRows] compare.
   */
  private fun rows(derivedPath: String, vararg entryNames: String): Map<String, List<String>> = memberJarRows(
    mainJarName = "demo.jar",
    memberNames = setOf(MEMBER),
    derivedJars = mapOf(MEMBER to derivedPath),
    entries = entryNames.map { RecipeEntry(name = it, contentModules = listOf(RecipeModule(name = MEMBER))) },
  )

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

  private companion object {
    /** The one member the path and the row cases state, so a jar name reads as the member's own or as the plugin's. */
    const val MEMBER: String = "intellij.demo.core"
  }
}
