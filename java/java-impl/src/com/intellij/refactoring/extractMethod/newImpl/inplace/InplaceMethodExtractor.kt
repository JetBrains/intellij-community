// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addInlaySettingsElement
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addTemplateFinishedListener
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeBasedDisposable
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeSignatureGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createCodePreview
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createInsertedHighlighting
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createNavigationGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.getEditedTemplateText
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.getNameIdentifier
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.showInEditor
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider
import com.intellij.refactoring.suggested.range
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.Nls

class InplaceMethodExtractor(private val editor: Editor,
                             private val context: ExtractParameters,
                             private val extractor: InplaceExtractMethodProvider,
                             private val popupProvider: ExtractMethodPopupProvider)
  : InplaceRefactoring(editor, null, context.targetClass.project) {

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

  private val caretToRevert: Int = editor.caretModel.currentCaret.offset

  private val selectionToRevert: TextRange? = ExtractMethodHelper.findEditorSelection(editor)

  private val textToRevert: String = editor.document.text

  private lateinit var method: SmartPsiElementPointer<PsiMethod>

  private lateinit var methodCall: SmartPsiElementPointer<PsiMethodCallExpression>

  private val disposable = Disposer.newDisposable()

  fun prepareCodeForTemplate() {
    val document = editor.document

    val extractedRange = document.createRangeMarker(context.range).also {
      it.isGreedyToLeft = true
      it.isGreedyToRight = true
    }

    val (method, callExpression) = extractMethod(extractor, context)

    this.method = SmartPointerManager.createPointer(method)
    this.methodCall = SmartPointerManager.createPointer(callExpression)

    editor.caretModel.moveToOffset(callExpression.textRange.startOffset)
    setElementToRename(method)

    val highlighting = createInsertedHighlighting(editor, method.textRange)
    Disposer.register(disposable, highlighting)

    val codePreview = createCodePreview(editor, extractedRange.range, this.method, this.methodCall)
    Disposer.register(disposable, codePreview)
  }

  fun extractMethod(extractor: InplaceExtractMethodProvider, parameters: ExtractParameters): Pair<PsiMethod, PsiMethodCallExpression> {
    val project = parameters.targetClass.project
    val file = parameters.targetClass.containingFile
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: throw IllegalStateException()

    val elements = ExtractSelector().suggestElementsToExtract(parameters.targetClass.containingFile, parameters.range)
    MethodExtractor.sendRefactoringStartedEvent(elements.toTypedArray())
    val (method, call) = extractor.extract(parameters.targetClass, elements, parameters.methodName, parameters.static)
    val methodPointer = SmartPointerManager.createPointer(method)
    val callPointer = SmartPointerManager.createPointer(call)
    val manager = PsiDocumentManager.getInstance(project)
    manager.doPostponedOperationsAndUnblockDocument(document)
    manager.commitDocument(document)
    return Pair(methodPointer.element!!, callPointer.element!!)
  }

  private fun installGotItTooltips(){
    val navigationGotItRange = getNameIdentifier(methodCall.element)?.textRange ?: return
    val changeSignatureGotItRange = method.element?.nameIdentifier?.textRange ?: return
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
    return context.targetClass
  }

  override fun revertState() {
    super.revertState()
    WriteCommandAction.runWriteCommandAction(myProject) {
      editor.document.setText(textToRevert)
      PsiDocumentManager.getInstance(myProject).commitDocument(editor.document)
    }
    editor.caretModel.moveToOffset(caretToRevert)
    if (selectionToRevert != null) {
      editor.selectionModel.setSelection(selectionToRevert.startOffset, selectionToRevert.endOffset)
    }
  }

  override fun afterTemplateStart() {
    setActiveExtractor(editor, this)
    val templateState = TemplateManagerImpl.getTemplateState(myEditor) ?: return
    Disposer.register(templateState) { SuggestedRefactoringProvider.getInstance(myProject).reset() }
    Disposer.register(templateState, disposable)
    super.afterTemplateStart()
    addTemplateFinishedListener(templateState, ::afterTemplateFinished)
    popupProvider.setChangeListener { restartInplace(getEditedTemplateText(templateState)) }
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
    val errorMessage = getIdentifierError(methodName, methodCall.element)
    if (errorMessage != null) {
      ApplicationManager.getApplication().invokeLater {
        restartInplace(methodName)
        CommonRefactoringUtil.showErrorHint(myProject, editor, errorMessage, ExtractMethodHandler.getRefactoringName(), null)
      }
    } else {
      val extractedMethod = method.element ?: return
      InplaceExtractMethodCollector.executed.log(context.methodName != methodName)
      installGotItTooltips()
      PsiDocumentManager.getInstance(myProject).commitAllDocuments()
      MethodExtractor.sendRefactoringDoneEvent(extractedMethod)
      extractor.postprocess(editor, extractedMethod)
    }
  }

  private fun setMethodName(methodName: String) {
    val manager = PsiDocumentManager.getInstance(myProject)
    val callNameRange = getNameIdentifier(methodCall.element)?.textRange ?: return
    editor.document.replaceString(callNameRange.startOffset, callNameRange.endOffset, methodName)
    manager.commitDocument(editor.document)
    val methodNameRange = method.element?.nameIdentifier?.textRange ?: return
    editor.document.replaceString(methodNameRange.startOffset, methodNameRange.endOffset, methodName)
    manager.commitDocument(editor.document)
  }

  fun restartInDialog(isLinkUsed: Boolean = false) {
    InplaceExtractMethodCollector.openExtractDialog.log(myProject, isLinkUsed)
    val updatedContext = context.update(method.element?.name ?: context.methodName, popupProvider.annotate, popupProvider.makeStatic)
    performCleanup()
    val elements = ExtractSelector().suggestElementsToExtract(updatedContext.targetClass.containingFile, updatedContext.range)
    extractor.extractInDialog(updatedContext.targetClass, elements, updatedContext.methodName, updatedContext.static)
  }

  private fun ExtractParameters.update(methodName: String, annotate: Boolean?, static: Boolean?): ExtractParameters {
    if (annotate != null) {
      PropertiesComponent.getInstance(myProject).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, annotate, true)
    }

    var context = this.copy(methodName = methodName)
    if (annotate != null) {
      context = context.copy(annotate = annotate)
    }
    if (static != null) {
      context = context.copy(static = static)
    }
    return context
  }

  private fun restartInplace(methodName: String?) {
    val updatedContext = context.update(context.methodName, popupProvider.annotate, popupProvider.makeStatic)
    performCleanup()
    WriteCommandAction.runWriteCommandAction(myProject) {
      val inplaceExtractor = InplaceMethodExtractor(editor, updatedContext, extractor, popupProvider)
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