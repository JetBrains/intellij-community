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

abstract class GenericProgramRunner<Settings : RunnerSettings> : BaseProgramRunner<Settings>() {
  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    ExecutionManager.getInstance(environment.project).startRunProfile(runProfileStarter { state, environment ->
      BaseProgramRunner.postProcess(environment, doExecute(state, environment), callback)
    }, state, environment)
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

inline fun runProfileStarter(crossinline starter: (state: RunProfileState, environment: ExecutionEnvironment) -> RunContentDescriptor?) = object : RunProfileStarter() {
  override fun execute(state: RunProfileState, env: ExecutionEnvironment) = starter(state, env)
}

fun startRunProfile(environment: ExecutionEnvironment, state: RunProfileState, callback: ProgramRunner.Callback?, starter: RunProfileStarter?) {
  ExecutionManager.getInstance(environment.project).startRunProfile(object : RunProfileStarter() {
    override fun executeAsync(state: RunProfileState, environment: ExecutionEnvironment): Promise<RunContentDescriptor> {
      if (starter == null) {
        return Promise.resolve<RunContentDescriptor>(BaseProgramRunner.postProcess(environment, null, callback))
      }
      return starter.executeAsync(state, environment).then<RunContentDescriptor> { descriptor ->
        BaseProgramRunner.postProcess(environment, descriptor, callback)
      }
    }

    @Throws(ExecutionException::class)
    override fun execute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
      return BaseProgramRunner.postProcess(environment, starter?.execute(state, environment), callback)
    }
  }, state, environment)
}