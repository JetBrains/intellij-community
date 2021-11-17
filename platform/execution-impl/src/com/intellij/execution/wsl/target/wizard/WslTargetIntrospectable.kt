// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.executeInShellAndGetCommandOnlyStdout
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class WslTargetIntrospectable(val distribution: WSLDistribution, val console: ConsoleView) : LanguageRuntimeType.Introspectable() {

  override fun promiseEnvironmentVariable(varName: String): CompletableFuture<String?> {
    try {
      val value = distribution.getEnvironmentVariable(varName)
      console.print("$varName=$value\n\n", ConsoleViewContentType.NORMAL_OUTPUT)
      return CompletableFuture.completedFuture(value)
    }
    catch (t: Throwable) {
      return CompletableFuture.failedFuture(t)
    }
  }

  override fun promiseExecuteScript(script: String): CompletableFuture<String?> {
    try {
      return CompletableFuture.completedFuture(executeCommand(script.split(" ")))
    }
    catch (t: Throwable) {
      return CompletableFuture.failedFuture(t)
    }
  }

  private fun executeCommand(cmd: List<@NlsSafe String>): String {
    val executeCommandInShell = cmd != listOf("pwd")
    val options = WSLCommandLineOptions().setExecuteCommandInShell(executeCommandInShell)
    if (executeCommandInShell) {
      options.shellPath = distribution.shellPath
      options.isExecuteCommandInInteractiveShell = true
      options.isExecuteCommandInLoginShell = true
    }
    val commandLine = GeneralCommandLine(cmd).withRedirectErrorStream(true)
    val output = if (executeCommandInShell) {
      distribution.executeInShellAndGetCommandOnlyStdout(commandLine, options, 10_000,
                                                         Consumer<ProcessHandler> { console.attachToProcess(it) })
    }
    else {
      distribution.patchCommandLine(commandLine, null, options)
      val processHandler = KillableProcessHandler(commandLine)
      console.attachToProcess(processHandler)
      CapturingProcessRunner(processHandler).runProcess(10_000)
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("Command $cmd finished: " +
                "stdout=${output.stdout}, stderr=${output.stderr}, " +
                "timeout=${output.isTimeout}, exitCode=${output.exitCode}")
    }

    val success = !output.isTimeout && output.exitCode == 0
    console.print(IdeBundle.message("wsl.target.introspection.step.command.finished.with.exit.code", output.exitCode) + "\n\n",
                  if (success) ConsoleViewContentType.SYSTEM_OUTPUT else ConsoleViewContentType.ERROR_OUTPUT)
    if (success) {
      return output.stdout
    }
    throw Exception(IdeBundle.message("wsl.target.introspection.step.command.finished.with.exit.code", output.exitCode))
  }

  companion object {
    val LOG = logger<WslTargetIntrospectable>()
  }
}
