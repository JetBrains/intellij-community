// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableRichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.JavaCodeVisionProviderBase
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.problems.FileState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.findState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.updateState
import com.intellij.codeInsight.daemon.problems.Problem
import com.intellij.codeInsight.daemon.problems.ProblemCollector.Companion.collect
import com.intellij.codeInsight.daemon.problems.ScopedMember
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.ObjectUtils
import com.intellij.util.SmartList
import java.awt.Color
import java.awt.event.MouseEvent
import java.util.function.Consumer

class ProjectProblemCodeVisionProvider : JavaCodeVisionProviderBase() {
  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    // we want to let this provider work only in tests dedicated for code vision, otherwise they harm performance
    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest(editor)) return emptyList()
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

    val document = editor.document
    val highlighters: MutableList<HighlightInfo> = SmartList()
    val lenses: MutableList<Pair<TextRange, CodeVisionEntry>> = ArrayList()

    val lenseColor = getCodeVisionColor()
    problems.forEach { (psiMember: PsiMember?, memberProblems: Set<Problem?>?) ->
      val namedElement = ObjectUtils.tryCast(
        psiMember,
        PsiNameIdentifierOwner::class.java) ?: return@forEach
      val identifier = namedElement.nameIdentifier ?: return@forEach
      val text = JavaBundle.message("project.problems.hint.text", memberProblems.size)
      lenses.add(InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(psiMember) to ClickableRichTextCodeVisionEntry(id, RichText(text).apply { this.setForeColor(lenseColor) }, longPresentation = text, onClick = ClickHandler(psiMember)))
      highlighters.add(ProjectProblemUtils.createHighlightInfo(editor, psiMember!!, identifier))
    }

    ApplicationManager.getApplication()
      .invokeLater({
                     if (project.isDisposed || !psiFile.isValid) return@invokeLater
                     val fileTextLength: Int = psiFile.textLength
                     val colorsScheme = editor.colorsScheme
                     UpdateHighlightersUtil.setHighlightersToEditor(project, document, 0, fileTextLength,
                                                                    highlighters, colorsScheme, -1)
                   },
                   ModalityState.NON_MODAL
      )

    return lenses
  }

  private fun getCodeVisionColor(): Color {
    return EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES).foregroundColor
  }


  override val name: String
    get() = JavaBundle.message("title.related.problems.inlay.hints")
  override val relativeOrderings: List<CodeVisionRelativeOrdering> = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast)
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

  private class ClickHandler(member: PsiMember) : (MouseEvent?, Editor) -> Unit {
    private val pointer = SmartPointerManager.createPointer(member)

    override fun invoke(event: MouseEvent?, editor: Editor) {
      if (event == null) return
      val member = pointer.element ?: return
      ProjectProblemUtils.showProblems(editor, member)
    }
  }
}