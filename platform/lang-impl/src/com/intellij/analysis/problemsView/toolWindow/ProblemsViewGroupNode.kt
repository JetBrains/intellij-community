// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState

internal class ProblemsViewGroupNode(val parent: FileNode, val group: String, val problems: Collection<Problem>) : Node(parent) {

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getName(): String = group

  override fun update(project: Project, presentation: PresentationData) = presentation.addText(name, REGULAR_ATTRIBUTES)

  override fun getChildren(): List<ProblemNode> = problems.map { ProblemNode(this, parent.file, it) }

  override fun hashCode(): Int = group.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? ProblemsViewGroupNode ?: return false
    return that.parent == parent && that.group == group
  }
}
