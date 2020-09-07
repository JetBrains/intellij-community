// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState
import com.intellij.util.DocumentUtil.isValidOffset
import java.util.Objects.hash

internal class ProblemNode(parent: FileNode, val problem: Problem) : Node(parent) {

  val file = parent.file

  var text: String = ""
    private set

  var offset: Int = 0
    private set

  var severity: Int = 0
    private set

  override fun getLeafState() = LeafState.ALWAYS

  override fun getName() = text

  override fun update(project: Project, presentation: PresentationData) {
    // update values before comparison because of general contract
    text = problem.text
    offset = (problem as? FileProblem)?.offset ?: -1
    severity = (problem as? HighlightingProblem)?.severity ?: -1
    presentation.addText(text, REGULAR_ATTRIBUTES)
    presentation.setIcon(problem.icon)
    presentation.tooltip = problem.description
    val document = ProblemsView.getDocument(project, file) ?: return // add nothing if no document
    if (!isValidOffset(offset, document)) return
    val line = document.getLineNumber(offset) + 1
    presentation.addText(" :$line", GRAYED_ATTRIBUTES)
  }

  override fun hashCode() = hash(project, problem)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? ProblemNode ?: return false
    return that.project == project && that.problem == problem
  }
}
