// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewHighlightingChildrenBuilder.toProblemNodes
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState

internal class ProblemsContextGroupNode(
  val parent: ProblemsContextNode,
  val group: String,
  val problems: Collection<Problem>,
) : Node(parent) {

  override fun getLeafState(): LeafState =
    LeafState.NEVER

  override fun getName(): String =
    group

  override fun update(project: Project, presentation: PresentationData) {
    presentation.addText(name, REGULAR_ATTRIBUTES)
  }

  override fun getChildren(): List<ProblemNode> =
    problems.toProblemNodes(this, parent.parent.file)

  override fun hashCode(): Int =
    group.hashCode() * 31 + parent.hashCode()

  override fun equals(other: Any?): Boolean {
    return this === other ||
           other is ProblemsContextGroupNode &&
           other.parent == parent &&
           other.group == group
  }
}
