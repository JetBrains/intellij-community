// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import icons.PlatformDependencyToolwindowIcons
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume

internal class DependencyToolWindowFactory : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    ToolWindowManager.awaitToolWindows(project)
    DependenciesToolWindowTabProvider.awaitFirstAvailable(project)

    project.service<DependencyToolWindowInitializer>()
      .createToolwindow()
  }

  private suspend fun ToolWindowManager.Companion.awaitToolWindows(project: Project) = suspendCancellableCoroutine {
    getInstance(project).invokeLater(ContextAwareRunnable { it.resume(Unit) })
  }
}

object DependencyToolWindowOpener {
  fun activateToolWindow(project: Project, id: DependenciesToolWindowTabProvider.Id, action: () -> Unit) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.BUILD_DEPENDENCIES) ?: return
    toolWindow.activate(action, true, true)
    DependenciesToolWindowTabProvider.extensions(project)
      .filter { it.isAvailable(project) }
      .associateBy { it.id }
      .get(id)
      ?.provideTab(project)
      ?.let { toolWindow.contentManager.setSelectedContent(it) }
  }
}

@Service(Service.Level.PROJECT)
internal class DependencyToolWindowInitializer(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) {
  internal suspend fun createToolwindow() {
    withContext(Dispatchers.EDT) {
      blockingContext {
        val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
          RegisterToolWindowTask(
            id = ToolWindowId.BUILD_DEPENDENCIES,
            stripeTitle = IdeBundle.messagePointer("toolwindow.stripe.Dependencies"),
            icon = PlatformDependencyToolwindowIcons.ArtifactSmall,
            shouldBeAvailable = DependenciesToolWindowTabProvider.hasAnyExtensions()
          )
        )
        initialize(toolWindow)
      }
    }
  }

  private fun initialize(toolWindow: ToolWindow) {
    toolWindow.contentManager.removeAllContents(true)

    toolWindow.contentManager.addSelectionChangedListener { event ->
      val actionToolWindow = event.content.component as? HasToolWindowActions
      if (toolWindow is ToolWindowEx) {
        toolWindow.setAdditionalGearActions(null)
        actionToolWindow?.also { toolWindow.setAdditionalGearActions(it.gearActions) }
      }
      toolWindow.setTitleActions(emptyList())
      actionToolWindow?.titleActions
        ?.also { toolWindow.setTitleActions(it.toList()) }
    }

    val tabsFlow = DependenciesToolWindowTabProvider.availableTabsFlow(project)
      .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    tabsFlow.onEach { toolWindow.isAvailable = it.isNotEmpty() }
      .flowOn(Dispatchers.EDT)
      .launchIn(coroutineScope)

    coroutineScope.launch {
      toolWindow.awaitIsVisible()

      tabsFlow
        .map { it.map { provider -> provider.provideTab(project) } }
        .onEach { change ->
          val removedContent = toolWindow.contentManager.contents.filter { it !in change }.toSet()
          val newContent = change.filter { it !in toolWindow.contentManager.contents }
          val contentOrder = toolWindow.contentManager
            .contents
            .toList()
            .minus(removedContent)
            .plus(newContent)
            .sortedBy { it.toolwindowTitle }
            .mapIndexed { index, content -> content to index }
            .toMap()

          for (it in removedContent) {
            toolWindow.contentManager.removeContent(it, true)
          }

          for (content in newContent) {
            contentOrder[content]?.let { order -> toolWindow.contentManager.addContent(content, order) }
            ?: toolWindow.contentManager.addContent(content)
          }
        }
        .flowOn(Dispatchers.EDT)
        .launchIn(coroutineScope)

      project.lookAndFeelFlow
        .onEach {
          toolWindow.contentManager.component.invalidate()
          toolWindow.contentManager.component.repaint()
        }
        .flowOn(Dispatchers.EDT)
        .launchIn(coroutineScope)
    }
  }

  private suspend fun ToolWindow.awaitIsVisible() {
    isVisibleFlow.filter { it }.first()
  }

  private val ToolWindow.isVisibleFlow
    get() = project.messageBusFlow(ToolWindowManagerListener.TOPIC, { isVisible }) {
      object : ToolWindowManagerListener {
        override fun toolWindowShown(toolWindow: ToolWindow) {
          if (toolWindow == this@messageBusFlow || toolWindow.id == id) {
            trySend(isVisible)
          }
        }
      }
    }
}