// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

abstract class GenericProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  final override fun execute(environment: ExecutionEnvironment) {
    execute(environment, environment.callback, environment.state ?: return)
  }

  protected open fun execute(environment: ExecutionEnvironment, state: RunProfileState) {
    ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
      resolvedPromise(doExecute(state, environment))
    }
  }

  protected open fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    execute(environment, state)
  }

  @Throws(ExecutionException::class)
  protected open fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    @Suppress("DEPRECATION")
    return doExecute(environment.project, state, environment.contentToReuse, environment)
  }

  @Deprecated("")
  protected open fun doExecute(project: Project,
                               state: RunProfileState,
                               contentToReuse: RunContentDescriptor?,
                               environment: ExecutionEnvironment): RunContentDescriptor? {
    throw AbstractMethodError()
  }
}

/**
 * An async variant of [ProgramRunner].
 * It allows starting a process and preparing [RunContentDescriptor] in a background thread.
 * It should be used to not block UI thread when preparing an execution or starting a process is slow.
 */
abstract class AsyncProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment) {
    val state = environment.state ?: return
    ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
      execute(environment, state)
    }
  }

  @Throws(ExecutionException::class)
  protected abstract fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?>
}