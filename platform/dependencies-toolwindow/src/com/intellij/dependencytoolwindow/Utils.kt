// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import icons.PlatformDependencyToolwindowIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("FunctionName")
internal fun SelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      action(event)
    }
  }
}

internal fun ContentManager.addSelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return SelectionChangedListener(action).also { addContentManagerListener(it) }
}

internal const val BUNDLE_NAME = "messages.dependenciesToolwindow"

internal suspend fun ToolWindow.awaitIsVisible() {
  isVisibleFlow.filter { it }.first()
}

internal val ToolWindow.isVisibleFlow
  get() = project.messageBusFlow(ToolWindowManagerListener.TOPIC, { isVisible }) {
    object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow == this@messageBusFlow || toolWindow.id == id) {
          trySend(isVisible)
        }
      }
    }
  }

internal fun Project.createDependenciesToolwindow(): ToolWindow {
  val messagePointer = DependencyToolWindowBundle.messagePointer("toolwindow.stripe.Dependencies")
  return ToolWindowManager.getInstance(this).registerToolWindow(
    RegisterToolWindowTask.closable(
      id = messagePointer.get(),
      stripeTitle = messagePointer,
      icon = PlatformDependencyToolwindowIcons.ArtifactSmall
    )
  )
}

internal fun ToolWindow.initialize(scope: CoroutineScope) {

  contentManager.removeAllContents(true)
  isAvailable = false

  contentManager.addSelectionChangedListener { event ->
    val actionToolWindow = event.content.component as? HasToolWindowActions
    if (this is ToolWindowEx) {
      setAdditionalGearActions(null)
      actionToolWindow?.also { setAdditionalGearActions(it.gearActions) }
    }
    setTitleActions(emptyList())
    actionToolWindow?.titleActions
      ?.also { setTitleActions(it.toList()) }
  }

  val tabsFlow = DependenciesToolWindowTabProvider.availableTabsFlow(project)
    .shareIn(scope, SharingStarted.Eagerly, 1)

  tabsFlow.onEach { isAvailable = it.isNotEmpty() }
    .flowOn(Dispatchers.EDT)
    .launchIn(scope)

  scope.launch {
    awaitIsVisible()

    tabsFlow
      .map { it.map { provider -> provider.provideTab(project) } }
      .onEach { change ->
        val removedContent = contentManager.contents.filter { it !in change }.toSet()
        val newContent = change.filter { it !in contentManager.contents }
        val contentOrder = contentManager
          .contents
          .toList()
          .minus(removedContent)
          .plus(newContent)
          .sortedBy { it.toolwindowTitle }
          .mapIndexed { index, content -> content to index }
          .toMap()
        removedContent.forEach { contentManager.removeContent(it, true) }
        newContent.forEach { content ->
          contentOrder[content]?.let { order -> contentManager.addContent(content, order) }
          ?: contentManager.addContent(content)
        }
      }
      .flowOn(Dispatchers.EDT)
      .launchIn(scope)

    project.lookAndFeelFlow
      .onEach {
        contentManager.component.invalidate()
        contentManager.component.repaint()
      }
      .flowOn(Dispatchers.EDT)
      .launchIn(scope)
  }
}

internal suspend fun ToolWindowManager.Companion.awaitToolWindows(project: Project) = suspendCoroutine {
  ToolWindowManager.getInstance(project).invokeLater { it.resume(Unit) }
}