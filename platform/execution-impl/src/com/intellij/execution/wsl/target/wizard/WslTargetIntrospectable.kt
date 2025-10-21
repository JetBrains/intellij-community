// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.concurrency.captureThreadContext
import com.intellij.execution.Platform
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.executeInShellAndGetCommandOnlyStdout
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.execution.ParametersListUtil
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class WslTargetIntrospectable(val distribution: WSLDistribution, val console: ConsoleView) : LanguageRuntimeType.Introspectable() {
  override val targetPlatform: CompletableFuture<TargetPlatform> = CompletableFuture.completedFuture(TargetPlatform(Platform.UNIX))

  override fun promiseEnvironmentVariable(varName: String): CompletableFuture<String?> =
    CompletableFuture.supplyAsync(captureThreadContext {
      distribution.getEnvironmentVariable(varName)
    })

  override fun promiseExecuteScript(script: List<String>): CompletableFuture<ProcessOutput> =
    CompletableFuture.supplyAsync(captureThreadContext {
      executeCommand(script)
    })

  @Deprecated("Use the override with List<String> parameter type")
  override fun promiseExecuteScript(script: String): CompletableFuture<String?> =
    promiseExecuteScript(ParametersListUtil.parse(script)).thenApply { output ->
      val success = !output.isTimeout && output.exitCode == 0
      if (!success) throw Exception(IdeBundle.message("wsl.target.introspection.step.command.finished.with.exit.code", output.exitCode))
      output.stdout
    }

  @RequiresBackgroundThread
  private fun executeCommand(cmd: List<@NlsSafe String>): ProcessOutput {
    val executeCommandInShell = cmd != listOf("pwd")
    val options = WSLCommandLineOptions().setExecuteCommandInShell(executeCommandInShell)
    if (executeCommandInShell) {
      options.isExecuteCommandInInteractiveShell = true
      options.isExecuteCommandInLoginShell = true
    }
    // Introspection runs in TTY
    val commandLine = PtyCommandLine(cmd).withRedirectErrorStream(true)
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
    return output
  }

  companion object {
    val LOG = logger<WslTargetIntrospectable>()
  }
}
