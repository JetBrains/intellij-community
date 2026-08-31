// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.BuildCategoryId
import com.intellij.build.BuildContent
import com.intellij.build.BuildContentId
import com.intellij.build.BuildContentManager
import com.intellij.build.BuildId
import com.intellij.build.BuildViewEvent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.buildView.BuildViewApi
import com.intellij.platform.project.projectId
import com.intellij.util.ConcurrencyUtil
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

private val LOG = fileLogger()

internal class FrontendBuildViewStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<FrontendBuildViewManager>().listenToBackend()
  }
}

@Service(Service.Level.PROJECT)
private class FrontendBuildViewManager(private val project: Project, private val scope: CoroutineScope) {
  val activeMap = mutableMapOf<BuildCategoryId, FrontendMultipleBuildsView>()
  val contentMap = mutableMapOf<BuildContentId, FrontendMultipleBuildsView>()
  val buildMap = mutableMapOf<BuildId, FrontendMultipleBuildsView>()

  fun listenToBackend() {
    scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      durable {
        BuildViewApi.getInstance().getBuildViewEventsFlow(project.projectId()).collect { event ->
          LOG.debug { "Event: $event" }

          // This is needed for the remote development case. Due to the racy dispatching, we can receive the event here
          // before the tool window creation on the backend is processed on the frontend.
          waitForToolWindow()

          val buildId = event.buildId
          val view = when (event) {
            is BuildViewEvent.BuildStarted -> getOrCreateView(event.content).also {
              buildMap[buildId] = it
            }
            is BuildViewEvent.BuildRemoved -> {
              buildMap.remove(buildId)
            }
            else -> {
              buildMap[buildId]
            }
          }
          if (view == null) {
            LOG.error("Build content not found for $event")
          }
          else {
            view.handleEvent(event)
          }
        }
      }
    }
  }

  private fun getOrCreateView(content: BuildContent) = contentMap.computeIfAbsent(content.id) {
    val categoryId = content.categoryId
    val newView = FrontendMultipleBuildsView(project, content, scope)
    newView.whenDisposed {
      activeMap.remove(categoryId, newView)
      contentMap.remove(content.id)
    }
    val oldView = activeMap.put(categoryId, newView)
    oldView?.lockContent()
    newView
  }

  private suspend fun waitForToolWindow() {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    if (!isToolWindowRegistered(toolWindowManager)) {
      if (AppMode.isMonolith()) {
        LOG.error("Tool window is not registered") // in monolith this is unexpected, but not critical
        return
      }
      LOG.info("Waiting for tool window registration")
      Disposer.newDisposable("FrontendBuildViewManager.waitForToolWindow").use { disposable ->
        suspendCancellableCoroutine { continuation ->
          val resumeOnce = ConcurrencyUtil.once { continuation.resumeWith(Result.success(Unit)) }
          fun checkDone() {
            if (isToolWindowRegistered(toolWindowManager)) {
              resumeOnce.run()
            }
          }
          val connection = project.messageBus.connect(disposable)
          connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
              checkDone()
            }

            override fun stateChanged(toolWindowManager: ToolWindowManager) {
              checkDone()
            }
          })
          checkDone() // just in case, since toolWindowsRegistered might be fired not on EDT
        }
      }
      LOG.info("Tool window registered")
    }
  }

  private fun isToolWindowRegistered(toolWindowManager: ToolWindowManager): Boolean {
    return toolWindowManager.getToolWindow(BuildContentManager.TOOL_WINDOW_ID) != null
  }
}