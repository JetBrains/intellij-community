// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.hint.EditorCodePreview
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.ui.IdeUiService
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.java.codeserver.core.JavaPsiVariableUtil
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil
import com.intellij.util.SmartList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

object InplaceExtractUtils {

  fun showErrorHint(editor: Editor, offset: Int, message: @Nls String) {
    val options = HintManager.HIDE_BY_ESCAPE or HintManager.HIDE_BY_TEXT_CHANGE
    HintManager.getInstance().showErrorHint(editor, message, offset, offset, HintManager.ABOVE, options, 0)
  }

  private fun checkIdentifierName(editor: Editor, file: PsiFile, variableRange: TextRange): Boolean {
    val project = file.project
    val referenceName = file.viewProvider.document.getText(variableRange)
    if (! PsiNameHelper.getInstance(project).isIdentifier(referenceName)) {
      showErrorHint(editor, variableRange.endOffset, JavaRefactoringBundle.message("template.error.invalid.identifier.name"))
      return false
    }
    return true
  }

  suspend fun showExtractErrorHint(editor: Editor, error: @Nls String) {
    val message: @Nls String = JavaRefactoringBundle.message("extract.method.error.prefix") + " " + error
    withContext(Dispatchers.EDT) {
      IdeUiService.getInstance().showErrorHint(editor, message)
    }
  }

  suspend fun showExtractErrorHint(editor: Editor, error: @Nls String, highlightedRanges: List<TextRange>){
    showExtractErrorHint(editor, error)
    withContext(Dispatchers.EDT) {
      highlightErrors(editor, highlightedRanges)
    }
  }

  private fun highlightErrors(editor: Editor, ranges: List<TextRange>) {
    val project = editor.project ?: return
    if (ranges.isEmpty()) return
    val highlightManager = HighlightManager.getInstance(project)
    ranges.forEach { textRange ->
      highlightManager.addRangeHighlight(editor, textRange.startOffset, textRange.endOffset,
                                         EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null)
    }
    WindowManager.getInstance().getStatusBar(project).info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
  }

  fun findTypeParameters(types: List<PsiType>): List<PsiTypeParameter>{
    return types.mapNotNull{ type -> PsiUtil.resolveClassInClassTypeOnly(type) as? PsiTypeParameter }
  }

  fun checkReferenceIdentifier(editor: Editor, file: PsiFile, variableRange: TextRange): Boolean {
    if (!checkIdentifierName(editor, file, variableRange)) {
      return false
    }
    val identifier = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, PsiIdentifier::class.java, false)
    val parent = identifier?.parent
    if (parent is PsiReferenceExpression && parent.multiResolve(false).size != 1) {
      showErrorHint(editor, variableRange.endOffset, JavaRefactoringBundle.message("extract.method.error.method.conflict"))
      return false
    }
    if (parent is PsiMethod && parent.containingClass?.findMethodsBySignature(parent, true).orEmpty().size > 1) {
      showErrorHint(editor, variableRange.endOffset, JavaRefactoringBundle.message("extract.method.error.method.conflict"))
      return false
    }
    return true
  }

  fun checkVariableIdentifier(editor: Editor, file: PsiFile, variableRange: TextRange): Boolean {
    if (!checkIdentifierName(editor, file, variableRange)) {
      return false
    }
    val variable = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, PsiVariable::class.java, false)
    if (variable != null && JavaPsiVariableUtil.findPreviousVariableDeclaration(variable) != null) {
      showErrorHint(editor, variableRange.endOffset, JavaRefactoringBundle.message("template.error.variable.already.defined"))
      return false
    }
    return true
  }

  fun checkClassReference(editor: Editor, file: PsiFile, variableRange: TextRange): Boolean {
    if (!checkIdentifierName(editor, file, variableRange)) {
      return false
    }
    val name = editor.document.getText(variableRange)
    val containingClass = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, PsiClass::class.java, false)
    val conflictsInParentClasses = generateSequence(containingClass?.containingClass) { parentClass -> parentClass.containingClass }
      .mapNotNull { parentClass -> parentClass.findInnerClassByName(name, false) }
      .toList()
    val conflictsInSameClass = PsiTreeUtil.findChildrenOfType(file, PsiClass::class.java).filter { psiClass -> psiClass.name == name }
    if (conflictsInSameClass.size + conflictsInParentClasses.size > 1) {
      showErrorHint(editor, variableRange.endOffset, JavaRefactoringBundle.message("template.error.class.already.defined", name))
      return false
    }
    return true
  }

  fun createInsertedHighlighting(editor: Editor, range: TextRange): Disposable {
    return createCodeHighlighting(editor, range, DiffColors.DIFF_INSERTED)
  }

  fun createCodeHighlighting(editor: Editor, range: TextRange, color: TextAttributesKey): Disposable {
    val project = editor.project ?: return Disposer.newDisposable()
    val highlighters = SmartList<RangeHighlighter>()
    val manager = HighlightManager.getInstance(project)
    manager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, color, 0, highlighters)
    return Disposable {
      highlighters.forEach { highlighter -> manager.removeSegmentHighlighter(editor, highlighter) }
    }
  }

  fun logStatisticsOnShow(editor: Editor, mouseEvent: MouseEvent? = null){
    val showEvent = mouseEvent
                    ?: KeyEvent(editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, KeyEvent.VK_TAB.toChar())
    InplaceExtractMethodCollector.show.log(editor.project, FusInputEvent(showEvent, javaClass.simpleName))
  }

  private fun logStatisticsOnHide(project: Project, popupProvider: ExtractMethodPopupProvider){
    InplaceExtractMethodCollector.hide.log(project, popupProvider.isChanged)
    if (popupProvider.annotate != popupProvider.annotateDefault) {
      val change = if (popupProvider.annotate == true) ExtractMethodSettingChange.AnnotateOn else ExtractMethodSettingChange.AnnotateOff
      logSettingsChange(project, change)
    }
    if (popupProvider.makeStatic != popupProvider.makeStaticDefault) {
      val change = when {
        popupProvider.makeStatic == true && popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticWithFieldsOn
        popupProvider.makeStatic == false && popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticWithFieldsOff
        popupProvider.makeStatic == true && ! popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticOn
        popupProvider.makeStatic == false && ! popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticOff
        else -> null
      }
      logSettingsChange(project, change)
    }
  }

  private fun logSettingsChange(project: Project, settingsChange: ExtractMethodSettingChange?){
    if (settingsChange != null) {
      InplaceExtractMethodCollector.settingsChanged.log(project, settingsChange)
    }
  }

  fun navigateToTemplateVariable(editor: Editor) {
    val textRange = TemplateManagerImpl.getTemplateState(editor)?.currentVariableRange ?: return
    navigateToEditorOffset(editor, textRange.endOffset)
  }

  private fun navigateToEditorOffset(editor: Editor, offset: Int?) {
    if (offset == null) return
    val project: Project = editor.project ?: return
    val file: VirtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    val descriptor = OpenFileDescriptor(project, file, offset)
    descriptor.navigate(true)
    descriptor.dispose()
  }

  fun addInlaySettingsElement(templateState: TemplateState, settingsPopup: ExtractMethodPopupProvider): Inlay<PresentationRenderer>? {
    val editor = templateState.editor as? EditorImpl ?: return null
    val project = editor.project ?: return null
    val offset = templateState.currentVariableRange?.endOffset ?: return null
    val presentation = TemplateInlayUtil.createSettingsPresentation(editor) { onClickEvent -> logStatisticsOnShow(editor, onClickEvent) }

    val templateElement = object : TemplateInlayUtil.SelectableTemplateElement(presentation) {
      override fun onSelect(templateState: TemplateState) {
        super.onSelect(templateState)
        logStatisticsOnShow(editor)
      }
    }
    presentation.addSelectionListener { isSelected ->
      if (!isSelected) {
        logStatisticsOnHide(project, settingsPopup)
      }
    }
    return TemplateInlayUtil.createNavigatableButtonWithPopup(templateState.editor, offset, presentation, settingsPopup.panel,
                                                              templateElement, isPopupAbove = false)
  }

  fun addPreview(preview: EditorCodePreview, editor: Editor, lines: IntRange, navigatableOffset: Int){
    val navigatableMarker = createGreedyRangeMarker(editor.document, TextRange(navigatableOffset, navigatableOffset))
    Disposer.register(preview) { navigatableMarker.dispose() }
    preview.addPreview(lines, onClickAction = { navigateToEditorOffset(editor, navigatableMarker.asTextRange?.endOffset) })
  }

  inline fun <reified T: PsiElement> findElementAt(file: PsiFile, range: RangeMarker?): T? {
    val offset = range?.asTextRange?.startOffset ?: return null
    return PsiTreeUtil.findElementOfClassAtOffset(file, offset, T::class.java, false)
  }

  internal fun createGreedyRangeMarker(document: Document, range: TextRange): RangeMarker {
    return document.createRangeMarker(range).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
    }
  }

  internal fun textRangeOf(first: PsiElement, last: PsiElement): TextRange {
    return TextRange(first.textRange.startOffset, last.textRange.endOffset)
  }

  private fun IntRange.trim(maxLength: Int) = first until first + minOf(maxLength, last - first + 1)

  fun getLinesFromTextRange(document: Document, range: TextRange, maxLength: Int): IntRange {
    val lines = document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)
    return lines.trim(maxLength)
  }

  fun getLinesFromTextRange(document: Document, range: TextRange): IntRange {
    return document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)
  }

  fun createPreview(editor: Editor, methodRange: TextRange, methodOffset: Int, callRange: TextRange?, callOffset: Int?): EditorCodePreview {
    val codePreview = EditorCodePreview.create(editor)
    val highlighting = createInsertedHighlighting(editor, methodRange)
    Disposer.register(codePreview, highlighting)
    if (callRange != null && callOffset != null) {
      addPreview(codePreview, editor, getLinesFromTextRange(editor.document, callRange), callOffset)
    }
    addPreview(codePreview, editor, getLinesFromTextRange(editor.document, methodRange).trim(4), methodOffset)
    return codePreview
  }

}