// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems.pass

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.SmartList
import java.awt.Color
import java.awt.event.MouseEvent

private val PREVIEW_PROBLEMS_KEY = Key.create<Set<Problem>>("preview.problems.key")
private const val ID = "java.RelatedProblems"

internal fun isCodeVisionEnabled(ignored: Project): Boolean {
  val settings = CodeVisionSettings.getInstance()
  return settings.codeVisionEnabled && settings.isProviderEnabled(ID)
}

internal class ProjectProblemCodeVisionProvider : JavaCodeVisionProviderBase() {
  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    // we want to let this provider work only in tests dedicated for code vision, otherwise they harm performance
    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest()) {
      return emptyList()
    }

    val project = editor.project ?: return emptyList()
    val previewProblems = PREVIEW_PROBLEMS_KEY.get(editor)
    if (previewProblems != null) {
      val problem = previewProblems.first()
      val lensColor = getCodeVisionColor()
      val lensPair = createLensPair(problem.context as PsiMethod, lensColor, previewProblems)
      return listOf(lensPair)
    }

    val problems = ProjectProblemUtils.getReportedProblems(editor)
    if (!CodeVisionSettings.getInstance().isProviderEnabled(PlatformCodeVisionIds.PROBLEMS.key)) {
      if (!problems.isEmpty()) {
        ProjectProblemUtils.reportProblems(editor, emptyMap())
        updateHighlighters(project, editor, SmartList())
      }
      return emptyList()
    }

    val prevState = FileStateUpdater.getState(psiFile) ?: return emptyList()
    val prevChanges = getPrevChanges(prevState.changes, problems.keys)
    val curState = findState(psiFile, prevState.snapshot, prevChanges)
    val changes = curState.changes
    val snapshot = curState.snapshot
    val editorManager = FileEditorManager.getInstance(project)
    val isInSplitEditorMode = editorManager.selectedEditors.size > 1
    collectProblems(changes = changes, oldChanges = prevState.changes, oldProblems = problems, isInSplitEditorMode = isInSplitEditorMode)

    ProjectProblemUtils.reportProblems(editor, problems)
    val allChanges = HashMap(changes)
    for ((key, value) in prevChanges) {
      allChanges.putIfAbsent(key, value)
    }
    val fileState = FileState(snapshot, allChanges)
    updateState(psiFile, fileState)

    val highlighters = SmartList<HighlightInfo>()
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()

    val codeVisionColor = getCodeVisionColor()
    for ((psiMember, memberProblems) in problems) {
      val namedElement = (psiMember as? PsiNameIdentifierOwner) ?: continue
      val identifier = namedElement.nameIdentifier ?: continue
      val lensPair = createLensPair(psiMember = psiMember, codeVisionColor = codeVisionColor, memberProblems = memberProblems)
      lenses.add(lensPair)
      highlighters.add(ProjectProblemUtils.createHighlightInfo(editor, psiMember, identifier))
    }

    updateHighlighters(project = project, editor = editor, highlighters = highlighters)
    return lenses
  }

  private fun createLensPair(psiMember: PsiMember,
                             codeVisionColor: Color,
                             memberProblems: Set<Problem?>): Pair<TextRange, ClickableRichTextCodeVisionEntry> {
    val text = JavaBundle.message("project.problems.hint.text", memberProblems.size)
    val richText = RichText(text)
    richText.setForeColor(codeVisionColor)
    val entry = ClickableRichTextCodeVisionEntry(id, richText, longPresentation = text, onClick = ClickHandler(psiMember))
    return InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(psiMember) to entry
  }

  override fun preparePreview(editor: Editor, file: PsiFile) {
    val method = (file as PsiJavaFile).classes[0].methods[0]
    editor.putUserData(PREVIEW_PROBLEMS_KEY, setOf(Problem(method, method)))
  }

  private fun updateHighlighters(project: Project, editor: Editor, highlighters: MutableList<HighlightInfo>) {
    ApplicationManager.getApplication().invokeLater(
      {
        val fileTextLength = editor.document.textLength
        val colorsScheme = editor.colorsScheme
        UpdateHighlightersUtil.setHighlightersToEditor(project, editor.document, 0, fileTextLength, highlighters, colorsScheme, -1)
      },
      ModalityState.nonModal(),
      project.disposed,
    )
  }

  private fun getCodeVisionColor(): Color {
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    return globalScheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES).foregroundColor ?: globalScheme.defaultForeground
  }

  override val name: String
    get() = JavaBundle.message("title.related.problems.inlay.hints")

  override val relativeOrderings = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast)
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = ID
  override val groupId: String
    get() = PlatformCodeVisionIds.PROBLEMS.key
}

private fun collectProblems(changes: Map<PsiMember, ScopedMember?>,
                            oldChanges: Map<PsiMember, ScopedMember?>,
                            oldProblems: MutableMap<PsiMember, Set<Problem>>,
                            isInSplitEditorMode: Boolean) {
  var changes = changes
  if (isInSplitEditorMode && changes.isEmpty()) {
    changes = oldChanges
  }
  for ((curMember, prevMember) in changes) {
    if (hasOtherElementsOnSameLine(curMember)) {
      oldProblems.remove(curMember)
      continue
    }

    val memberProblems = collect(prevMember = prevMember, curMember = curMember)
    if (memberProblems.isNullOrEmpty()) {
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
    if (prevSibling !is PsiWhiteSpace && !prevSibling.text.isEmpty()) {
      return true
    }
    prevSibling = prevSibling.prevSibling
  }
  return false
}

private class ClickHandler(member: PsiMember) : (MouseEvent?, Editor) -> Unit {
  private val pointer = SmartPointerManager.createPointer(member)

  override fun invoke(event: MouseEvent?, editor: Editor) {
    if (event == null) {
      return
    }
    val member = pointer.element ?: return
    ProjectProblemUtils.showProblems(editor, member)
  }
}

private fun getPrevChanges(prevChanges: Map<PsiMember, ScopedMember?>, reportedMembers: Set<PsiMember>): Map<PsiMember, ScopedMember?> {
  if (reportedMembers.isEmpty()) {
    return prevChanges
  }

  val changes = HashMap(prevChanges)
  for (it in reportedMembers) {
    changes.putIfAbsent(it, null)
  }
  return changes
}