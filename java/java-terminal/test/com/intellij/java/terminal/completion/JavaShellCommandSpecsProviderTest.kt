// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.completion

import com.intellij.execution.vmOptions.*
import com.intellij.java.terminal.JavaShellCommandContext
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
    assertSameElements(fixture.getCompletionNames(), listOf("-?", "-help", "-h", "-jar", "-D", "-version", "-classpath", "-cp", "-showversion"))
  }

  @Test
  fun `double dashed options are present only from java 11`() = runBlocking {
    val fixture = ShellCompletionTestFixture.builder(project).mockShellCommandResults { command ->
      if (command == JavaShellCommandContext.JAVA_SHOW_SETTINGS_PROPERTIES_VERSION_COMMAND) {
        return@mockShellCommandResults ShellCommandResult.create("java.version = 11", exitCode = 0)
      }
      return@mockShellCommandResults ShellCommandResult.create("", exitCode = 1)
    }.build()
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames(), listOf("--show-version", "--help", "--version", "--dry-run"))
  }

  @Test
  fun `x options are present`() = runBlocking {
    ApplicationManager.getApplication().replaceService(VMOptionsService::class.java, MockVMOptionsService(), testRootDisposable)
    val fixture = ShellCompletionTestFixture.builder(project).mockShellCommandResults { command ->
      if (command == JavaShellCommandContext.JAVA_SHOW_SETTINGS_PROPERTIES_VERSION_COMMAND) {
        return@mockShellCommandResults ShellCommandResult.create("java.home = /jre/home", exitCode = 0)
      }
      return@mockShellCommandResults ShellCommandResult.create("", exitCode = 1)
    }.build()
    UsefulTestCase.assertContainsElements(fixture.getCompletionNames(), listOf("-Xsettings", "-Xlint"))
    UsefulTestCase.assertDoesntContain(fixture.getCompletionNames(), listOf("-Xexperiment", "-Xdiagnose", "-XXdoubleDash"))
  }

  private suspend fun ShellCompletionTestFixture.getCompletionNames(): List<String> {
    val actual: List<ShellCompletionSuggestion> = getCompletions("java ")
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
          VMOption("doubleDash", null, null, VMOptionKind.Product, null, VMOptionVariant.XX),
        )
        )
      )
    }

  }
}