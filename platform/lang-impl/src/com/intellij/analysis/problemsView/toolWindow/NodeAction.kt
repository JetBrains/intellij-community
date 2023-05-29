// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.StringSelection
import javax.swing.JTree

internal class CopyProblemDescriptionAction : NodeAction<Problem>() {
  override fun getData(node: Any?): Problem? = (node as? ProblemNode)?.problem
  override fun actionPerformed(data: Problem) {
    CopyPasteManager.getInstance().setContents(StringSelection(data.description ?: data.text))
  }
}

internal abstract class NodeAction<Data> : DumbAwareAction() {
  abstract fun getData(node: Any?): Data?
  abstract fun actionPerformed(data: Data)
  open fun isEnabled(data: Data): Boolean = true

  override fun update(event: AnActionEvent) {
    val data = getData(getSelectedNode(event))
    event.presentation.isEnabledAndVisible = data != null && isEnabled(data)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val data = getData(getSelectedNode(event))
    if (data != null) actionPerformed(data)
  }
}

private fun getSelectedNode(event: AnActionEvent): Any? {
  val tree = event.getData(CONTEXT_COMPONENT) as? JTree
  return tree?.selectionPath?.lastPathComponent
}