// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.completion

import com.intellij.java.terminal.JavaShellCommandUtils
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessOptions
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class JavacShellCommandSpecsProviderTest(engine: TerminalEngine) : JdkCommandsShellSpecsProviderTestBase(engine) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun engine(): List<TerminalEngine> = listOf(TerminalEngine.REWORKED, TerminalEngine.NEW_TERMINAL)
  }

  @Test
  fun `default options are present`() = runBlocking {
    val fixture = ShellCompletionTestFixture.builder(project)
      .mockProcessesExecutor(object : ShellDataGeneratorProcessExecutor {
        override suspend fun executeProcess(options: ShellDataGeneratorProcessOptions): ShellCommandResult {
          return ShellCommandResult.create("", exitCode = 1)
        }
      })
      .mockShellCommandResults { _ ->
      return@mockShellCommandResults ShellCommandResult.create("", exitCode = 1)
    }.build()
    assertSameElements(fixture.getCompletionNames("javac "), listOf(
      "-A", "-g", "-g:", "-g:none", "-h", "-J", "-d", "-nowarn", "-parameters", "-processor",
      "-profile", "-s", "-verbose", "-Werror", "-proc:", "-implicit:",
      "-encoding", "-endorseddirs", "-extdirs", "-cp", "-classpath", "-?", "-help", "-X", "-version"))
  }

  @Test
  fun `double dash options are present`() = runBlocking {
    val fixture = createFixture()
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames("javac "), listOf("--enable-preview", "--release", "--source", "--target"))
  }

  @Test
  fun `classpath suggestion generator`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "file1.jar$separator"
    val completion =  fixture.getCompletions("javac -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length })
  }

  @Test
  fun `boot classpath suggestion generator`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "file1.jar$separator"
    val completion =  fixture.getCompletions("javac --boot-class-path $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length })
  }
}


