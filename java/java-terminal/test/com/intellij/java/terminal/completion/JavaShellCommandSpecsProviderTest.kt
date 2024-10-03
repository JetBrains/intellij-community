// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.completion

import com.intellij.execution.vmOptions.*
import com.intellij.java.terminal.JavaShellCommandContext
import com.intellij.java.terminal.JavaShellCommandUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.util.ShellCompletionTestFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CompletableFuture

@RunWith(JUnit4::class)
class JavaShellCommandSpecsProviderTest : BasePlatformTestCase() {
  @Test
  fun `default options are present`() = runBlocking {
    val fixture = ShellCompletionTestFixture.builder(project).mockShellCommandResults { _ ->
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
  fun `classpath suggestion generator simple`() = runBlocking {
    val fixture = createFixture()
    val completion =  fixture.getCompletions("java -cp ")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == 0})
  }

  @Test
  fun `classpath suggestion generator after colon`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "file1.jar$separator"
    val completion =  fixture.getCompletions("java -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length})
  }

  @Test
  fun `classpath suggestion generator with double colon`() = runBlocking {
    val separator = JavaShellCommandUtils.getClassPathSeparator()
    val fixture = createFixture()
    val argument = "file1.jar$separator$separator"
    val completion =  fixture.getCompletions("java -cp $argument")
    assertSameElements(completion.map { it.name  }, listOf("file1.jar", "file2.jar", "dir1/"))
    assertTrue(completion.all { it.prefixReplacementIndex == argument.length})
  }

  private fun createFixture(javaVersion: Int = 11): ShellCompletionTestFixture {
    ApplicationManager.getApplication().replaceService(VMOptionsService::class.java, MockVMOptionsService(), testRootDisposable)
    val fixture = ShellCompletionTestFixture.builder(project).mockShellCommandResults { command ->
      if (command == JavaShellCommandContext.JAVA_SHOW_SETTINGS_PROPERTIES_VERSION_COMMAND) {
        return@mockShellCommandResults ShellCommandResult.create("java.home = /jre/home\njava.version = ${javaVersion}", exitCode = 0)
      }

      if (command.startsWith("__jetbrains_intellij_get_directory_files")) {
        return@mockShellCommandResults ShellCommandResult.create("file1.jar\nfile2.jar\ndir1/", exitCode = 0)
      }

      return@mockShellCommandResults ShellCommandResult.create("", exitCode = 1)
    }.build()
    return fixture
  }

  private suspend fun ShellCompletionTestFixture.getCompletionNames(command: String = "java "): List<String> {
    val actual: List<ShellCompletionSuggestion> = getCompletions(command)
    return actual.map { it.name }
  }

  private class MockVMOptionsService : VMOptionsService {
    override fun getOrComputeOptionsForJdk(javaHome: String): CompletableFuture<JdkOptionsData> {
      TestCase.assertEquals("/jre/home", javaHome)
      return CompletableFuture.completedFuture(
        JdkOptionsData(
        listOf(
          VMOption("settings", null, null, VMOptionKind.Product, null, VMOptionVariant.X),
          VMOption("lint", null, null, VMOptionKind.Standard, null, VMOptionVariant.X),
          VMOption("experiment", null, null, VMOptionKind.Experimental, null, VMOptionVariant.X),
          VMOption("diagnose", null, null, VMOptionKind.Diagnostic, null, VMOptionVariant.X),
          VMOption("advanced", null, null, VMOptionKind.Product, null, VMOptionVariant.XX),
          VMOption("add-opens", null, null, VMOptionKind.Product, null, VMOptionVariant.DASH_DASH),
          VMOption("add-exports", null, null, VMOptionKind.Standard, null, VMOptionVariant.DASH_DASH),
          VMOption("add-experiment-exports", null, null, VMOptionKind.Experimental, null, VMOptionVariant.DASH_DASH),
          VMOption("add-diagnostic-exports", null, null, VMOptionKind.Experimental, null, VMOptionVariant.DASH_DASH),
        )
        )
      )
    }
  }
}