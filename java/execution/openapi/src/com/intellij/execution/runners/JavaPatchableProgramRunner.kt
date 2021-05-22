// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.resolvedPromise

@Deprecated(message = "Not required and not used anymore")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
abstract class JavaPatchableProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment) {
    execute(environment, environment.callback, environment.state ?: return)
  }

  abstract fun patch(javaParameters: JavaParameters?, settings: RunnerSettings?, runProfile: RunProfile?, beforeExecution: Boolean)

  fun execute(environment: ExecutionEnvironment, @Suppress("UNUSED_PARAMETER") callback: ProgramRunner.Callback?, state: RunProfileState) {
    ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
      resolvedPromise(doExecute(state, environment))
    }
  }

  abstract fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor?
}

interface JvmPatchableProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  fun patch(javaParameters: JavaParameters, settings: RunnerSettings?, runProfile: RunProfile, beforeExecution: Boolean)
}