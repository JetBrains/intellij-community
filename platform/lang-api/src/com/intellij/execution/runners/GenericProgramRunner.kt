// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunProfileStarter
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

abstract class GenericProgramRunner<Settings : RunnerSettings> : BaseProgramRunner<Settings>() {
  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    startRunProfile(environment, state, callback) {
      resolvedPromise(doExecute(state, environment))
    }
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

abstract class AsyncProgramRunner<Settings : RunnerSettings> : BaseProgramRunner<Settings>() {
  final override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    startRunProfile(environment, state, callback) {
      execute(environment, state)
    }
  }

  @Throws(ExecutionException::class)
  protected abstract fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?>
}

internal inline fun startRunProfile(environment: ExecutionEnvironment, state: RunProfileState, callback: ProgramRunner.Callback?, crossinline starter: () -> Promise<RunContentDescriptor?>) {
  ExecutionManager.getInstance(environment.project).startRunProfile(object : RunProfileStarter() {
    override fun executeAsync(state: RunProfileState, environment: ExecutionEnvironment): Promise<RunContentDescriptor> {
      // errors are handled by com.intellij.execution.ExecutionManager.startRunProfile
      return starter()
        .then {
          BaseProgramRunner.postProcess(environment, it, callback)
        }
    }
  }, state, environment)
}