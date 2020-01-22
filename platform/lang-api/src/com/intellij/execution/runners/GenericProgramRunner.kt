// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?) {
    execute(environment, callback, environment.state ?: return)
  }

  @Throws(ExecutionException::class)
  protected open fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    ExecutionManager.getInstance(environment.project).startRunProfile(environment, callback, {
      resolvedPromise(doExecute(state, environment))
    })
  }

  @Throws(ExecutionException::class)
  protected open fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    @Suppress("DEPRECATION")
    return doExecute(environment.project, state, environment.contentToReuse, environment)
  }

  @Deprecated("")
  @Throws(ExecutionException::class)
  protected open fun doExecute(project: Project,
                               state: RunProfileState,
                               contentToReuse: RunContentDescriptor?,
                               environment: ExecutionEnvironment): RunContentDescriptor? {
    throw AbstractMethodError()
  }
}

abstract class AsyncProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?) {
    val state = environment.state ?: return
    ExecutionManager.getInstance(environment.project).startRunProfile(environment, callback, {
      execute(environment, state)
    })
  }

  @Throws(ExecutionException::class)
  protected abstract fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?>
}