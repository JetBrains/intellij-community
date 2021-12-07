// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.daemon.impl.JavaCodeVisionProviderBase
import com.intellij.codeInsight.daemon.problems.FileState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.findState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.updateState
import com.intellij.codeInsight.daemon.problems.Problem
import com.intellij.codeInsight.daemon.problems.ProblemCollector.Companion.collect
import com.intellij.codeInsight.daemon.problems.ScopedMember
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiWhiteSpace
import java.util.function.Consumer

class ProjectProblemCodeVisionProvider : JavaCodeVisionProviderBase() {
  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    val prevState = FileStateUpdater.getState(psiFile)
    if (prevState == null) return emptyList()
    val project = editor.project ?: return emptyList()

    val problems = ProjectProblemUtils.getReportedProblems(editor)
    val prevChanges = getPrevChanges(prevState.changes, problems.keys)
    val curState = findState(psiFile, prevState.snapshot, prevChanges)
    val changes = curState.changes
    val snapshot = curState.snapshot
    val editorManager = FileEditorManager.getInstance(project)
    val isInSplitEditorMode = editorManager.selectedEditors.size > 1
    collectProblems(changes, prevState.changes, problems, isInSplitEditorMode)

    ProjectProblemUtils.reportProblems(editor, problems)
    val allChanges = HashMap(changes)
    prevChanges.forEach { (key, value) ->
      allChanges.putIfAbsent(key, value)
    }
    val fileState = FileState(snapshot, allChanges)
    updateState(psiFile, fileState)

    val factory = PresentationFactory((editor as EditorImpl))
    val document = editor.document

    //todo

    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    return lenses
  }

  override fun handleClick(editor: Editor, textRange: TextRange) {
    TODO("Not yet implemented")
  }

  override val name: String
    get() = "Related problems"
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = "java.RelatedProblems"
  override val groupId: String
    get() = PlatformCodeVisionIds.PROBLEMS.key

  private fun getPrevChanges(prevChanges: Map<PsiMember, ScopedMember?>,
                             reportedMembers: Set<PsiMember>): Map<PsiMember, ScopedMember?> {
    if (reportedMembers.isEmpty()) return prevChanges
    val changes = HashMap(prevChanges)
    reportedMembers.forEach(Consumer { m: PsiMember? -> changes.putIfAbsent(m, null) })
    return changes
  }

  private fun collectProblems(changes: Map<PsiMember, ScopedMember?>,
                              oldChanges: Map<PsiMember, ScopedMember?>,
                              oldProblems: MutableMap<PsiMember, Set<Problem>>,
                              isInSplitEditorMode: Boolean) {
    var changes = changes
    if (isInSplitEditorMode && changes.isEmpty()) {
      changes = oldChanges
    }
    changes.forEach { (curMember: PsiMember, prevMember: ScopedMember?) ->
      if (hasOtherElementsOnSameLine(curMember)) {
        oldProblems.remove(curMember)
        return@forEach
      }
      val memberProblems = collect(
        prevMember,
        curMember)
      if (memberProblems == null || memberProblems.isEmpty()) {
        oldProblems.remove(curMember)
      }
      else {
        oldProblems[curMember] = memberProblems
      }
    }
  }

  private fun hasOtherElementsOnSameLine(psiMember: PsiMember): Boolean {
    var prevSibling = psiMember.prevSibling
    while (prevSibling != null && !(prevSibling is PsiWhiteSpace && prevSibling.textContains('\n'))) {
      if (prevSibling !is PsiWhiteSpace && !prevSibling.text.isEmpty()) return true
      prevSibling = prevSibling.prevSibling
    }
    return false
  }
}