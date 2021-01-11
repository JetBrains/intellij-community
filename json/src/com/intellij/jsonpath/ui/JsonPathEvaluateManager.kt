// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.icons.AllIcons
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.EDT

@Service
class JsonPathEvaluateManager(private val project: Project) {
  fun openToolwindow() {
    open()
  }

  fun evaluateExpression(jsonPathExpr: String) {
    open {
      it.setExpression(jsonPathExpr)
    }
  }

  fun evaluateFromJson(json: String) {
    open {
      it.setSource(json)
    }
  }

  private fun open(callback: ((ui: JsonPathEvaluateView) -> Unit)? = null) {
    EDT.assertIsEdt()

    val toolWindowManager = ToolWindowManager.getInstance(project)
    var toolWindow = toolWindowManager.getToolWindow(EVALUATE_TOOLWINDOW_ID)
    if (toolWindow == null) {
      toolWindow = registerToolwindow(toolWindowManager)
    }
    toolWindow.activate(Runnable {
      val ui = toolWindow.contentManager.getContent(0)?.component as? JsonPathEvaluateView

      if (ui != null) {
        callback?.invoke(ui)
      }
    }, true)
  }

  private fun registerToolwindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val view = JsonPathEvaluateView(project)
    val toolWindow = toolWindowManager.registerToolWindow(
      RegisterToolWindowTask(
        id = EVALUATE_TOOLWINDOW_ID,
        anchor = ToolWindowAnchor.BOTTOM,
        component = view.component,
        icon = AllIcons.Toolwindows.ToolWindowFind,
        stripeTitle = JsonBundle.messagePointer("jsonpath.toolwindow.evaluate")
      )
    )
    Disposer.register(toolWindow.disposable, view)
    return toolWindow
  }

  companion object {
    const val EVALUATE_TOOLWINDOW_ID = "JSONPathEvaluate"

    @JvmField
    val JSON_PATH_EVALUATE_EXPRESSION_KEY: Key<Boolean> = Key.create("JSON_PATH_EVALUATE_EXPRESSION")

    @JvmField
    val JSON_PATH_EVALUATE_SOURCE_KEY: DataKey<JsonFile> = DataKey.create("JSON_PATH_EVALUATE_SOURCE")

    @JvmField
    val JSON_PATH_EVALUATE_RESULT_KEY: Key<Boolean> = Key.create("JSON_PATH_EVALUATE_RESULT")

    @JvmStatic
    fun getInstance(project: Project): JsonPathEvaluateManager {
      return project.getService(JsonPathEvaluateManager::class.java)
    }
  }
}