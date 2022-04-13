// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.hint.EditorCodePreview
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addInlaySettingsElement
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addPreview
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addTemplateFinishedListener
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeBasedDisposable
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeSignatureGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createGreedyRangeMarker
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createInsertedHighlighting
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createNavigationGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.findElementAt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.getEditedTemplateText
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.getLinesFromTextRange
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.showInEditor
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.trim
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider
import com.intellij.refactoring.suggested.range
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls

class EditorState(val editor: Editor){
  private val caretToRevert: Int = editor.caretModel.currentCaret.offset
  private val selectionToRevert: TextRange? = ExtractMethodHelper.findEditorSelection(editor)
  private val textToRevert: String = editor.document.text

  fun revert() {
    val project = editor.project
    ApplicationManager.getApplication().runWriteAction {
      editor.document.setText(textToRevert)
      if (project != null) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
      }
    }
    editor.caretModel.moveToOffset(caretToRevert)
    if (selectionToRevert != null) {
      editor.selectionModel.setSelection(selectionToRevert.startOffset, selectionToRevert.endOffset)
    }
  }
}

private fun installGotItTooltips(editor: Editor, navigationGotItRange: TextRange?, changeSignatureGotItRange: TextRange?){
  if (navigationGotItRange == null || changeSignatureGotItRange == null) {
    return
  }
  val parentDisposable = Disposer.newDisposable().also { EditorUtil.disposeWithEditor(editor, it) }
  val previousBalloonFuture = createNavigationGotIt(parentDisposable)?.showInEditor(editor, navigationGotItRange)
  val disposable = createChangeBasedDisposable(editor)
  val caretListener = object: CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      if (editor.logicalPositionToOffset(event.newPosition) in changeSignatureGotItRange) {
        previousBalloonFuture?.thenAccept { balloon -> balloon.hide(true) }
        createChangeSignatureGotIt(parentDisposable)?.showInEditor(editor, changeSignatureGotItRange)
        Disposer.dispose(disposable)
      }
    }
  }
  editor.caretModel.addCaretListener(caretListener, disposable)
}

class InplaceMethodExtractor(private val editor: Editor,
                             private val range: TextRange,
                             private val targetClass: PsiClass,
                             private val popupProvider: ExtractMethodPopupProvider,
                             private val initialMethodName: String)
  : InplaceRefactoring(editor, null, targetClass.project) {

  companion object {
    private val INPLACE_METHOD_EXTRACTOR = Key<InplaceMethodExtractor>("InplaceMethodExtractor")

    fun getActiveExtractor(editor: Editor): InplaceMethodExtractor? {
      return TemplateManagerImpl.getTemplateState(editor)?.properties?.get(INPLACE_METHOD_EXTRACTOR) as? InplaceMethodExtractor
    }

    private fun setActiveExtractor(editor: Editor, extractor: InplaceMethodExtractor) {
      TemplateManagerImpl.getTemplateState(editor)?.properties?.put(INPLACE_METHOD_EXTRACTOR, extractor)
    }
  }

  init {
    initPopupOptionsAdvertisement()
  }

  private val extractor: DuplicatesMethodExtractor = DuplicatesMethodExtractor()

  private val editorState = EditorState(editor)

  private val file: PsiFile = targetClass.containingFile

  private var methodIdentifierRange: RangeMarker? = null

  private var callIdentifierRange: RangeMarker? = null

  private val disposable = Disposer.newDisposable()

  fun prepareCodeForTemplate() {
    val elements = ExtractSelector().suggestElementsToExtract(file, range)
    MethodExtractor.sendRefactoringStartedEvent(elements.toTypedArray())
    val (callElements, method) = extractor.extract(targetClass, elements, initialMethodName, popupProvider.makeStatic ?: false)
    val callExpression = PsiTreeUtil.findChildOfType(callElements.first(), PsiMethodCallExpression::class.java, false)
                         ?: throw IllegalStateException()
    val methodIdentifier = method.nameIdentifier ?: throw IllegalStateException()
    val callIdentifier = callExpression.methodExpression.referenceNameElement ?: throw IllegalStateException()

    methodIdentifierRange = createGreedyRangeMarker(editor.document, methodIdentifier.textRange)
    callIdentifierRange = createGreedyRangeMarker(editor.document, callIdentifier.textRange)

    editor.caretModel.moveToOffset(callExpression.textRange.startOffset)
    setElementToRename(method)

    val highlighting = createInsertedHighlighting(editor, method.textRange)
    Disposer.register(disposable, highlighting)

    val codePreview = EditorCodePreview.create(editor)
    Disposer.register(disposable, codePreview)
    val callRange = TextRange(callElements.first().textRange.startOffset, callElements.last().textRange.endOffset)
    addPreview(codePreview, editor, getLinesFromTextRange(editor.document, callRange).trim(4), callIdentifier.textRange.endOffset)
    addPreview(codePreview, editor, getLinesFromTextRange(editor.document, method.textRange), methodIdentifier.textRange.endOffset)
  }

  override fun performInplaceRefactoring(nameSuggestions: LinkedHashSet<String>?): Boolean {
    try {
      ApplicationManager.getApplication().runWriteAction { prepareCodeForTemplate() }
      val succeed = super.performInplaceRefactoring(nameSuggestions)
      if (!succeed) {
        Disposer.dispose(disposable)
      }
      return succeed
    } catch (e: Throwable) {
      Disposer.dispose(disposable)
      throw e
    }
  }

  override fun checkLocalScope(): PsiElement {
    return targetClass
  }

  override fun collectRefs(referencesSearchScope: SearchScope?): MutableCollection<PsiReference> {
    return SmartList()
  }

  override fun revertState() {
    super.revertState()
    editorState.revert()
  }

  override fun afterTemplateStart() {
    setActiveExtractor(editor, this)
    val templateState = TemplateManagerImpl.getTemplateState(myEditor)
    if (templateState == null) {
      Disposer.dispose(disposable)
      throw IllegalStateException("Failed to start code template.")
    }
    Disposer.register(templateState) { SuggestedRefactoringProvider.getInstance(myProject).reset() }
    Disposer.register(templateState, disposable)
    super.afterTemplateStart()
    addTemplateFinishedListener(templateState, ::afterTemplateFinished)
    popupProvider.setChangeListener {
      val shouldAnnotate = popupProvider.annotate
      if (shouldAnnotate != null) {
        PropertiesComponent.getInstance(myProject).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, shouldAnnotate, true)
      }
      restartInplace(getEditedTemplateText(templateState))
    }
    popupProvider.setShowDialogAction { actionEvent -> restartInDialog(actionEvent == null) }
    addInlaySettingsElement(templateState, popupProvider)
  }

  private fun getIdentifierError(methodName: String, methodCall: PsiMethodCallExpression?): @Nls String? {
    return if (! PsiNameHelper.getInstance(myProject).isIdentifier(methodName)) {
      JavaRefactoringBundle.message("extract.method.error.invalid.name")
    } else if (methodCall?.resolveMethod() == null) {
      JavaRefactoringBundle.message("extract.method.error.method.conflict")
    } else {
      null
    }
  }

  private fun afterTemplateFinished(templateEditedText: String?) {
    val methodName = templateEditedText ?: return
    val call = findElementAt<PsiMethodCallExpression>(file, callIdentifierRange)
    val errorMessage = getIdentifierError(methodName, call)
    if (errorMessage != null) {
      ApplicationManager.getApplication().invokeLater {
        restartInplace(methodName)
        CommonRefactoringUtil.showErrorHint(myProject, editor, errorMessage, ExtractMethodHandler.getRefactoringName(), null)
      }
    } else {
      val extractedMethod = findElementAt<PsiMethod>(file, methodIdentifierRange) ?: return
      InplaceExtractMethodCollector.executed.log(initialMethodName != methodName)
      installGotItTooltips(editor, callIdentifierRange?.range, methodIdentifierRange?.range)
      MethodExtractor.sendRefactoringDoneEvent(extractedMethod)
      extractor.postprocess(editor, extractedMethod)
    }
  }

  private fun setMethodName(methodName: String) {
    val callRange = callIdentifierRange ?: return
    val methodRange = methodIdentifierRange ?: return
    if (callRange.isValid && callRange.isValid) {
      editor.document.replaceString(callRange.startOffset, callRange.endOffset, methodName)
      editor.document.replaceString(methodRange.startOffset, methodRange.endOffset, methodName)
      PsiDocumentManager.getInstance(myProject).commitDocument(editor.document)
    }
  }

  fun restartInDialog(isLinkUsed: Boolean = false) {
    InplaceExtractMethodCollector.openExtractDialog.log(myProject, isLinkUsed)
    performCleanup()
    val elements = ExtractSelector().suggestElementsToExtract(targetClass.containingFile, range)
    val methodRange = callIdentifierRange?.range
    val methodName = if (methodRange != null) editor.document.getText(methodRange) else ""
    extractor.extractInDialog(targetClass, elements, methodName, popupProvider.makeStatic ?: false)
  }

  private fun restartInplace(methodName: String?) {
    performCleanup()
    WriteCommandAction.runWriteCommandAction(myProject) {
      val inplaceExtractor = InplaceMethodExtractor(editor, range, targetClass, popupProvider, initialMethodName)
      inplaceExtractor.performInplaceRefactoring(linkedSetOf())
      if (methodName != null) {
        inplaceExtractor.setMethodName(methodName)
      }
    }
  }

  override fun performRefactoring(): Boolean {
    return false
  }

  override fun performCleanup() {
    revertState()
  }

  override fun shouldSelectAll(): Boolean = false

  override fun getCommandName(): String = ExtractMethodHandler.getRefactoringName()

}