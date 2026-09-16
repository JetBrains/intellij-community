// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The jar composition over hand-stated facts, and the jar-path rule over hand-stated descriptor facts.
 *
 * Every case calls a function that holds a rule with no project model behind it: [deriveMemberJarPath] for where a
 * member's jar goes, [deriveMemberJar] for the offer on top of that path, and [composeDerivedPluginJars] for the jar
 * set the answers compose into.
 */
class PluginJarDerivationTest {
  @Test
  fun `two members sharing one stated path group into one derived jar`() {
    val derived = derive(
      memberNames = listOf("intellij.demo.rt", "intellij.demo.core"),
      memberJars = mapOf("intellij.demo.rt" to setOf("shared.jar"), "intellij.demo.core" to setOf("shared.jar")),
      closureMembers = setOf("intellij.demo.core"),
    )

    val jar = derived.single { it.name == "plugins/demo/lib/shared.jar" }
    // The closure decides which of the two lists a member reaches, the same split an offered jar takes.
    assertThat(jar.modules).containsExactly("intellij.demo.rt")
    assertThat(jar.contentModules).containsExactly("intellij.demo.core")
    assertThat(jar.isHandedOver).isFalse()
    assertThat(derived.single { it.isMainJar }.modules).containsExactly("intellij.demo")
  }

  @Test
  fun `a member-named jar and a stated jar at one path compose one jar`() {
    // `intellij.station.plugin` is the case: `<content>` gives `intellij.station.aia` its own jar, and the layout
    // packs `intellij.station.comms.mcp` into that same jar.
    val derived = derive(
      memberNames = listOf("intellij.demo.aia", "intellij.demo.mcp"),
      derivedJars = mapOf("intellij.demo.aia" to "modules/intellij.demo.aia.jar"),
      memberJars = mapOf("intellij.demo.mcp" to setOf("modules/intellij.demo.aia.jar")),
      closureMembers = setOf("intellij.demo.aia"),
      handedOverMembers = setOf("intellij.demo.aia"),
      memberLibraries = mapOf("intellij.demo.aia" to setOf("alpha"), "intellij.demo.mcp" to setOf("beta")),
    )

    assertThat(derived.map { it.relativeOutputFile }).containsExactly("modules/intellij.demo.aia.jar", "demo.jar")
    val jar = derived.single { !it.isMainJar }
    // The member the jar is named after comes first, and the layout member follows, which is the order the build packs.
    assertThat(jar.members).containsExactly("intellij.demo.aia", "intellij.demo.mcp")
    assertThat(jar.contentModules).containsExactly("intellij.demo.aia")
    assertThat(jar.modules).containsExactly("intellij.demo.mcp")
    // The member's own packing target cannot pack the layout member, so the member channel hands nothing over.
    assertThat(jar.isHandedOver).isFalse()
    assertThat(jar.libraries).isNull()

    val handedOver = derive(
      memberNames = listOf("intellij.demo.aia", "intellij.demo.mcp"),
      derivedJars = mapOf("intellij.demo.aia" to "modules/intellij.demo.aia.jar"),
      memberJars = mapOf("intellij.demo.mcp" to setOf("modules/intellij.demo.aia.jar")),
      closureMembers = setOf("intellij.demo.aia"),
      handedOverJars = setOf("modules/intellij.demo.aia.jar"),
      memberLibraries = mapOf("intellij.demo.aia" to setOf("beta"), "intellij.demo.mcp" to setOf("alpha")),
    ).single { !it.isMainJar }
    assertThat(handedOver.isHandedOver).isTrue()
    assertThat(handedOver.libraries).containsExactly("alpha", "beta")
  }

  @Test
  fun `a stated path naming the main jar keeps the member there`() {
    // `intellij.spring.customNs` sits in the plugin's main jar and in a jar of its own, so its row states both.
    val derived = derive(
      memberNames = listOf("intellij.demo.ns"),
      memberJars = mapOf("intellij.demo.ns" to setOf("demo.jar", "customNs/customNs.jar")),
      closureMembers = setOf("intellij.demo.ns"),
    )

    assertThat(derived.map { it.name }.sorted())
      .containsExactly("plugins/demo/lib/customNs/customNs.jar", "plugins/demo/lib/demo.jar")
    val mainJar = derived.single { it.isMainJar }
    assertThat(mainJar.modules).containsExactly("intellij.demo")
    assertThat(mainJar.contentModules).containsExactly("intellij.demo.ns")
  }

  @Test
  fun `a member with a derived jar and no stated row takes that jar`() {
    val derived = derive(
      memberNames = listOf("intellij.demo.core"),
      derivedJars = mapOf("intellij.demo.core" to "modules/intellij.demo.core.jar"),
      closureMembers = setOf("intellij.demo.core"),
    )

    val jar = derived.single { it.name == "plugins/demo/lib/modules/intellij.demo.core.jar" }
    assertThat(jar.contentModules).containsExactly("intellij.demo.core")
    assertThat(derived.single { it.isMainJar }.modules).containsExactly("intellij.demo")
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

    assertThat(derived.map { it.name }).containsExactly("plugins/demo/lib/demo.jar")
    val mainJar = derived.single { it.isMainJar }
    assertThat(mainJar.modules).containsExactly("intellij.demo")
    assertThat(mainJar.contentModules).containsExactly("intellij.demo.core")
  }

  @Test
  fun `a stated row wins over the member's own derived jar`() {
    // `deriveMemberJarPath` answers a path for every member, so a member the layout names a jar for holds both
    // answers, and the row has to state the whole set.
    val derived = derive(
      memberNames = listOf("intellij.demo.core"),
      memberJars = mapOf("intellij.demo.core" to setOf("demo-rt.jar")),
      derivedJars = mapOf("intellij.demo.core" to "modules/intellij.demo.core.jar"),
      closureMembers = setOf("intellij.demo.core"),
    )

    assertThat(derived.map { it.name }).containsExactly("plugins/demo/lib/demo-rt.jar", "plugins/demo/lib/demo.jar")
    assertThat(derived.single { it.name == "plugins/demo/lib/demo-rt.jar" }.contentModules).containsExactly("intellij.demo.core")
    val mainJar = derived.single { it.isMainJar }
    assertThat(mainJar.modules).containsExactly("intellij.demo")
    assertThat(mainJar.contentModules).isEmpty()
  }

  @Test
  fun `a derived jar with no member name derives no jar`() {
    // The precondition of `memberNames`: the caller already dropped a member with a jar and no module. The path alone
    // derives no jar, and it puts the member in no main jar either.
    val derived = derive(
      memberNames = emptyList(),
      derivedJars = mapOf("intellij.demo.core" to "modules/intellij.demo.core.jar"),
    )

    assertThat(derived.map { it.name }).containsExactly("plugins/demo/lib/demo.jar")
    val mainJar = derived.single { it.isMainJar }
    assertThat(mainJar.modules).containsExactly("intellij.demo")
    assertThat(mainJar.contentModules).isEmpty()
  }

  @Test
  fun `the main jar merges the co-packed members in content order and the main module last`() {
    val derived = derive(
      memberNames = listOf("intellij.demo.b", "intellij.demo.a"),
      derivedJars = mapOf("intellij.demo.b" to "demo.jar", "intellij.demo.a" to "demo.jar"),
      closureMembers = setOf("intellij.demo.a", "intellij.demo.b"),
    )

    assertThat(derived.single { it.isMainJar }.members).containsExactly("intellij.demo.b", "intellij.demo.a", "intellij.demo")
  }

  @Test
  fun `a jar the layout names reads its own destination, and never its members`() {
    // The plugin's own packing target packs such a jar, and that target states a destination. The members answer
    // another question, and the two answers differ for the case below.
    val members = listOf(MEMBER, "intellij.demo.extra")
    val statedJar = mapOf(MEMBER to setOf("rt/demo-rt.jar"), "intellij.demo.extra" to setOf("rt/demo-rt.jar"))

    assertThat(derive(memberNames = members, memberJars = statedJar).single { !it.isMainJar }.isHandedOver).isFalse()
    // Every member handed over through the member channel, and no target of this plugin.
    assertThat(
      derive(memberNames = members, memberJars = statedJar, handedOverMembers = members.toSet())
        .single { !it.isMainJar }.isHandedOver
    ).isFalse()
    assertThat(
      derive(memberNames = members, memberJars = statedJar, handedOverJars = setOf("rt/demo-rt.jar"))
        .single { !it.isMainJar }.isHandedOver
    ).isTrue()
  }

  @Test
  fun `a member of two jars the layout names moves both jars and keeps its declaration`() {
    // `intellij.maven.server.telemetry` is the case: the residue gives it two nested jars.
    val statedJars = mapOf(MEMBER to setOf("server3/telemetry.jar", "server4/telemetry.jar"))
    val jars = derive(
      memberNames = listOf(MEMBER),
      memberJars = statedJars,
      handedOverJars = setOf("server3/telemetry.jar", "server4/telemetry.jar"),
    )

    assertThat(jars.filter { !it.isMainJar }.map { it.isHandedOver }).containsExactly(true, true)
    // The residue states the member's whole jar set and no row of it names the main jar, so the main jar holds the
    // plugin's own module alone.
    assertThat(jars.single { it.isMainJar }.members).containsExactly("intellij.demo")
  }

  @Test
  fun `a content member in a nested layout jar keeps its own jar and a pure layout member does not`() {
    // `intellij.gateway.core` is the case: `<content>` gives it `modules/intellij.gateway.core.jar`, and the layout
    // adds `gateway-standalone/gateway.core.jar`. A pure `withModule` member has no jar of its own to keep.
    val nested = mapOf(MEMBER to setOf("standalone/core.jar"))
    val own = mapOf(MEMBER to "modules/$MEMBER.jar")

    val contentMember = derive(memberNames = listOf(MEMBER), memberJars = nested, derivedJars = own, closureMembers = setOf(MEMBER))
    assertThat(contentMember.map { it.relativeOutputFile }).containsExactly("modules/$MEMBER.jar", "standalone/core.jar", "demo.jar")

    val layoutMember = derive(memberNames = listOf(MEMBER), memberJars = nested, derivedJars = own)
    assertThat(layoutMember.map { it.relativeOutputFile }).containsExactly("standalone/core.jar", "demo.jar")

    // A flat custom jar replaces the member's own.
    val flat = derive(memberNames = listOf(MEMBER), memberJars = mapOf(MEMBER to setOf("custom.jar")), derivedJars = own, closureMembers = setOf(MEMBER))
    assertThat(flat.map { it.relativeOutputFile }).containsExactly("custom.jar", "demo.jar")
  }

  @Test
  fun `a main module embedded in its own content gets its own jar and the conventional main jar only for co-packed members`() {
    // `intellij.json` is the case: `lib/intellij.json.jar` holds the main module, and no `json.jar` is written.
    val alone = derive(memberNames = listOf(MEMBER), derivedJars = mapOf(MEMBER to "modules/$MEMBER.jar"), closureMembers = setOf(MEMBER), mainModuleJar = "intellij.demo.jar")
    assertThat(alone.map { it.relativeOutputFile }).containsExactly("modules/$MEMBER.jar", "intellij.demo.jar")
    assertThat(alone.single { it.isMainJar }.contentModules).containsExactly("intellij.demo")

    // A member the convention co-packs still lands in the conventional main jar, which then exists beside the module's own.
    val coPacked = derive(memberNames = listOf(MEMBER), derivedJars = mapOf(MEMBER to "demo.jar"), closureMembers = setOf(MEMBER), mainModuleJar = "intellij.demo.jar")
    assertThat(coPacked.map { it.relativeOutputFile }).containsExactly("intellij.demo.jar", "demo.jar")
    assertThat(coPacked.single { it.isMainJar }.members).containsExactly(MEMBER)
  }

  @Test
  fun `a handed-over jar states the union of its members' libraries and an unknown set states none`() {
    val known = derive(
      memberNames = listOf(MEMBER),
      derivedJars = mapOf(MEMBER to "modules/$MEMBER.jar"),
      closureMembers = setOf(MEMBER),
      handedOverMembers = setOf(MEMBER),
      memberLibraries = mapOf(MEMBER to setOf("beta", "alpha")),
    )
    assertThat(known.single { !it.isMainJar }.libraries).containsExactly("alpha", "beta")

    val unknown = derive(
      memberNames = listOf(MEMBER),
      derivedJars = mapOf(MEMBER to "modules/$MEMBER.jar"),
      closureMembers = setOf(MEMBER),
      handedOverMembers = setOf(MEMBER),
      memberLibraries = mapOf(MEMBER to null),
    )
    assertThat(unknown.single { !it.isMainJar }.libraries).isNull()

    // A jar no packing target packs states no library set, whatever the members declare.
    val kept = derive(
      memberNames = listOf(MEMBER),
      derivedJars = mapOf(MEMBER to "modules/$MEMBER.jar"),
      closureMembers = setOf(MEMBER),
      memberLibraries = mapOf(MEMBER to setOf("alpha")),
    )
    assertThat(kept.single { !it.isMainJar }.libraries).isNull()
  }

  @Test
  fun `a member whose module library has no name keeps its path and loses only the offer`() {
    // An unnamed library with no single jar is a module library all the same, so the path is the member's own jar.
    val unnameable = deriveMemberJar(libraries = null)

    assertThat(unnameable.relativeOutputFile).isEqualTo("modules/$MEMBER.jar")
    assertThat(unnameable.offer).isNull()
    // The same member with a library the derivation can name offers that jar.
    val nameable = deriveMemberJar(libraries = setOf("demo-library"))

    assertThat(nameable.relativeOutputFile).isEqualTo("modules/$MEMBER.jar")
    assertThat(nameable.offer!!.libraries).containsExactly("demo-library")
  }

  @Test
  fun `a lib root jar that merges a library is no offer`() {
    // The Kotlin plugin case: `lib/<module>.jar` is a jar of the member's own only when it merges no module library.
    val merging = deriveMemberJar(libraries = setOf("demo-library"), loadingRule = EMBEDDED_LOADING_RULE)
    assertThat(merging.relativeOutputFile).isEqualTo("$MEMBER.jar")
    assertThat(merging.offer).isNull()

    val plain = deriveMemberJar(libraries = emptySet(), loadingRule = EMBEDDED_LOADING_RULE)
    assertThat(plain.offer!!.relativeOutputFile).isEqualTo("$MEMBER.jar")
  }

  @Test
  fun `an embedded member that merges libraries derives the lib root jar`() {
    val path = derivePath(loadingRule = EMBEDDED_LOADING_RULE, hasPackageAttribute = true, mergesLibraries = true)

    assertThat(path).isEqualTo("$MEMBER.jar")
  }

  @Test
  fun `a member with no package attribute or with a library gets its own jar`() {
    // A member with a `package` attribute and no library is co-packed into the main jar.
    assertThat(derivePath(hasPackageAttribute = true)).isEqualTo("demo.jar")
    assertThat(derivePath(hasPackageAttribute = false)).isEqualTo("modules/$MEMBER.jar")
    assertThat(derivePath(hasPackageAttribute = true, mergesLibraries = true)).isEqualTo("modules/$MEMBER.jar")
  }

  @Test
  fun `a frontend member of a plugin that is not frontend-compatible splits`() {
    assertThat(derivePath(hasPackageAttribute = true, frontendSplit = true)).isEqualTo("modules/$MEMBER.jar")
  }

  /** One demo plugin's jars, from the facts [composeDerivedPluginJars] takes and a fixed placement. */
  private fun derive(
    memberNames: List<String>,
    memberJars: Map<String, Set<String>> = emptyMap(),
    derivedJars: Map<String, String> = emptyMap(),
    closureMembers: Set<String> = emptySet(),
    handedOverMembers: Set<String> = emptySet(),
    handedOverJars: Set<String> = emptySet(),
    memberLibraries: Map<String, Set<String>?> = emptyMap(),
    mainModuleJar: String? = null,
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
    memberLibraries = memberLibraries,
    mainModuleJar = mainModuleJar,
  )

  /** [deriveMemberJar] for [MEMBER] under the demo plugin's `demo.jar`. */
  private fun deriveMemberJar(libraries: Set<String>?, loadingRule: String? = null): DerivedMemberJar = deriveMemberJar(
    moduleName = MEMBER,
    loadingRule = loadingRule,
    hasPackageAttribute = true,
    libraries = libraries,
    isStated = false,
    mainJarName = "demo.jar",
  )

  /** [deriveMemberJarPath] for [MEMBER] under the demo plugin's `demo.jar`, with the convention's own defaults. */
  private fun derivePath(
    loadingRule: String? = null,
    hasPackageAttribute: Boolean = false,
    mergesLibraries: Boolean = false,
    frontendSplit: Boolean = false,
  ): String = deriveMemberJarPath(
    moduleName = MEMBER,
    loadingRule = loadingRule,
    hasPackageAttribute = hasPackageAttribute,
    mergesLibraries = mergesLibraries,
    mainJarName = "demo.jar",
    frontendSplit = frontendSplit,
  )

  private companion object {
    /** The one member the path cases state, so a jar name reads as the member's own or as the plugin's. */
    const val MEMBER: String = "intellij.demo.core"
  }
}
