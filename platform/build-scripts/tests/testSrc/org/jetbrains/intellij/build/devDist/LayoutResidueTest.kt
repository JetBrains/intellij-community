// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** What the layout facts of one plugin imply for its content; see [layoutResidueOf]. */
class LayoutResidueTest {
  @Test
  fun `the layout facts imply the residue rows a build used to state`() {
    val facts = PluginLayoutFacts(
      directoryName = "demo",
      mainJarName = "demo.jar",
      memberJars = mapOf(
        "intellij.demo.plain" to setOf("demo.jar"),
        "intellij.demo.rt" to setOf("demo-rt.jar"),
        "intellij.demo.ns" to setOf("demo.jar", "ns/ns.jar"),
      ),
      unmergedMembers = setOf("intellij.demo.unmerged"),
      excludedModuleLibraries = mapOf("intellij.demo.core" to setOf("excluded")),
      projectLibraries = mapOf("project-library" to null),
      moduleLibraries = listOf(LayoutModuleLibrary(moduleName = "intellij.demo.owner", libraryName = "owned", relativeOutputPath = null)),
      generatorLibraries = setOf("generated"),
    )

    val residue = layoutResidueOf(mainModule = "intellij.demo", facts = facts)

    // Every layout member is a member. A plain `withModule(name)` states the main jar, because the build packs the item
    // there whatever descriptor the module has, and a member the layout names the main jar for beside another jar keeps
    // both.
    assertThat(residue.extraMembers).containsExactlyInAnyOrder("intellij.demo.plain", "intellij.demo.rt", "intellij.demo.ns")
    assertThat(residue.memberJars).isEqualTo(mapOf(
      "intellij.demo.plain" to setOf("demo.jar"),
      "intellij.demo.rt" to setOf("demo-rt.jar"),
      "intellij.demo.ns" to setOf("demo.jar", "ns/ns.jar"),
    ))
    // A `<content>` member loses the main jar, because the build skips a flat layout item of a member its `<content>`
    // already packed, and it gets no row when nothing else is left.
    val closure = layoutResidueOf(mainModule = "intellij.demo", facts = facts, closureMembers = setOf("intellij.demo.plain", "intellij.demo.ns"))
    assertThat(closure.memberJars).isEqualTo(mapOf("intellij.demo.rt" to setOf("demo-rt.jar"), "intellij.demo.ns" to setOf("ns/ns.jar")))
    // A `withModuleLibrary` owner is vetoed, and its library leaves every member jar.
    assertThat(residue.vetoedMembers).containsExactly("intellij.demo.owner")
    assertThat(residue.takenOutLibraries).containsExactly("owned")
    assertThat(residue.libraries).containsExactly(
      RecordedLibrary(name = "project-library", ownerModule = null),
      RecordedLibrary(name = "owned", ownerModule = "intellij.demo.owner"),
      RecordedLibrary(name = "generated", ownerModule = null),
    )
    assertThat(residue.unmergedMembers).containsExactly("intellij.demo.unmerged")
    assertThat(residue.excludedModuleLibraries).isEqualTo(mapOf("intellij.demo.core" to setOf("excluded")))
  }

  @Test
  fun `facts that state nothing imply no residue`() {
    val facts = pluginLayoutFacts(mainModule = "intellij.demo", layouts = emptyList())

    assertThat(layoutResidueOf(mainModule = "intellij.demo", facts = facts)).isSameAs(PluginContentResidue.NONE)
  }

  @Test
  fun `a content member in a jar the layout names is a raw member and a pure layout member is not`() {
    // The `intellij.demo.flat` module is the flat case. The `flat.jar` file replaces the module JAR.
    val residue = PluginContentResidue(
      memberJars = mapOf(
        "intellij.demo.core" to setOf("standalone/core.jar"),
        "intellij.demo.rt" to setOf("rt/rt.jar"),
        "intellij.demo.flat" to setOf("flat.jar"),
        "intellij.demo.main" to setOf("demo.jar"),
      ),
    )

    val closureMembers = setOf("intellij.demo.core", "intellij.demo.flat", "intellij.demo.main")
    assertThat(layoutJarMembers(residue = residue, closureMembers = closureMembers, mainJarName = "demo.jar"))
      .containsExactly("intellij.demo.core", "intellij.demo.flat")
  }

  @Test
  fun `a layout member of the main jar and another jar keeps both`() {
    // `intellij.javaee.appServers.glassfish.agent.rt` is the case: the layout packs it into the main jar and into the
    // specifics jars.
    val facts = PluginLayoutFacts(
      directoryName = "demo",
      mainJarName = "demo.jar",
      memberJars = mapOf("intellij.demo.rt" to setOf("demo.jar", "specifics/demo2.jar"), "intellij.demo.plain" to setOf("demo.jar")),
    )

    val residue = layoutResidueOf(mainModule = "intellij.demo", facts = facts)

    assertThat(residue.memberJars).isEqualTo(mapOf("intellij.demo.rt" to setOf("demo.jar", "specifics/demo2.jar"), "intellij.demo.plain" to setOf("demo.jar")))
    assertThat(residue.extraMembers).containsExactlyInAnyOrder("intellij.demo.plain", "intellij.demo.rt")

    // A `<content>` member loses the main jar: the build packs such a member from its `<content>` first, and then skips
    // the flat layout item. `intellij.gateway.core` is the case.
    val content = layoutResidueOf(mainModule = "intellij.demo", facts = facts, closureMembers = setOf("intellij.demo.rt"))
    assertThat(content.memberJars).isEqualTo(mapOf("intellij.demo.rt" to setOf("specifics/demo2.jar"), "intellij.demo.plain" to setOf("demo.jar")))
  }

  @Test
  fun `two residues join, and the second wins a member's jar set by union`() {
    val first = PluginContentResidue(extraMembers = setOf("intellij.demo.rt"), memberJars = mapOf("intellij.demo.rt" to setOf("demo-rt.jar")))
    val second = PluginContentResidue(vetoedMembers = setOf("intellij.demo.vetoed"), memberJars = mapOf("intellij.demo.rt" to setOf("rt/demo-rt.jar")))

    val joined = first + second

    assertThat(joined.memberJars).isEqualTo(mapOf("intellij.demo.rt" to setOf("demo-rt.jar", "rt/demo-rt.jar")))
    assertThat(joined.vetoedMembers).containsExactly("intellij.demo.vetoed")
    assertThat(joined.extraMembers).containsExactly("intellij.demo.rt")
  }
}
