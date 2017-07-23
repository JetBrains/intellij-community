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
package com.intellij.execution.impl

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunProfileStarter
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Trinity
import com.intellij.ui.AppUIUtil
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

class ExecutionManagerKtImpl(project: Project) : ExecutionManagerImpl(project) {
  @set:TestOnly
  @Volatile var forceCompilationInTests = false

  override fun startRunProfile(starter: RunProfileStarter, state: RunProfileState, environment: ExecutionEnvironment) {
    val project = environment.project
    val reuseContent = contentManager.getReuseContent(environment)
    if (reuseContent != null) {
      reuseContent.executionId = environment.executionId
      environment.contentToReuse = reuseContent
    }

    val executor = environment.executor
    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processStartScheduled(executor.id, environment)

    fun processNotStarted() {
      project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processNotStarted(executor.id, environment)
    }

    val startRunnable = Runnable {
      if (project.isDisposed) {
        return@Runnable
      }

      project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processStarting(executor.id, environment)

      fun handleError(e: Throwable) {
        if (e !is ProcessCanceledException) {
          ExecutionUtil.handleExecutionError(project, contentManager.getToolWindowIdByEnvironment(environment), environment.runProfile.name, e)
          LOG.debug(e)
        }
        processNotStarted()
      }

      try {
        starter.executeAsync(state, environment)
          .done { descriptor ->
            AppUIUtil.invokeLaterIfProjectAlive(project) {
              if (descriptor == null) {
                processNotStarted()
                return@invokeLaterIfProjectAlive
              }

              val trinity = Trinity.create(descriptor, environment.runnerAndConfigurationSettings, executor)
              myRunningConfigurations.add(trinity)
              Disposer.register(descriptor, Disposable { myRunningConfigurations.remove(trinity) })
              contentManager.showRunContent(executor, descriptor, environment.contentToReuse)
              val processHandler = descriptor.processHandler
              if (processHandler != null) {
                if (!processHandler.isStartNotified) {
                  processHandler.startNotify()
                }
                project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processStarted(executor.id, environment, processHandler)

                val listener = ProcessExecutionListener(project, executor.id, environment, processHandler, descriptor)
                processHandler.addProcessListener(listener)

                // Since we cannot guarantee that the listener is added before process handled is start notified,
                // we have to make sure the process termination events are delivered to the clients.
                // Here we check the current process state and manually deliver events, while
                // the ProcessExecutionListener guarantees each such event is only delivered once
                // either by this code, or by the ProcessHandler.

                val terminating = processHandler.isProcessTerminating
                val terminated = processHandler.isProcessTerminated
                if (terminating || terminated) {
                  listener.processWillTerminate(ProcessEvent(processHandler), false /*doesn't matter*/)
                  if (terminated) {
                    listener.processTerminated(ProcessEvent(processHandler, if (processHandler.isStartNotified) processHandler.exitCode ?: -1 else -1))
                  }
                }
              }
              environment.contentToReuse = descriptor
            }
          }
          .rejected(::handleError)
      }
      catch (e: Throwable) {
        handleError(e)
      }
    }

    if (!forceCompilationInTests && ApplicationManager.getApplication().isUnitTestMode) {
      startRunnable.run()
    }
    else {
      compileAndRun(Runnable { TransactionGuard.submitTransaction(project, startRunnable) }, environment, state, Runnable {
        if (!project.isDisposed) {
          processNotStarted()
        }
      })
    }
  }
}

private class ProcessExecutionListener(private val project: Project,
                                       private val executorId: String,
                                       private val environment: ExecutionEnvironment,
                                       private val processHandler: ProcessHandler,
                                       private val descriptor: RunContentDescriptor) : ProcessAdapter() {
  private val willTerminateNotified = AtomicBoolean()
  private val terminateNotified = AtomicBoolean()

  override fun processTerminated(event: ProcessEvent) {
    if (project.isDisposed || !terminateNotified.compareAndSet(false, true)) {
      return
    }

    ApplicationManager.getApplication().invokeLater({
      val ui = descriptor.runnerLayoutUi
      if (ui != null && !ui.isDisposed) {
        ui.updateActionsNow()
      }
    }, ModalityState.any())

    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processTerminated(executorId, environment, processHandler, event.exitCode)

    SaveAndSyncHandler.getInstance()?.scheduleRefresh()
  }

  override fun processWillTerminate(event: ProcessEvent, shouldNotBeUsed: Boolean) {
    if (project.isDisposed || !willTerminateNotified.compareAndSet(false, true)) {
      return
    }

    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processTerminating(executorId, environment, processHandler)
  }
}