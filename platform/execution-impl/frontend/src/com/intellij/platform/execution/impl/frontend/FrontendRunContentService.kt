// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.CommonBundle.message
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleState
import com.intellij.execution.impl.ConsoleState.NotStartedStated
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ConsoleViewRunningState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.execution.rpc.RunSessionsApi
import com.intellij.execution.runners.RunContentBuilder.addAdditionalConsoleEditorActions
import com.intellij.execution.ui.ExecutionConsole.CONSOLE_CONTENT_ID
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.execution.ui.RunnerLayoutUi.Factory
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.DockableGridContainerFactory
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl.CONTENT_TYPE
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.docking.DockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class FrontendRunContentService(private val project: Project, private val cs: CoroutineScope) {

  init {
    if (RunContentManagerImpl.isSplitRun() && AppModeAssertions.isFrontend()) {
      init()
    }
  }

  // todo: need to show the tab content correctly, now it creates a mock tab with empty console
  private fun init() {
    val containerFactory = DockableGridContainerFactory()
    DockManager.getInstance(project).register(DockableGridContainerFactory.TYPE, containerFactory, project)

    cs.launch {
      val eventFlow = RunSessionsApi.getInstance().events(project.projectId())

      eventFlow.toFlow().collect {
        if (it is RunSessionEvent.SessionStarted) {
          val runSession = RunSessionsApi.getInstance().getSession(project.projectId(), it.runTabId) ?: return@collect

          val executionEnvironmentProxy = runSession.executorEnvironment
          val globalSearchScope = ExecutionSearchScopes.executionScope(project, null)
          val runProfileName = executionEnvironmentProxy.runProfileName

          val processHandlerDto = runSession.processHandler

          val frontendProcessHandler = createFrontendProcessHandler(project, processHandlerDto!!)

          val consoleState = object : NotStartedStated() {
            override fun attachTo(console: ConsoleViewImpl, processHandler: ProcessHandler): ConsoleState {
              return ConsoleViewRunningState(console, processHandler, this, true, true)
            }
          }
          val console = object : ConsoleViewImpl(project, globalSearchScope, false, consoleState, false) {}

          consoleState.attachTo(console, frontendProcessHandler)

          val existingRunToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN)

          val contentManager = existingRunToolWindow?.contentManager
                               ?: withContext(Dispatchers.EDT) {
                                 val defaultRunExecutor = DefaultRunExecutor()
                                 val runContentManager = RunContentManager.getInstance(project)
                                 val runContentManagerImpl = runContentManager as RunContentManagerImpl
                                 runContentManagerImpl.registerToolWindow(defaultRunExecutor)
                               }

          withContext(Dispatchers.EDT) {

            val runnerLayoutUi = Factory.getInstance(project).create("JavaRunner", "My Runner", runProfileName, project)

            // see RunContentBuilder.buildConsoleUiDefault
            val consoleContent: Content = runnerLayoutUi.createContent(CONSOLE_CONTENT_ID, console.getComponent(),
                                                                       message("title.console"),
                                                                       null,
                                                                       console.getPreferredFocusableComponent())
            consoleContent.setCloseable(false)
            addAdditionalConsoleEditorActions(console, consoleContent)
            runnerLayoutUi.addContent(consoleContent, 0, PlaceInGrid.bottom, false)

            val newContent = createNewContent(runnerLayoutUi.component, "My Name ${it.runTabId}")
            newContent.putUserData(CONTENT_TYPE, "JavaRunner") // can also be Go, Php, .Net, Ruby, other value is "Debug" for RunTab)
            contentManager.addContent(newContent)
            contentManager.setSelectedContent(newContent)

            ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN)?.activate(null)
          }
        }
      }
    }
  }

  // see RunContentManagerImpl.createNewContent(descriptor: RunContentDescriptor, executor: Executor)
  // used in RunContentManagerImpl.showRunContent(executor: Executor, descriptor: RunContentDescriptor, executionId: Long)
  private fun createNewContent(component: JComponent, @NlsSafe displayName: String): Content {
    val content = ContentFactory.getInstance().createContent(component, displayName, true)
    //content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    if (AdvancedSettings.getBoolean("start.run.configurations.pinned")) content.isPinned = true
    //content.icon = com.intellij.util.PlatformIcons.RunConfiguration
    return content
  }


  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendRunContentService = project.service<FrontendRunContentService>()
  }
}