// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleTextAttributes
import java.awt.event.ActionEvent

class ProblemsViewProjectErrorsPanelProvider(private val project: Project) : ProblemsViewPanelProvider {
  companion object {
    const val ID = "ProjectErrors"
  }
  private val ACTION_IDS = listOf("CompileDirty", "InspectCode")

  override fun create(): ProblemsViewTab? {
    val state = ProblemsViewState.getInstance(project)
    val panel = ProblemsViewPanel(project, ID, state, ProblemsViewBundle.messagePointer("problems.view.project"))
    panel.treeModel.root = CollectorBasedRoot(panel)

    val status = panel.tree.emptyText
    status.text = ProblemsViewBundle.message("problems.view.project.empty")

    if (Registry.`is`("ide.problems.view.empty.status.actions")) {
      val or = ProblemsViewBundle.message("problems.view.project.empty.or")
      var index = 0
      for (id in ACTION_IDS) {
        val action = ActionUtil.getAction(id) ?: continue
        val text = action.templateText
        if (text == null || text.isBlank()) continue
        if (index == 0) {
          status.appendText(".")
          status.appendLine("")
        }
        else {
          status.appendText(" ").appendText(or).appendText(" ")
        }
        status.appendText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) { event: ActionEvent? ->
          ActionUtil.invokeAction(action, panel, "ProblemsView", null, null)
        }
        val shortcut = KeymapUtil.getFirstKeyboardShortcutText(action)
        if (!shortcut.isBlank()) status.appendText(" (").appendText(shortcut).appendText(")")
        index++
      }
    }

    return panel
  }
}