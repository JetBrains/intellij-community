// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("WslExecution")

package com.intellij.execution.wsl

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.wsl.WSLUtil.LOG
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EnvironmentUtil
import com.intellij.util.LineSeparator
import com.intellij.util.containers.ContainerUtil
import java.util.function.Consumer

@JvmOverloads
@Throws(ExecutionException::class)
fun WSLDistribution.executeInShellAndGetCommandOnlyStdout(commandLine: GeneralCommandLine,
                                                          options: WSLCommandLineOptions,
                                                          timeout: Int,
                                                          processHandlerCustomizer: Consumer<ProcessHandler> = Consumer {}): ProcessOutput {
  if (!options.isExecuteCommandInShell) {
    throw AssertionError("Execution in shell is expected")
  }
  // When command is executed in interactive/login shell, the result stdout may contain additional output
  // produced by shell configuration files, for example, "Message Of The Day".
  // Let's print some unique message before executing the command to know where command output begins in the result output.
  val prefixText = "intellij: executing command..."
  options.addInitCommand("echo " + CommandLineUtil.posixQuote(prefixText))
  if (options.isExecuteCommandInInteractiveShell) {
    // Disable oh-my-zsh auto update on shell initialization
    commandLine.environment[EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE] = "true"
    options.isPassEnvVarsUsingInterop = true
  }
  val output: ProcessOutput = executeOnWsl(commandLine, options, timeout, processHandlerCustomizer)
  val stdout = output.stdout
  val markerText = prefixText + LineSeparator.LF.separatorString
  val index = stdout.indexOf(markerText)
  if (index < 0) {
    val application = ApplicationManager.getApplication()
    if (application == null || application.isInternal || application.isUnitTestMode) {
      LOG.error("Cannot find '$prefixText' in stdout: $output")
    }
    else {
      LOG.info("Cannot find '$prefixText' in stdout")
    }
    return output
  }
  return ProcessOutput(stdout.substring(index + markerText.length),
                       output.stderr,
                       output.exitCode,
                       output.isTimeout,
                       output.isCancelled)
}

fun WSLDistribution.executeInShellAndGetCommandOnlyStdout(commandLine: GeneralCommandLine,
                                                          options: WSLCommandLineOptions,
                                                          timeout: Int,
                                                          expectOneLineStdout: Boolean): String? {
  try {
    val output: ProcessOutput = executeInShellAndGetCommandOnlyStdout(commandLine, options, timeout)
    val stdout = output.stdout
    if (!output.isTimeout && output.exitCode == 0) {
      return if (expectOneLineStdout) expectOneLineOutput(commandLine, stdout) else stdout
    }
    LOG.info("Failed to execute $commandLine for $msId: exitCode=${output.exitCode}, timeout=${output.isTimeout}," +
             " stdout=$stdout, stderr=${output.stderr}")
  }
  catch (e: ExecutionException) {
    LOG.info("Failed to execute $commandLine for $msId", e)
  }
  return null
}

private fun WSLDistribution.expectOneLineOutput(commandLine: GeneralCommandLine, stdout: String): String {
  val converted = StringUtil.convertLineSeparators(stdout, LineSeparator.LF.separatorString)
  val lines = StringUtil.split(converted, LineSeparator.LF.separatorString, true, true)
  if (lines.size != 1) {
    LOG.info("One line stdout expected: " + msId + ", command=" + commandLine + ", stdout=" + stdout + ", lines=" + lines.size)
  }
  return StringUtil.notNullize(ContainerUtil.getFirstItem(lines), stdout)
}

@Throws(ExecutionException::class)
private fun WSLDistribution.executeOnWsl(commandLine: GeneralCommandLine,
                                         options: WSLCommandLineOptions,
                                         timeout: Int,
                                         processHandlerCustomizer: Consumer<ProcessHandler>): ProcessOutput {
  patchCommandLine<GeneralCommandLine>(commandLine, null, options)
  val processHandler = CapturingProcessHandler(commandLine)
  processHandlerCustomizer.accept(processHandler)
  return processHandler.runProcess(timeout)
}