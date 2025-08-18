// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  override fun getData(node: Any?): Problem? = (node as? ProblemNodeI)?.problem
  override fun actionPerformed(data: List<Problem>) {
    val text = when {
      data.isEmpty() -> null
      data.size == 1 -> data.single().toCopyableText()
      else -> data.joinToString("") { "${it.toCopyableText()}\n" }
    }
    if (text != null) {
      CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
  }
}

private fun Problem.toCopyableText(): String = description ?: text

internal abstract class NodeAction<Data> : DumbAwareAction() {
  abstract fun getData(node: Any?): Data?
  abstract fun actionPerformed(data: List<Data>)
  open fun isEnabled(data: List<Data>): Boolean = true

  override fun update(event: AnActionEvent) {
    val data = getData(event)
    event.presentation.isEnabledAndVisible = data.isNotEmpty() && isEnabled(data)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val data = getData(event)
    if (data.isNotEmpty()) actionPerformed(data)
  }

  private fun getData(event: AnActionEvent): List<Data> = getSelectedNodes(event).mapNotNull { getData(it) }
}

private fun getSelectedNodes(event: AnActionEvent): List<Any> {
  val tree = event.getData(CONTEXT_COMPONENT) as? JTree
  return tree?.selectionPaths?.map { it.lastPathComponent } ?: emptyList()
}
