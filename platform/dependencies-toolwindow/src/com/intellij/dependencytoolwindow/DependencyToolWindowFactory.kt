// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.DynamicBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import icons.PlatformDependencyToolwindowIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier
import kotlin.coroutines.CoroutineContext

class DependencyToolWindowFactory : ProjectPostStartupActivity {
  companion object {
    const val toolWindowId = "Dependencies"

    private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)

    fun activateToolWindow(project: Project, tab: Content? = null, action: () -> Unit) {
      val toolWindow = getToolWindow(project) ?: return
      toolWindow.activate(action, true, true)
      tab?.let { toolWindow.contentManager.setSelectedContent(it) }
    }
  }

  override suspend fun execute(project: Project) {
    withContext(Dispatchers.toolWindowManager(project)) {
      DependenciesToolWindowTabProvider.availableTabsFlow(project)
        .filter { it.isNotEmpty() }
        .take(1)
        .map {
          RegisterToolWindowTask.closable(
            id = toolWindowId,
            stripeTitle = DependencyToolWindowBundle.messagePointer("toolwindow.stripe.Dependencies"),
            icon = PlatformDependencyToolwindowIcons.ArtifactSmall
          )
        }
        .map { toolWindowTask -> ToolWindowManager.getInstance(project).registerToolWindow(toolWindowTask) }
        .collect { toolWindow -> initializeToolWindow(toolWindow, project) }
    }
  }
}

private fun initializeToolWindow(toolWindow: ToolWindow, project: Project) {
  toolWindow.contentManager.addSelectionChangedListener { event ->
    if (toolWindow is ToolWindowEx) {
      toolWindow.setAdditionalGearActions(null)
      (event.content.component as? HasToolWindowActions)
        ?.also { toolWindow.setAdditionalGearActions(it.gearActions) }
    }
    toolWindow.setTitleActions(emptyList())
    (event.content.component as? HasToolWindowActions)
      ?.titleActions
      ?.also { toolWindow.setTitleActions(it.toList()) }
  }

  toolWindow.isAvailable = false
  toolWindow.contentManager.removeAllContents(true)

  DependenciesToolWindowTabProvider.availableTabsFlow(project)
    .flowOn(getLifecycleScope(project).dispatcher)
    .map { it.map { provider -> provider.provideTab(project) } }
    .onEach { change ->
      val removedContent = toolWindow.contentManager.contents.filter { it !in change }.toSet()
      val newContent = change.filter { it !in toolWindow.contentManager.contents }
      val contentOrder = (toolWindow.contentManager.contents.toList() - removedContent + newContent)
        .sortedBy { it.toolwindowTitle }
        .mapIndexed { index, content -> content to index }
        .toMap()
      removedContent.forEach { toolWindow.contentManager.removeContent(it, true) }
      newContent.forEach { content ->
        contentOrder.get(content)?.let { order -> toolWindow.contentManager.addContent(content, order) }
        ?: toolWindow.contentManager.addContent(content)
      }
      toolWindow.isAvailable = change.isNotEmpty()
    }
    .flowOn(Dispatchers.EDT)
    .launchIn(getLifecycleScope(project))

  project.lookAndFeelFlow
    .onEach {
      toolWindow.contentManager.component.invalidate()
      toolWindow.contentManager.component.repaint()
    }
    .flowOn(Dispatchers.EDT)
    .launchIn(getLifecycleScope(project))
}

@Suppress("FunctionName")
private fun SelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      action(event)
    }
  }
}

private fun ContentManager.addSelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return SelectionChangedListener(action).also(::addContentManagerListener)
}

private const val BUNDLE_NAME = "messages.dependenciesToolwindow"

private object DependencyToolWindowBundle : DynamicBundle(BUNDLE_NAME) {
  @Nls
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): Supplier<String> {
    return getLazyMessage(key, *params)
  }
}

@Suppress("UnusedReceiverParameter")
private fun Dispatchers.toolWindowManager(project: Project): CoroutineDispatcher = object : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) = ToolWindowManager.getInstance(project).invokeLater(block)
}
