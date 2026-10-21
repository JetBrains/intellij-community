// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Runs the command line of an external tool.
 *
 * [ToolProgramRunner] starts the process with [startProcessInBackground] on a background thread.
 * Then it calls [createExecutionResult] on the EDT, because the console is a Swing component.
 */
internal class ToolCommandLineState(
  environment: ExecutionEnvironment,
  private val tool: Tool,
  private val commandLine: GeneralCommandLine,
  private val toolName: @NlsSafe String,
) : CommandLineState(environment) {
  override fun startProcess(): OSProcessHandler {
    val processHandler = ColoredProcessHandler(commandLine)
    ProcessTerminatedListener.attach(processHandler)
    return processHandler
  }

  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  fun startProcessInBackground(): OSProcessHandler = startProcess()

  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
    return createExecutionResult(executor, startProcess())
  }

  /**
   * Creates the console for [processHandler] and adds the tool listeners to it.
   */
  @RequiresEdt
  @Throws(ExecutionException::class)
  fun createExecutionResult(executor: Executor, processHandler: ProcessHandler): ExecutionResult {
    val console = createConsole(executor)
    console?.attachToProcess(processHandler)
    val result = DefaultExecutionResult(console, processHandler, *createActions(console, processHandler, executor))
    addToolListeners(processHandler, executor)
    return result
  }

  private fun addToolListeners(processHandler: ProcessHandler, executor: Executor) {
    val project = environment.project
    processHandler.addProcessListener(ToolProcessAdapter(project, tool.synchronizeAfterExecution(), toolName))
    processHandler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if ((ProcessOutputType.isStdout(outputType) && tool.isShowConsoleOnStdOut) ||
            (ProcessOutputType.isStderr(outputType) && tool.isShowConsoleOnStdErr)) {
          ApplicationManager.getApplication().invokeLater({
            RunContentManager.getInstance(project).toFrontRunContent(executor, processHandler)
          }, project.disposed)
        }
      }
    })
  }
}
