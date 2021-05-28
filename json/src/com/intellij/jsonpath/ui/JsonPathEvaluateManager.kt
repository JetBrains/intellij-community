// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.util.function.Supplier

@Service
@ApiStatus.Experimental
class JsonPathEvaluateManager internal constructor(private val project: Project) {
  fun evaluateExpression(jsonPathExpr: String? = null) {
    val toolWindow = initToolwindow()
    val cm = toolWindow.contentManager
    val (content, view) = cm.contents.findLast {
      it.component is JsonPathEvaluateSnippetView && !it.isPinned
    }?.let {
      Pair(it, it.component as JsonPathEvaluateSnippetView)
    } ?: addSnippetTab(cm)

    toolWindow.show()
    cm.setSelectedContent(content, true)
    if (jsonPathExpr != null) {
      view.setExpression(jsonPathExpr)
    }
  }

  fun evaluateFromJson(file: JsonFile) {
    val toolWindow = initToolwindow()
    val cm = toolWindow.contentManager

    val existingContent = cm.contents.find {
      val component = it.component
      component is JsonPathEvaluateFileView && component.getJsonFile() == file
    }
    if (existingContent != null) {
      toolWindow.show()
      cm.setSelectedContent(existingContent, true)
      return
    }

    val newView = JsonPathEvaluateFileView(project, file)
    val newContent = cm.factory.createContent(newView.component,
                                              JsonBundle.message("jsonpath.toolwindow.evaluate.on.file", file.name),
                                              false)
    newContent.isCloseable = true
    newContent.isPinnable = true
    newContent.preferredFocusableComponent = newView.searchComponent
    cm.addContent(newContent)
    toolWindow.show()
    cm.setSelectedContent(newContent, true)
  }

  private fun initToolwindow(): ToolWindow {
    EDT.assertIsEdt()

    val toolWindowManager = ToolWindowManager.getInstance(project)
    var toolWindow = toolWindowManager.getToolWindow(EVALUATE_TOOLWINDOW_ID)
    if (toolWindow == null) {
      toolWindow = registerToolwindow(toolWindowManager)
      (toolWindow as? ToolWindowEx)?.setTabActions(
        object : DumbAwareAction(JsonBundle.message("jsonpath.evaluate.add.tab.text"), null, AllIcons.General.Add) {
          override fun actionPerformed(e: AnActionEvent) {
            val (content, _) = addSnippetTab(toolWindow.contentManager)
            toolWindow.contentManager.setSelectedContent(content, true)
          }
        })
    }

    return toolWindow
  }

  private fun registerToolwindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(
      RegisterToolWindowTask(
        id = EVALUATE_TOOLWINDOW_ID,
        anchor = ToolWindowAnchor.RIGHT,
        component = null,
        icon = AllIcons.Toolwindows.ToolWindowJsonPath,
        stripeTitle = JsonBundle.messagePointer("jsonpath.toolwindow.evaluate.stripe")
      )
    )
    ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)
    return toolWindow
  }

  private fun addSnippetTab(cm: @NotNull ContentManager): Pair<@NotNull Content, JsonPathEvaluateSnippetView> {
    val newView = JsonPathEvaluateSnippetView(project)
    val newContent = cm.factory.createContent(newView.component, findNextSnippetTitle(cm), false)
    newContent.isCloseable = true
    newContent.isPinnable = true
    newContent.preferredFocusableComponent = newView.searchComponent
    cm.addContent(newContent)
    return Pair(newContent, newView)
  }

  @NlsContexts.TabTitle
  private fun findNextSnippetTitle(cm: ContentManager): @NlsContexts.TabTitle String {
    val defaultTitle = JsonBundle.message("jsonpath.toolwindow.evaluate.on.snippet")
    if (cm.contents.none { it.tabName == defaultTitle }) {
      return defaultTitle
    }

    var index = 2
    while (cm.contents.any { it.tabName == JsonBundle.message("jsonpath.toolwindow.evaluate.on.snippet.n", index) }) {
      index++
    }
    return JsonBundle.message("jsonpath.toolwindow.evaluate.on.snippet.n", index)
  }

  companion object {
    const val EVALUATE_TOOLWINDOW_ID: String = "JSONPathEvaluate"

    internal const val JSON_PATH_EVALUATE_HISTORY: String = "JSONPathEvaluateHistory"

    @JvmField
    internal val JSON_PATH_EVALUATE_EXPRESSION_KEY: Key<Boolean> = Key.create("JSON_PATH_EVALUATE_EXPRESSION")

    @ApiStatus.Internal
    @JvmField
    val JSON_PATH_EVALUATE_SOURCE_KEY: Key<Supplier<JsonFile?>> = Key.create("JSON_PATH_EVALUATE_SOURCE")

    @JvmField
    internal val JSON_PATH_EVALUATE_RESULT_KEY: Key<Boolean> = Key.create("JSON_PATH_EVALUATE_RESULT")

    @JvmStatic
    fun getInstance(project: Project): JsonPathEvaluateManager {
      return project.getService(JsonPathEvaluateManager::class.java)
    }
  }
}