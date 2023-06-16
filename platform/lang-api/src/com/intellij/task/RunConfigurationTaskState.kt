// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * If run configuration requires some additional steps usually performed by its command line state,
 * environment provider extension may put <code>RunConfigurationTaskState</code> instance as a user data by <code>KEY</code>
 * for corresponding external system run configuration. Task state will be called during run configuration task execution.
 */
@ApiStatus.Experimental
interface RunConfigurationTaskState {
  companion object {
    @JvmStatic
    val KEY: Key<RunConfigurationTaskState> = Key.create("RunConfigurationTaskState")
  }

  @Throws(ExecutionException::class)
  fun prepareTargetEnvironmentRequest(request: TargetEnvironmentRequest, targetProgressIndicator: TargetProgressIndicator)

  /**
   * @return init script text which will be appended if it is not <code>null</code>
   */
  fun handleCreatedTargetEnvironment(environment: TargetEnvironment, targetProgressIndicator: TargetProgressIndicator): String?

  fun processExecutionResult(handler: ProcessHandler, console: ExecutionConsole)

  fun createCustomActions(handler: ProcessHandler, console: ExecutionConsole, executor: Executor?): List<AnAction> = emptyList()
}