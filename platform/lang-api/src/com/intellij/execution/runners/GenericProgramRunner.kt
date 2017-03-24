/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    startRunProfile(environment, state, callback, runProfileStarter { resolvedPromise(doExecute(state, environment)) })
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
  override final fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    startRunProfile(environment, state, callback, runProfileStarter { execute(environment, state) })
  }

  @Throws(ExecutionException::class)
  protected abstract fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?>
}

internal inline fun runProfileStarter(crossinline starter: () -> Promise<RunContentDescriptor?>) = object : RunProfileStarter() {
  override fun executeAsync(state: RunProfileState, environment: ExecutionEnvironment) = starter()
}

internal fun startRunProfile(environment: ExecutionEnvironment, state: RunProfileState, callback: ProgramRunner.Callback?, starter: RunProfileStarter?) {
  ExecutionManager.getInstance(environment.project).startRunProfile(runProfileStarter {
    (starter?.executeAsync(state, environment) ?: resolvedPromise())
      .then { BaseProgramRunner.postProcess(environment, it, callback) }
  }, state, environment)
}