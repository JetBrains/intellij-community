// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.editor.impl.multiverse.createCodeInsightContextPresentation
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
class ProblemsContextNode(
  val parent: FileNode,
  val contextGroup: CodeInsightContext,
  val problems: Collection<Problem>,
  val isGroupIdToolSwitchedOn: () -> Boolean
) : Node(parent) {
  val myIcon: Icon? = createCodeInsightContextPresentation(contextGroup, parent.project).icon

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getName(): String {
    val presentation = createCodeInsightContextPresentation(contextGroup, parent.project)
    return presentation.text
  }

  override fun update(project: Project, presentation: PresentationData) {
    presentation.addText(name, REGULAR_ATTRIBUTES)
    presentation.setIcon(myIcon)
  }

  private fun getNodesForContext(problems: List<Problem>): Collection<Node> {
    return problems.map { ProblemNode(this, parent.file, it) }
  }

  override fun getChildren(): List<Node> {
    if (!isGroupIdToolSwitchedOn()) {
      return problems.map { ProblemNode(this, parent.file, it) }
    }
    else {
      return problems.groupBy { it.group }.flatMap { (group, problems) ->
        if (group != null) {
          listOf(ProblemsContextGroupNode(this, group, problems))
        }
        else {
          getNodesForContext(problems)
        }
      }
    }

  }

  override fun hashCode(): Int = contextGroup.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? ProblemsContextNode ?: return false
    return that.contextGroup == contextGroup
  }
}