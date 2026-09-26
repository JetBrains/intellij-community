// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.BuildPaths.Companion.MAYBE_ULTIMATE_HOME
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class BazelMigratedTestModulesTest {
  @Test
  fun `the file in the repository follows the format`() {
    val modules = BazelMigratedTestModules.load(COMMUNITY_ROOT.communityRoot)
    assertThat(modules.patterns).isNotEmpty()
  }

  @Test
  fun `every pattern in the file matches a module of the ultimate project`() {
    assumeTrue(MAYBE_ULTIMATE_HOME != null, "The file can name an ultimate module, so the check needs the ultimate project model")
    val ultimateHome = requireNotNull(MAYBE_ULTIMATE_HOME)
    val moduleNames: List<String> = JpsSerializationManager.getInstance()
      .loadProject(ultimateHome.toString(), mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()), false)
      .modules.map { it.name }
    val unmatched = BazelMigratedTestModules.load(COMMUNITY_ROOT.communityRoot).patterns.filter { pattern ->
      val single = BazelMigratedTestModules.parse(listOf(pattern))
      moduleNames.none { it in single }
    }
    assertThat(unmatched).describedAs("patterns without a module in the project").isEmpty()
  }

  @Test
  fun `a module name matches only itself`() {
    val modules = BazelMigratedTestModules.parse(listOf("intellij.maven.tests"))
    assertThat("intellij.maven.tests" in modules).isTrue()
    assertThat("intellij.maven.tests.extra" in modules).isFalse()
    assertThat("intellij.maven.test" in modules).isFalse()
    assertThat("intellij.maven" in modules).isFalse()
    assertThat("Intellij.maven.tests" in modules).isFalse()
  }

  @Test
  fun `a prefix pattern matches every module name that starts with the prefix`() {
    val modules = BazelMigratedTestModules.parse(listOf("language-server.*"))
    assertThat("language-server.lsp.test" in modules).isTrue()
    assertThat("language-server." in modules).isTrue()
    assertThat("language-server" in modules).isFalse()
    assertThat("my.language-server.lsp" in modules).isFalse()
  }

  @Test
  fun `a comment and an empty line are skipped`() {
    val modules = BazelMigratedTestModules.parse(listOf("# comment", "", "#", "intellij.a.tests", ""))
    assertThat(modules.patterns).containsExactly("intellij.a.tests")
  }

  @Test
  fun `a bad line is reported with its number`() {
    assertThatThrownBy { BazelMigratedTestModules.parse(listOf("# comment", "intellij.a.tests", "intellij.*.tests"), source = "list.txt") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageStartingWith("list.txt:3: 'intellij.*.tests'")
  }

  @Test
  fun `a duplicate pattern is rejected`() {
    assertThatThrownBy { BazelMigratedTestModules.parse(listOf("intellij.a.tests", "intellij.a.tests")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("duplicate pattern 'intellij.a.tests'")
  }

  /** A glob for bash, a regular expression for `findstr`, or whitespace inside a module name. */
  @ParameterizedTest
  @ValueSource(strings = [
    "*",
    "*.tests",
    "intellij.*.tests",
    "intellij.tests.**",
    "intellij.tests?",
    "intellij.[ab].tests",
    "intellij.{a,b}.tests",
    "intellij.a.tests ",
    " intellij.a.tests",
    "\tintellij.a.tests",
    "intellij.a.tests # comment",
    " # comment",
    "intellij a tests",
    "plugins/maven/tests",
    "intellij.a.tests\r",
    "\uFEFF# a byte order mark",
    "модуль.tests",
  ])
  fun `a line that a shell parser reads differently is rejected`(line: String) {
    assertThatThrownBy { BazelMigratedTestModules.parse(listOf(line)) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining(":1:")
  }
}
