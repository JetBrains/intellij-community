// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunProfileStarter
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

abstract class GenericProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?) {
    val state = environment.state ?: return
    execute(environment, callback, state)
  }

  @Throws(ExecutionException::class)
  protected open fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
    startRunProfile(environment, callback) {
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

abstract class AsyncProgramRunner<Settings : RunnerSettings> : ProgramRunner<Settings> {
  @Throws(ExecutionException::class)
  final override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?) {
    val state = environment.state ?: return
    startRunProfile(environment, callback) {
      execute(environment, state)
    }
  }

  @Throws(ExecutionException::class)
  protected abstract fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?>
}

/**
 * Internal usage only. Maybe removed or changed in any moment. No backward compatibility.
 */
@ApiStatus.Internal
fun startRunProfile(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, starter: () -> Promise<RunContentDescriptor?>) {
  ExecutionManager.getInstance(environment.project).startRunProfile(object : RunProfileStarter() {
    override fun executeAsync(environment: ExecutionEnvironment): Promise<RunContentDescriptor> {
      // errors are handled by com.intellij.execution.ExecutionManager.startRunProfile
      return starter()
        .then { descriptor ->
          if (descriptor != null) {
            descriptor.executionId = environment.executionId

            val toolWindowId = RunContentManager.getInstance(environment.project).getContentDescriptorToolWindowId(environment)
            if (toolWindowId != null) {
              descriptor.contentToolWindowId = toolWindowId
            }

            val settings = environment.runnerAndConfigurationSettings
            if (settings != null) {
              descriptor.isActivateToolWindowWhenAdded = settings.isActivateToolWindowBeforeRun
            }
          }
          callback?.processStarted(descriptor)
          descriptor
        }
    }
  }, environment)
}