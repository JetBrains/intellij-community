// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
internal class JavaShellCommandSpecsProviderTest(engine: TerminalEngine) : JdkCommandsShellSpecsProviderTestBase(engine) {
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
    assertSameElements(fixture.getCompletionNames(), listOf("-ea", "-enableassertions", "-da", "-disableassertions", "-esa", "-enablesystemassertions",
                                                            "-dsa", "-disablesystemassertions", "-agentpath:", "-agentlib:", "-javaagent:",
                                                            "-D", "-XX:", "-?", "-help", "-h", "-jar", "-version", "-classpath", "-cp", "-showversion"))
  }

  @Test
  fun `java 11 dynamic options are present`() = runBlocking {
    val fixture = createFixture(11)
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames(), listOf("--show-version", "--help", "--version", "--dry-run", "--class-path", "--help", "--enable-preview", "-verbose"))
  }

  @Test
  fun `java 8 dynamic options are present`() = runBlocking {
    val fixture = createFixture(8)
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames(), listOf("-verbose"))
  }

  @Test
  fun `x options are present`() = runBlocking {
    val fixture = createFixture()
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames(), listOf("-Xsettings", "-Xlint"))
    UsefulTestCase.assertDoesntContain(fixture.getCompletionNames(), listOf("-Xexperiment", "-Xdiagnose", "-XXadvanced"))
  }

  @Test
  fun `double dash options are present`() = runBlocking {
    val fixture = createFixture()
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames(), listOf("--add-opens", "--add-exports"))
    UsefulTestCase.assertDoesntContain(fixture.getCompletionNames(), listOf("--add-experimental-exports", "--add-diagnostic-exports", "-XXadvanced"))
  }

  @Test
  fun `classpath suggestion generator with single quote`() = runBlocking {
    val fixture = createFixture()
    val completion =  fixture.getCompletions("java -cp '")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == 0 })
  }

  @Test
  fun `classpath suggestion generator single quote and after separator`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "'file1.jar$separator"
    val completion =  fixture.getCompletions("java -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length - 1 })
  }

  @Test
  fun `classpath suggestion generator with double quote`() = runBlocking {
    val fixture = createFixture()
    val completion =  fixture.getCompletions("java -cp \"")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == 0 })
  }

  @Test
  fun `classpath suggestion generator with double quote and after separator`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "\"file1.jar$separator"
    val completion =  fixture.getCompletions("java -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length - 1 })
  }

  @Test
  fun `classpath suggestion generator simple`() = runBlocking {
    val fixture = createFixture()
    val completion =  fixture.getCompletions("java -cp ")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == 0})
  }

  @Test
  fun `classpath suggestion generator after separator`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "file1.jar$separator"
    val completion =  fixture.getCompletions("java -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length})
  }

  @Test
  fun `classpath suggestion generator after double separator`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "file1.jar$separator$separator"
    val completion =  fixture.getCompletions("java -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length})
  }
}