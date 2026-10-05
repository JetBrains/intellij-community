// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.executeState
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * Runs an external tool with a console, and starts the process of the tool on a background thread.
 *
 * The runner creates the console and shows the run content on the EDT.
 */
internal class ToolProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "ExternalToolRunner"

  /**
   * Accepts only [ToolRunProfile] itself. A subclass can have its own state, which this runner cannot start in the background.
   */
  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DefaultRunExecutor.EXECUTOR_ID && profile.javaClass == ToolRunProfile::class.java
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    if (state !is ToolCommandLineState) {
      return resolvedPromise(executeState(state, environment, this))
    }

    // the tool can read the saved files, so write them to the disk before the start
    FileDocumentManager.getInstance().saveAllDocuments()
    ManagingFS.getInstance().flushPendingUpdatesOrNotify()
    val modality = ModalityState.defaultModalityState().asContextElement()
    // the project scope cancels the start when the project closes
    val start = environment.project.service<ToolProgramRunnerScope>().coroutineScope.async(Dispatchers.EDT + modality) {
      var processHandler: ProcessHandler? = null
      try {
        val startedHandler = withContext(Dispatchers.IO) {
          state.startProcessInBackground().also { processHandler = it }
        }
        showRunContent(state.createExecutionResult(environment.executor, startedHandler), environment)
      }
      catch (e: Throwable) {
        processHandler?.let(::destroyStartedProcess)
        throw e
      }
    }
    // the manager handles an error on the EDT, like the error of a synchronous runner
    val result = AsyncPromise<RunContentDescriptor?>()
    start.invokeOnCompletion { error ->
      when (error) {
        null -> result.setResult(start.getCompleted())
        // the project is closed, so the manager has no content to show
        is CancellationException -> result.setResult(null)
        else -> result.setError(error)
      }
    }
    return result
  }
}

@Service(Service.Level.PROJECT)
internal class ToolProgramRunnerScope(val coroutineScope: CoroutineScope)

/**
 * Destroys a process that has no run content.
 *
 * A process handler destroys the process only after the start notification, so this function sends it first.
 */
private fun destroyStartedProcess(processHandler: ProcessHandler) {
  if (!processHandler.isStartNotified) {
    processHandler.startNotify()
  }
  processHandler.destroyProcess()
}
