// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The repo-global fold of [foldDerivedPluginContentCandidacy]: an AND over every plugin's offers and vetoes. */
class PluginContentCandidacyFoldTest {
  @Test
  fun `an offered module with no veto is a candidate with its library set`() {
    val folded = foldDerivedPluginContentCandidacy(listOf(plugin(offers = listOf(offer(MEMBER, setOf("alpha"))))))

    assertThat(folded).isEqualTo(mapOf(MEMBER to setOf("alpha")))
  }

  @Test
  fun `a veto of one plugin refuses the module whatever order the plugins are read in`() {
    val offering = plugin(offers = listOf(offer(MEMBER, emptySet())))
    val vetoing = plugin(vetoes = listOf(MEMBER))

    assertThat(foldDerivedPluginContentCandidacy(listOf(offering, vetoing))).isEmpty()
    assertThat(foldDerivedPluginContentCandidacy(listOf(vetoing, offering))).isEmpty()
  }

  @Test
  fun `a stated library set beats a derived one`() {
    val derived = plugin(offers = listOf(offer(MEMBER, setOf("alpha", "beta"))))
    val stated = plugin(offers = listOf(offer(MEMBER, setOf("alpha"), isStated = true)))

    assertThat(foldDerivedPluginContentCandidacy(listOf(derived, stated))).isEqualTo(mapOf(MEMBER to setOf("alpha")))
    assertThat(foldDerivedPluginContentCandidacy(listOf(stated, derived))).isEqualTo(mapOf(MEMBER to setOf("alpha")))
  }

  @Test
  fun `two stated sets that differ veto the module`() {
    val first = plugin(offers = listOf(offer(MEMBER, setOf("alpha"), isStated = true)))
    val second = plugin(offers = listOf(offer(MEMBER, setOf("beta"), isStated = true)))

    assertThat(foldDerivedPluginContentCandidacy(listOf(first, second))).isEmpty()
  }

  @Test
  fun `two derived sets that differ veto the module`() {
    val first = plugin(offers = listOf(offer(MEMBER, setOf("alpha"))))
    val second = plugin(offers = listOf(offer(MEMBER, setOf("beta"))))

    assertThat(foldDerivedPluginContentCandidacy(listOf(first, second))).isEmpty()
  }

  @Test
  fun `a vetoed module never comes back through a later stated offer`() {
    val vetoing = plugin(vetoes = listOf(MEMBER))
    val stated = plugin(offers = listOf(offer(MEMBER, setOf("alpha"), isStated = true)))

    assertThat(foldDerivedPluginContentCandidacy(listOf(vetoing, stated))).isEmpty()
  }

  private fun plugin(offers: List<DerivedCandidacyOffer> = emptyList(), vetoes: List<String> = emptyList()): DerivedPluginCandidacy {
    return DerivedPluginCandidacy(offers = offers, vetoes = vetoes, memberPaths = emptyMap(), memberLibraries = emptyMap())
  }

  private fun offer(moduleName: String, libraries: Set<String>, isStated: Boolean = false): DerivedCandidacyOffer {
    return DerivedCandidacyOffer(moduleName = moduleName, relativeOutputFile = "modules/$moduleName.jar", libraries = libraries, isStated = isStated)
  }

  private companion object {
    const val MEMBER: String = "intellij.demo.core"
  }
}
