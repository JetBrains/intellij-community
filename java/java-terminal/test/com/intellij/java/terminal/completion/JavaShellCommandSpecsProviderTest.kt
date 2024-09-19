// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.completion

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.util.ShellCompletionTestFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavaShellCommandSpecsProviderTest : BasePlatformTestCase() {
  @Test
  fun `default options are present`() = runBlocking {
    val fixture = ShellCompletionTestFixture.builder(project).build()
    val actual: List<ShellCompletionSuggestion> = fixture.getCompletions("java ")
    assertSameElements(actual.map { it.name }, listOf("--help", "-help", "-h",
                                                      "-jar",
                                                      "-D",
                                                      "--version", "-version",
                                                      "-classpath", "-cp",
                                                      "-showversion", "--show-version",
                                                      "--dry-run"))
  }
}