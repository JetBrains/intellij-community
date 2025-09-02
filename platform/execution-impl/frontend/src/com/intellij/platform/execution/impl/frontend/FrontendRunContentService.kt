// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleState
import com.intellij.execution.impl.ConsoleState.NotStartedStated
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ConsoleViewRunningState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.execution.rpc.RunSessionsApi
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.ide.ui.icons.icon
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.psi.search.ExecutionSearchScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class FrontendRunContentService(private val project: Project, private val cs: CoroutineScope) {

  init {
    if (RunContentManagerImpl.isSplitRun() && AppModeAssertions.isFrontend()) {
      init()
    }
  }

  // TODO IJPL-181190: initial implementation should be improved
  private fun init() {
    cs.launch {
      val eventFlow = RunSessionsApi.getInstance().events(project.projectId())

      eventFlow.collect {
        if (it is RunSessionEvent.SessionStarted) {
          val runSession = it.runSession

          val globalSearchScope = ExecutionSearchScopes.executionScope(project, null)

          val processHandlerDto = runSession.processHandler

          val frontendProcessHandler = createFrontendProcessHandler(project, processHandlerDto!!)

          val consoleState = object : NotStartedStated() {
            override fun attachTo(console: ConsoleViewImpl, processHandler: ProcessHandler): ConsoleState {
              return ConsoleViewRunningState(console, processHandler, this, true, false)
            }
          }
          val console = object : ConsoleViewImpl(project, globalSearchScope, false, consoleState, false) {}
          consoleState.attachTo(console, frontendProcessHandler)

          withContext(Dispatchers.EDT) {
            val executionResultProxy = DefaultExecutionResult(console, frontendProcessHandler)

            // TODO IJPL-181190 use correct arguments instead of mocked
            val runContentBuilder = RunContentBuilder(executionResultProxy, project, globalSearchScope,
                                                      "JavaRunner", "My Runner", "My Runner Session Name")
            val frontendDescriptor = runContentBuilder.showRunContent(null,
                                                                 runSession.executorEnvironment.runProfileName,
                                                                 runSession.executorEnvironment.icon.icon(),
                                                                 null
            )

            Disposer.register(frontendDescriptor) {
              runSession.tabClosedCallback.trySend(Unit)
            }

            val defaultRunExecutor = DefaultRunExecutor()
            val runContentManager = RunContentManager.getInstance(project)
            val runContentManagerImpl = runContentManager as RunContentManagerImpl
            runContentManagerImpl.registerToolWindow(defaultRunExecutor)

            runContentManagerImpl.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), frontendDescriptor)
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendRunContentService = project.service<FrontendRunContentService>()
  }
}