// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.concurrency.resolvedPromise

// due to backward compatibility, we cannot get rid of GenericProgramRunner inheritance
abstract class JavaPatchableProgramRunner<Settings : RunnerSettings> : GenericProgramRunner<Settings>() {
  companion object {
    @JvmStatic
    protected fun runCustomPatchers(javaParameters: JavaParameters, executor: Executor, runProfile: RunProfile) {
      JavaProgramPatcher.EP_NAME.forEachExtensionSafe {
        it.patchJavaParameters(executor, runProfile, javaParameters)
      }
    }
  }

  @Throws(ExecutionException::class)
  abstract fun patch(javaParameters: JavaParameters?, settings: RunnerSettings?, runProfile: RunProfile?, beforeExecution: Boolean)

  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    startRunProfile(environment, callback, { resolvedPromise(doExecute(state, environment)) })
  }

  @Throws(ExecutionException::class)
  abstract override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor?
}