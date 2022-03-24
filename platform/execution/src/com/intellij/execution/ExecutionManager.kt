// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.ThrowableConvertor
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.function.Consumer

/**
 * Manages the execution of run configurations and the relationship between running processes and Run/Debug toolwindow tabs.
 */
abstract class ExecutionManager {
  companion object {
    @JvmField
    val EXECUTION_SESSION_ID_KEY = Key.create<Any>("EXECUTION_SESSION_ID_KEY")

    @JvmField
    val EXECUTION_SKIP_RUN = Key.create<Boolean>("EXECUTION_SKIP_RUN")

    @JvmField
    @Topic.ProjectLevel
    val EXECUTION_TOPIC = Topic("configuration executed", ExecutionListener::class.java, Topic.BroadcastDirection.TO_PARENT)

    @JvmStatic
    fun getInstance(project: Project): ExecutionManager {
      return project.getService(ExecutionManager::class.java)
    }
  }

  @Deprecated("Use {@link RunContentManager#getInstance(Project)}")
  abstract fun getContentManager(): RunContentManager

  /**
   * Executes the before launch tasks for a run configuration.
   *
   * @param startRunnable    the runnable to actually start the process for the run configuration.
   * @param environment              the execution environment describing the process to be started.
   * @param onCancelRunnable the callback to call if one of the before launch tasks cancels the process execution.
   */
  abstract fun compileAndRun(startRunnable: Runnable, environment: ExecutionEnvironment, onCancelRunnable: Runnable?)

  /**
   * Returns the list of processes managed by all open run and debug tabs.
   */
  abstract fun getRunningProcesses(): Array<ProcessHandler>

  /**
   * Prepares the run or debug tab for running the specified process.
   */
  abstract fun startRunProfile(starter: RunProfileStarter, environment: ExecutionEnvironment)

  @ApiStatus.Internal
  abstract fun startRunProfile(environment: ExecutionEnvironment, starter: () -> Promise<RunContentDescriptor?>)

  @ApiStatus.Internal
  @Throws(ExecutionException::class)
  fun startRunProfile(environment: ExecutionEnvironment, executor: ThrowableConvertor<RunProfileState, RunContentDescriptor?, ExecutionException>) {
    startRunProfile(environment, environment.state ?: return, executor)
  }

  @ApiStatus.Internal
  fun startRunProfile(environment: ExecutionEnvironment, state: RunProfileState, executor: ThrowableConvertor<RunProfileState, RunContentDescriptor?, ExecutionException>) {
    startRunProfile(environment) {
      resolvedPromise(executor.convert(state))
    }
  }

  @ApiStatus.Internal
  @Throws(ExecutionException::class)
  fun startRunProfileWithPromise(environment: ExecutionEnvironment,
                                 state: RunProfileState,
                                 executor: ThrowableConvertor<RunProfileState, Promise<RunContentDescriptor?>, ExecutionException>) {
    startRunProfile(environment) {
      executor.convert(state)
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use {@link #startRunProfile(RunProfileStarter, ExecutionEnvironment)}")
  @ApiStatus.ScheduledForRemoval
  fun startRunProfile(starter: RunProfileStarter, @Suppress("UNUSED_PARAMETER") state: RunProfileState, environment: ExecutionEnvironment) {
    startRunProfile(starter, environment)
  }

  fun restartRunProfile(project: Project,
                        executor: Executor,
                        target: ExecutionTarget,
                        configuration: RunnerAndConfigurationSettings?,
                        processHandler: ProcessHandler?) {
    restartRunProfile(project, executor, target, configuration, processHandler, environmentCustomization = null)
  }

  abstract fun restartRunProfile(project: Project,
                                 executor: Executor,
                                 target: ExecutionTarget,
                                 configuration: RunnerAndConfigurationSettings?,
                                 processHandler: ProcessHandler?,
                                 environmentCustomization: Consumer<ExecutionEnvironment>?)
  abstract fun restartRunProfile(environment: ExecutionEnvironment)

  fun isStarting(environment: ExecutionEnvironment): Boolean {
    return isStarting(environment.executor.id, environment.runner.runnerId)
  }

  @ApiStatus.Internal
  abstract fun isStarting(executorId: String, runnerId: String): Boolean

  @ApiStatus.Experimental
  abstract fun executePreparationTasks(environment: ExecutionEnvironment, currentState: RunProfileState): Promise<Any?>
}
