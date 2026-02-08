// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.completion

import com.intellij.execution.vmOptions.JdkOptionsData
import com.intellij.execution.vmOptions.VMOption
import com.intellij.execution.vmOptions.VMOptionKind
import com.intellij.execution.vmOptions.VMOptionVariant
import com.intellij.execution.vmOptions.VMOptionsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellFileInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import junit.framework.TestCase
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessOptions
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import java.util.concurrent.CompletableFuture

internal abstract class JdkCommandsShellSpecsProviderTestBase(private val engine: TerminalEngine) : BasePlatformTestCase() {
  protected fun createFixture(javaVersion: Int = 11): ShellCompletionTestFixture {
    ApplicationManager.getApplication().replaceService(VMOptionsService::class.java, MockVMOptionsService(), testRootDisposable)
    val fixture = ShellCompletionTestFixture.builder(project)
      .setIsReworkedTerminal(engine == TerminalEngine.REWORKED)
      .mockProcessesExecutor(object : ShellDataGeneratorProcessExecutor {
        override suspend fun executeProcess(options: ShellDataGeneratorProcessOptions): ShellCommandResult {
          if (options.executable == "java" && options.args == listOf("-XshowSettings:properties", "-version")) {
            return ShellCommandResult.create("java.home = /jre/home\njava.version = $javaVersion", exitCode = 0)
          }
          else if (options.executable == "__jetbrains_intellij_get_directory_files") {
            return ShellCommandResult.create("file1.jar\nfile2.jar\ndir1/", exitCode = 0)
          }
          return ShellCommandResult.create("", exitCode = 1)
        }
      })
      .mockShellCommandResults { command ->
        if (command.startsWith("__jetbrains_intellij_get_directory_files")) {
          return@mockShellCommandResults ShellCommandResult.create("file1.jar\nfile2.jar\ndir1/", exitCode = 0)
        }
        return@mockShellCommandResults ShellCommandResult.create("", exitCode = 1)
      }
      .mockFileSystemSupport(object : ShellFileSystemSupport {
        override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
          return listOf(
            ShellFileInfo.create("file1.jar", ShellFileInfo.Type.FILE),
            ShellFileInfo.create("file2.jar", ShellFileInfo.Type.FILE),
            ShellFileInfo.create("dir1", ShellFileInfo.Type.DIRECTORY),
          )
        }
      })
      .build()
    return fixture
  }

  protected suspend fun ShellCompletionTestFixture.getCompletionNames(command: String = "java "): List<String> {
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

    override fun getOrComputeOptionsForJavac(javaHome: String): CompletableFuture<JdkOptionsData> {
      TestCase.assertEquals("/jre/home", javaHome)
      return CompletableFuture.completedFuture(
        JdkOptionsData(
          listOf(
            VMOption("boot-class-path", null, null, VMOptionKind.Standard, null, VMOptionVariant.DASH_DASH),
            VMOption("enable-preview", null, null, VMOptionKind.Standard, null, VMOptionVariant.DASH_DASH),
            VMOption("release", null, null, VMOptionKind.Standard, null, VMOptionVariant.DASH_DASH),
            VMOption("source", null, null, VMOptionKind.Standard, null, VMOptionVariant.DASH_DASH),
            VMOption("target", null, null, VMOptionKind.Standard, null, VMOptionVariant.DASH_DASH),
            VMOption("experimental", null, null, VMOptionKind.Experimental, null, VMOptionVariant.DASH_DASH),
            VMOption("lint", null, null, VMOptionKind.Product, null, VMOptionVariant.X),
            VMOption("bootclasspath:", null, null, VMOptionKind.Product, null, VMOptionVariant.X),
          )
        )
      )
    }
  }
}
