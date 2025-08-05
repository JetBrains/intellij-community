// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState
import org.jetbrains.annotations.ApiStatus
import java.util.Objects.hash

class ProblemNode(parent: Node, val file: VirtualFile, override val problem: Problem) : Node(parent), ProblemNodeI {
  init {
    Logger.getInstance(javaClass).assertTrue(project != null, this)
  }

  private var text: String = ""

  private var line: Int = 0

  private var column: Int = 0

  private var severity: Int = 0

  override fun getText(): String = text

  override fun getLine(): Int = line

  override fun getColumn(): Int = column

  override fun getSeverity(): Int = severity

  @ApiStatus.Experimental
  var context: CodeInsightContext? = null
    private set

  override val descriptor: OpenFileDescriptor
    get() = OpenFileDescriptor(project!!, file, line, column)

  override fun getLeafState(): LeafState = LeafState.ALWAYS

  override fun getName(): String = text

  override fun getVirtualFile(): VirtualFile = file

  override fun getNavigatable(): Navigatable? = problem as? Navigatable ?: descriptor

  override fun update(project: Project, presentation: PresentationData) {
    // update values before comparison because of general contract
    text = problem.text
    line = (problem as? FileProblem)?.line ?: -1
    column = (problem as? FileProblem)?.column ?: -1
    severity = (problem as? HighlightingProblem)?.severity ?: -1
    context = (problem as? HighlightingProblem)?.highlighter?.codeInsightContext
    presentation.addText(text, REGULAR_ATTRIBUTES)
    presentation.setIcon(problem.icon)
    presentation.tooltip = problem.description
    if (line >= 0) presentation.addText(" :${line + 1}", GRAYED_ATTRIBUTES)
  }

  override fun hashCode(): Int = hash(project, problem)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? ProblemNode ?: return false
    return that.project == project && that.problem == problem
  }
}
