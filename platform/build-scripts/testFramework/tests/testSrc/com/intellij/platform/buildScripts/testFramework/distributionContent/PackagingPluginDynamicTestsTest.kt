// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.opentest4j.TestAbortedException

class PackagingPluginDynamicTestsTest {
  @Test
  fun `creates target success test when no plugin failures`() {
    val tests = createPluginContentDynamicTests(targetId = "ultimate", checkPlugins = true)

    assertThat(tests.map { it.displayName }).containsExactly("ultimate")
    assertThatCode {
      tests.single().executable.execute()
    }.doesNotThrowAnyException()
  }

  @Test
  fun `creates one dynamic test per plugin failure`() {
    val firstFailure = PackagingCheckFailure(
      name = "ultimate bundled-plugin: intellij.spring",
      error = AssertionError("first failure"),
    )
    val secondFailure = PackagingCheckFailure(
      name = "ultimate bundled-plugin: intellij.javaFX",
      error = IllegalStateException("second failure"),
    )

    val tests = createPluginContentDynamicTests(
      targetId = "ultimate",
      checkPlugins = true,
      failures = listOf(firstFailure, secondFailure),
    )

    assertThat(tests.map { it.displayName }).containsExactly(firstFailure.name, secondFailure.name)
    assertThat(catchThrowable { tests[0].executable.execute() }).isSameAs(firstFailure.error)
    assertThat(catchThrowable { tests[1].executable.execute() }).isSameAs(secondFailure.error)
  }

  @Test
  fun `creates target-level aborted test when packaging failed`() {
    val packagingFailure = TestAbortedException(
      "Plugin content check for ultimate skipped because packaging failed",
      IllegalStateException("packaging failed"),
    )

    val tests = createPluginContentDynamicTests(
      targetId = "ultimate",
      checkPlugins = true,
      failure = packagingFailure,
    )

    assertThat(tests.map { it.displayName }).containsExactly("ultimate")
    assertThat(catchThrowable { tests.single().executable.execute() }).isSameAs(packagingFailure)
  }

  @Test
  fun `creates target success test when plugin checks are disabled`() {
    val tests = createPluginContentDynamicTests(
      targetId = "code-server",
      checkPlugins = false,
      failures = listOf(PackagingCheckFailure("ignored failure", AssertionError("ignored"))),
      failure = AssertionError("ignored"),
    )

    assertThat(tests.map { it.displayName }).containsExactly("code-server")
    assertThatCode {
      tests.single().executable.execute()
    }.doesNotThrowAnyException()
  }
}
