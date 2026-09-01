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
          relativeOutputFile = "modules/intellij.demo.core.jar",
          members = listOf("intellij.demo.core"),
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
          relativeOutputFile = "modules/intellij.demo.core.jar",
          members = listOf("intellij.demo.core"),
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
    // The written table and its own reader, and not a hand-written text: the two state the field vocabulary separately
    // and no compiler pins them to each other.
    val section = ContentResidueSection(
      memberJars = mapOf("intellij.demo.ns" to listOf("demo.jar", "ns/ns.jar"), "intellij.demo.rt" to listOf("demo-rt.jar")),
    )
    val file = temporaryFolder.root.toPath().resolve(PLUGIN_CONTENT_RESIDUE_FILE_NAME)
    file.writeText(renderPluginContentResidue(mapOf("intellij.demo.plugin" to section)))

    val parsed = readPluginContentResidue(file).getValue("intellij.demo.plugin")
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

  @Test
  fun `a jar of one member the model can name earns a packing target`() {
    assertNull(refuse())
  }

  @Test
  fun `a jar named after its one member belongs to that member's own target`() {
    // The rule the user's rejection of 2026-09-01 states: the two destinations `_collect_prepacked` accepts are the
    // member's own jar under `lib/modules/` and in `lib/` itself, and a target here would restate the member's own facts.
    assertEquals(PluginJarProducer.MEMBER_JAR_TARGET, producerOf("modules/$MEMBER.jar", hasMemberJarTarget = true))
    assertEquals(PluginJarProducer.MEMBER_JAR_TARGET, producerOf("$MEMBER.jar", hasMemberJarTarget = true))
    // The candidacy states no target, so no relation can name one either. The hand-over slice owns that half.
    assertEquals(PluginJarProducer.MEMBER_JAR_CANDIDACY, producerOf("modules/$MEMBER.jar", hasMemberJarTarget = false))
  }

  @Test
  fun `a jar the plugin names itself is this generator's to emit`() {
    // Three shapes, and every one of them states something no member does: a name, a directory, and a second member.
    assertEquals(PluginJarProducer.PLUGIN_JAR_TARGET, producerOf("demo-rt.jar", hasMemberJarTarget = true))
    assertEquals(PluginJarProducer.PLUGIN_JAR_TARGET, producerOf("specifics/$MEMBER.jar", hasMemberJarTarget = true))
    assertEquals(
      PluginJarProducer.PLUGIN_JAR_TARGET,
      producerOf("modules/$MEMBER.jar", hasMemberJarTarget = true, members = listOf(MEMBER, "intellij.demo.extra")),
    )
  }

  @Test
  fun `one ambiguous destination takes its two jars through two exits`() {
    // The station case: the plugin states `modules/<member>.jar` for one member and derives the same path for another.
    // `computeMovablePluginJars` asks the producer first, and the producer reads the destination alone. So the
    // member-named half is counted under its member's own target and never reaches the refusal, and only the half this
    // generator answers for is refused. The refused count is 1 for the pair, and the hand-over slice has to ask the
    // ambiguity again before it wires a relation for the other half.
    assertEquals(PluginJarProducer.MEMBER_JAR_TARGET, producerOf("modules/$MEMBER.jar", hasMemberJarTarget = true))
    assertEquals(
      PluginJarProducer.PLUGIN_JAR_TARGET,
      producerOf("modules/$MEMBER.jar", hasMemberJarTarget = true, members = listOf("intellij.demo.other")),
    )
    assertEquals(PluginJarExclusion.AMBIGUOUS_DESTINATION, refuse(ambiguousDestinations = setOf("modules/$MEMBER.jar")))
  }

  /** [pluginJarProducer] over one jar of [members] at [relativeOutputFile]. */
  private fun producerOf(
    relativeOutputFile: String,
    hasMemberJarTarget: Boolean,
    members: List<String> = listOf(MEMBER),
  ): PluginJarProducer = pluginJarProducer(
    jar = DerivedPluginJar(
      name = "plugins/demo/lib/$relativeOutputFile",
      relativeOutputFile = relativeOutputFile,
      members = members,
      modules = emptyList(),
      contentModules = members,
      isHandedOver = false,
    ),
    hasMemberJarTarget = { hasMemberJarTarget },
  )

  @Test
  fun `a jar the layout names reads its own destination, and never its members`() {
    // The plugin's own `dev_dist_plugin_jar` packs such a jar, and that target states a destination. The members
    // answer another question - whether the leaf still declares them - and the two answers differ for the case below.
    val members = listOf(MEMBER, "intellij.demo.extra")
    val statedJar = mapOf(MEMBER to setOf("rt/demo-rt.jar"), "intellij.demo.extra" to setOf("rt/demo-rt.jar"))

    assertFalse(derive(memberNames = members, memberJars = statedJar).single { !it.isMainJar }.isHandedOver)
    // Every member handed over through the *member* channel, and no target of this plugin. Reading the members here
    // would take the jar out of the movable set while the fragment still packs it.
    assertFalse(
      derive(memberNames = members, memberJars = statedJar, handedOverMembers = members.toSet())
        .single { !it.isMainJar }.isHandedOver
    )
    assertTrue(
      derive(memberNames = members, memberJars = statedJar, handedOverJars = setOf("rt/demo-rt.jar"))
        .single { !it.isMainJar }.isHandedOver
    )
  }

  @Test
  fun `a member of two jars the layout names moves both jars and keeps its declaration`() {
    // `intellij.maven.server.telemetry` is the case: the residue gives it `intellij.maven.server3/…` and
    // `intellij.maven.server4/maven-server-telemetry.jar`. The fragment packs its raw output into the second jar, so
    // `computeDerivedPluginPacking` leaves the member declared - and both jars still have a packing target.
    val statedJars = mapOf(MEMBER to setOf("server3/telemetry.jar", "server4/telemetry.jar"))
    val jars = derive(
      memberNames = listOf(MEMBER),
      memberJars = statedJars,
      handedOverJars = setOf("server3/telemetry.jar", "server4/telemetry.jar"),
    )

    assertEquals(listOf(true, true), jars.filter { !it.isMainJar }.map { it.isHandedOver })
    // The residue states the member's whole jar set and no row of it names the main jar, so the main jar holds the
    // plugin's own module alone.
    assertEquals(listOf("intellij.demo"), jars.single { it.isMainJar }.members)
  }

  @Test
  fun `each refusal class takes the jar out of the movable set`() {
    // One case per class, and the message is asserted rather than the constant, so a renamed class still reads.
    assertEquals(PluginJarExclusion.SCRAMBLING_PLUGIN, refuse(scrambles = true))
    assertEquals(PluginJarExclusion.AMBIGUOUS_DESTINATION, refuse(ambiguousDestinations = setOf("modules/$MEMBER.jar")))
    assertEquals(PluginJarExclusion.VETOED_MEMBER, refuse(vetoedMembers = setOf(MEMBER)))
    assertEquals(PluginJarExclusion.RAW_MEMBER, refuse(rawMembers = setOf(MEMBER)))
    assertEquals(PluginJarExclusion.UNKNOWN_MEMBER, refuse(unknownMembers = setOf(MEMBER)))
    assertEquals(PluginJarExclusion.CROSS_REPOSITORY_MEMBER, refuse(crossRepositoryMembers = setOf(MEMBER)))
    assertEquals(PluginJarExclusion.UNSTATED_MEMBER_LIBRARIES, refuse(memberLibraries = emptyMap()))
    assertEquals(PluginJarExclusion.UNNAMEABLE_LIBRARY, refuse(memberLibraries = mapOf(MEMBER to null)))
  }

  @Test
  fun `a stated unpackable jar is refused by its distribution path`() {
    // The one class no derivation reaches, so the row is keyed by where the jar lands and not by a member.
    val jar = DerivedPluginJar(
      name = "plugins/maven-plugin/lib/artifact-resolver-m31.jar",
      relativeOutputFile = "artifact-resolver-m31.jar",
      members = listOf(MEMBER),
      modules = listOf(MEMBER),
      contentModules = emptyList(),
      isHandedOver = false,
    )

    assertEquals(
      PluginJarExclusion.STATED_UNPACKABLE,
      refusePluginJar(
        jar = jar,
        scrambles = false,
        ambiguousDestinations = emptySet(),
        vetoedMembers = emptySet(),
        rawMembers = emptySet(),
        unknownMembers = emptySet(),
        crossRepositoryMembers = emptySet(),
        memberLibraries = mapOf(MEMBER to emptySet()),
      ),
    )
  }

  @Test
  fun `the widest refusal wins, so the counts partition the refused set`() {
    // Every class holds at once. The order decides which one the table counts it under, and a jar counted twice would
    // make the refused total larger than the derived set.
    assertEquals(
      PluginJarExclusion.SCRAMBLING_PLUGIN,
      refuse(
        scrambles = true,
        ambiguousDestinations = setOf("modules/$MEMBER.jar"),
        vetoedMembers = setOf(MEMBER),
        rawMembers = setOf(MEMBER),
        unknownMembers = setOf(MEMBER),
        crossRepositoryMembers = setOf(MEMBER),
        memberLibraries = emptyMap(),
      ),
    )
  }

  @Test
  fun `two plugins of one package emitting one target name fail the run, and both are named`() {
    // The check the converter runs before it saves the first file. One plugin stating one destination twice is no
    // collision, because the emission writes one target for it.
    val plugins = listOf(
      PluginJarPackage(packagePath = "/repo/plugins/demo", plugin = "intellij.demo", relativeOutputFiles = listOf("modules/$MEMBER.jar")),
      PluginJarPackage(packagePath = "/repo/plugins/demo", plugin = "intellij.other", relativeOutputFiles = listOf("modules/$MEMBER.jar")),
    )

    checkOnePluginPerJarTargetPackage(listOf(plugins[0], plugins[0]))
    val failure = assertThrows(IllegalStateException::class.java) { checkOnePluginPerJarTargetPackage(plugins) }
    assertTrue(failure.message, failure.message!!.contains("`intellij.other` and `intellij.demo`"))
    assertTrue(failure.message, failure.message!!.contains(pluginJarTargetName("modules/$MEMBER.jar")))
  }

  @Test
  fun `a plugin jar target is named after its destination`() {
    // The Kotlin half of the macro's own rule. A subdirectory becomes one token, so two jars of one package cannot
    // collide unless their destinations do.
    assertEquals("modules_intellij.demo.core_dev_dist_plugin_jar", pluginJarTargetName("modules/intellij.demo.core.jar"))
    assertEquals("specifics_tomee-specifics_dev_dist_plugin_jar", pluginJarTargetName("specifics/tomee-specifics.jar"))
    assertEquals("demo_dev_dist_plugin_jar", pluginJarTargetName("demo.jar"))
  }

  /** [refusePluginJar] over one derived jar of [MEMBER], with every fact defaulting to the movable answer. */
  private fun refuse(
    scrambles: Boolean = false,
    ambiguousDestinations: Set<String> = emptySet(),
    vetoedMembers: Set<String> = emptySet(),
    rawMembers: Set<String> = emptySet(),
    unknownMembers: Set<String> = emptySet(),
    crossRepositoryMembers: Set<String> = emptySet(),
    memberLibraries: Map<String, Set<String>?> = mapOf(MEMBER to setOf("junit4")),
  ): PluginJarExclusion? = refusePluginJar(
    jar = DerivedPluginJar(
      name = "plugins/demo/lib/modules/$MEMBER.jar",
      relativeOutputFile = "modules/$MEMBER.jar",
      members = listOf(MEMBER),
      modules = emptyList(),
      contentModules = listOf(MEMBER),
      isHandedOver = false,
    ),
    scrambles = scrambles,
    ambiguousDestinations = ambiguousDestinations,
    vetoedMembers = vetoedMembers,
    rawMembers = rawMembers,
    unknownMembers = unknownMembers,
    crossRepositoryMembers = crossRepositoryMembers,
    memberLibraries = memberLibraries,
  )

  /** One demo plugin's jars, from the facts [composeDerivedPluginJars] takes and a fixed placement. */
  private fun derive(
    memberNames: List<String>,
    memberJars: Map<String, Set<String>> = emptyMap(),
    derivedJars: Map<String, String> = emptyMap(),
    closureMembers: Set<String> = emptySet(),
    handedOverMembers: Set<String> = emptySet(),
    handedOverJars: Set<String> = emptySet(),
  ): List<DerivedPluginJar> = composeDerivedPluginJars(
    libDir = "plugins/demo/lib/",
    mainJarName = "demo.jar",
    mainModule = "intellij.demo",
    memberNames = memberNames,
    derivedJars = derivedJars,
    handedOverMembers = handedOverMembers,
    closureMembers = closureMembers,
    memberJars = memberJars,
    handedOverJars = handedOverJars,
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
    relativeOutputFile = "demo.jar",
    members = modules + contentModules,
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
