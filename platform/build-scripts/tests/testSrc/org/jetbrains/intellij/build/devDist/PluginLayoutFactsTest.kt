// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.PluginLayout
import org.junit.jupiter.api.Test

/** The union of one plugin's layouts into [PluginLayoutFacts]. */
class PluginLayoutFactsTest {
  @Test
  fun `no layout gives the convention and no member`() {
    val facts = pluginLayoutFacts(mainModule = "intellij.demo.plugin", layouts = emptyList())

    assertThat(facts.directoryName).isEqualTo("demo-plugin")
    assertThat(facts.mainJarName).isEqualTo("demo-plugin.jar")
    assertThat(facts.memberJars).isEmpty()
    assertThat(facts.noEmbedding).isFalse()
    // The build gives a plugin with no layout an `auto` one.
    assertThat(facts.auto).isTrue()
  }

  @Test
  fun `a plugin is auto when one of its layouts is`() {
    val plain = PluginLayout.plugin(MAIN) { }
    val auto = PluginLayout.pluginAuto(MAIN) { }

    assertThat(pluginLayoutFacts(mainModule = MAIN, layouts = listOf(plain)).auto).isFalse()
    assertThat(pluginLayoutFacts(mainModule = MAIN, layouts = listOf(plain, auto)).auto).isTrue()
  }

  @Test
  fun `the convention drops the intellij prefix and joins the rest with dashes`() {
    assertThat(pluginJarPlacementConvention("intellij.demo.plugin").directory).isEqualTo("demo-plugin")
    assertThat(pluginJarPlacementConvention("fleet.demo").mainJarName).isEqualTo("fleet-demo.jar")
  }

  @Test
  fun `two layouts of one plugin union their members and libraries in sorted order`() {
    val first = PluginLayout.plugin(MAIN) { spec ->
      spec.withModule("intellij.demo.rt", "demo-rt.jar")
      spec.withModule("intellij.demo.b")
      spec.withProjectLibrary("project-library")
      spec.doNotCopyModuleLibrariesAutomatically(listOf("intellij.demo.unmerged"))
    }
    val second = PluginLayout.plugin(MAIN) { spec ->
      spec.withModule("intellij.demo.a")
      spec.withModule("intellij.demo.rt", "rt/demo-rt.jar")
      spec.withModuleLibrary("owned", "intellij.demo.owner", "")
      spec.excludeModuleLibrary("excluded", "intellij.demo.core")
      spec.withGeneratedResources(listOf("generated")) { _, _ -> }
    }

    val facts = pluginLayoutFacts(mainModule = MAIN, layouts = listOf(second, first))

    assertThat(facts.directoryName).isEqualTo("demo")
    assertThat(facts.mainJarName).isEqualTo("demo.jar")
    // The main module's own item is not a member. A plain `withModule(name)` states the main jar as the path.
    assertThat(facts.memberJars.keys).containsExactly("intellij.demo.a", "intellij.demo.b", "intellij.demo.rt")
    assertThat(facts.memberJars.getValue("intellij.demo.b")).containsExactly("demo.jar")
    assertThat(facts.memberJars.getValue("intellij.demo.rt")).containsExactly("demo-rt.jar", "rt/demo-rt.jar")
    assertThat(facts.unmergedMembers).containsExactly("intellij.demo.unmerged")
    assertThat(facts.excludedModuleLibraries).isEqualTo(mapOf("intellij.demo.core" to setOf("excluded")))
    assertThat(facts.projectLibraries).isEqualTo(mapOf<String, String?>("project-library" to null))
    assertThat(facts.moduleLibraries).hasSize(1)
    assertThat(facts.moduleLibraries.single().moduleName).isEqualTo("intellij.demo.owner")
    assertThat(facts.moduleLibraries.single().libraryName).isEqualTo("owned")
    assertThat(facts.moduleLibraries.single().relativeOutputPath).isNull()
    assertThat(facts.generatorLibraries).containsExactly("generated")
    assertThat(facts.noEmbedding).isFalse()
  }

  @Test
  fun `a scrambled layout states no embedding`() {
    val layout = PluginLayout.plugin(MAIN) { spec -> spec.scramble("demo.jar") }

    assertThat(pluginLayoutFacts(mainModule = MAIN, layouts = listOf(layout)).noEmbedding).isTrue()
  }

  @Test
  fun `two layouts that disagree on the placement fail and name the plugin`() {
    val plain = PluginLayout.plugin(MAIN) { }
    val renamed = PluginLayout.plugin(MAIN) { spec -> spec.directoryName = "Demo" }
    val renamedJar = PluginLayout.plugin(MAIN) { spec -> spec.mainJarName = "demo-plugin.jar" }

    assertThatThrownBy { pluginLayoutFacts(mainModule = MAIN, layouts = listOf(plain, renamed)) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(MAIN)
      .hasMessageContaining("directory name")
    assertThatThrownBy { pluginLayoutFacts(mainModule = MAIN, layouts = listOf(plain, renamedJar)) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(MAIN)
      .hasMessageContaining("main jar name")
  }

  @Test
  fun `a layout of another plugin is refused`() {
    val other = PluginLayout.plugin("intellij.other") { }

    assertThatThrownBy { pluginLayoutFacts(mainModule = MAIN, layouts = listOf(other)) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining(MAIN)
  }

  private companion object {
    const val MAIN: String = "intellij.demo"
  }
}
