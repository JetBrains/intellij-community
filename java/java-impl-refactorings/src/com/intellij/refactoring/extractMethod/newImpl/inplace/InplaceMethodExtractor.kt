// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addInlaySettingsElement
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.checkReferenceIdentifier
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeBasedDisposable
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeSignatureGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createGreedyRangeMarker
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createNavigationGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createPreview
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.findElementAt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.showInEditor
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.suggested.range

class InplaceMethodExtractor(private val editor: Editor,
                             private val range: TextRange,
                             private val targetClass: PsiClass,
                             private val popupProvider: ExtractMethodPopupProvider,
                             private val initialMethodName: String) {

  companion object {
    private val INPLACE_METHOD_EXTRACTOR = Key<InplaceMethodExtractor>("InplaceMethodExtractor")

    fun getActiveExtractor(editor: Editor): InplaceMethodExtractor? {
      return TemplateManagerImpl.getTemplateState(editor)?.properties?.get(INPLACE_METHOD_EXTRACTOR) as? InplaceMethodExtractor
    }

    private fun setActiveExtractor(editor: Editor, extractor: InplaceMethodExtractor) {
      TemplateManagerImpl.getTemplateState(editor)?.properties?.put(INPLACE_METHOD_EXTRACTOR, extractor)
    }
  }

  private val file: PsiFile = targetClass.containingFile

  private fun createExtractor(): DuplicatesMethodExtractor {
    val elements = ExtractSelector().suggestElementsToExtract(file, range)
    val shouldBeStatic = popupProvider.makeStatic ?: false
    return DuplicatesMethodExtractor.create(targetClass, elements, initialMethodName, shouldBeStatic)
  }

  private val extractor: DuplicatesMethodExtractor = createExtractor()

  private val editorState = EditorState(file.project, editor)

  private var methodIdentifierRange: RangeMarker? = null

  private var callIdentifierRange: RangeMarker? = null

  private val disposable = Disposer.newDisposable()

  private val project = file.project

  fun extractAndRunTemplate(suggestedNames: LinkedHashSet<String>) {
    try {
      val startMarkAction = StartMarkAction.start(editor, project, ExtractMethodHandler.getRefactoringName())
      Disposer.register(disposable) { FinishMarkAction.finish(project, editor, startMarkAction) }
      val elements = ExtractSelector().suggestElementsToExtract(file, range)
      MethodExtractor.sendRefactoringStartedEvent(elements.toTypedArray())
      val (callElements, method) = extractor.extract()

      val callExpression = PsiTreeUtil.findChildOfType(callElements.firstOrNull(), PsiMethodCallExpression::class.java, false)
      val callIdentifier = callExpression?.methodExpression?.referenceNameElement
      callIdentifierRange = if (callIdentifier != null) createGreedyRangeMarker(editor.document, callIdentifier.textRange) else null
      val callRange = if (callElements.isNotEmpty()) {
        TextRange(callElements.first().textRange.startOffset, callElements.last().textRange.endOffset)
      } else {
        null
      }

      val methodIdentifier = method.nameIdentifier ?: throw IllegalStateException()
      methodIdentifierRange = createGreedyRangeMarker(editor.document, methodIdentifier.textRange)
      val codePreview = createPreview(editor, method.textRange, methodIdentifier.textRange.endOffset, callRange,
                                      callIdentifierRange?.textRange?.endOffset)
      Disposer.register(disposable, codePreview)

      val templateField = if (callIdentifier != null) {
        TemplateField(callIdentifier.textRange, listOf(methodIdentifier.textRange))
      } else {
        TemplateField(methodIdentifier.textRange)
      }
      val templateFieldWithSettings = templateField
        .withCompletionNames(suggestedNames.toList())
        .withCompletionHint(InplaceRefactoring.getPopupOptionsAdvertisement())
        .withValidation { variableRange -> checkReferenceIdentifier(editor, file, variableRange) }
      val templateState = ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
        .enableRestartForHandler(ExtractMethodHandler::class.java)
        .onBroken {
          editorState.revert()
        }
        .onSuccess {
          val range = callIdentifierRange?.range ?: return@onSuccess
          val methodName = editor.document.getText(range)
          val extractedMethod = findElementAt<PsiMethod>(file, methodIdentifierRange) ?: return@onSuccess
          InplaceExtractMethodCollector.executed.log(initialMethodName != methodName)
          installGotItTooltips(editor, callIdentifierRange?.range, methodIdentifierRange?.range)
          MethodExtractor.sendRefactoringDoneEvent(extractedMethod)
          extractor.replaceDuplicates(editor, extractedMethod)
        }
        .disposeWithTemplate(disposable)
        .createTemplate(file, listOf(templateFieldWithSettings))
      afterTemplateStart(templateState)
    } catch (e: Throwable) {
      Disposer.dispose(disposable)
      throw e
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

  private fun afterTemplateStart(templateState: TemplateState) {
    setActiveExtractor(editor, this)
    popupProvider.setChangeListener {
      val shouldAnnotate = popupProvider.annotate
      if (shouldAnnotate != null) {
        PropertiesComponent.getInstance(project).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, shouldAnnotate, true)
      }
      val makeStatic = popupProvider.makeStatic
      if (!popupProvider.staticPassFields && makeStatic != null) {
        JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = makeStatic
      }
      restartInplace()
    }
    popupProvider.setShowDialogAction { actionEvent -> restartInDialog(actionEvent == null) }
    addInlaySettingsElement(templateState, popupProvider)?.also { inlay ->
      Disposer.register(disposable, inlay)
    }
  }

  private fun setMethodName(methodName: String) {
    val callRange = callIdentifierRange ?: return
    val methodRange = methodIdentifierRange ?: return
    if (callRange.isValid && callRange.isValid) {
      editor.document.replaceString(callRange.startOffset, callRange.endOffset, methodName)
      editor.document.replaceString(methodRange.startOffset, methodRange.endOffset, methodName)
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    }
  }

  fun restartInDialog(isLinkUsed: Boolean = false) {
    val methodRange = callIdentifierRange?.range
    val methodName = if (methodRange != null) editor.document.getText(methodRange) else ""
    InplaceExtractMethodCollector.openExtractDialog.log(project, isLinkUsed)
    TemplateManagerImpl.getTemplateState(editor)?.gotoEnd(true)
    val elements = ExtractSelector().suggestElementsToExtract(targetClass.containingFile, range)
    extractInDialog(targetClass, elements, methodName, popupProvider.makeStatic ?: extractor.extractOptions.isStatic)
  }

  private fun restartInplace() {
    val startTime = System.currentTimeMillis()
    val identifierRange = callIdentifierRange?.range
    val methodName = if (identifierRange != null) editor.document.getText(identifierRange) else null
    TemplateManagerImpl.getTemplateState(editor)?.gotoEnd(true)
    WriteCommandAction.writeCommandAction(project).withName(ExtractMethodHandler.getRefactoringName()).run<Throwable> {
      val inplaceExtractor = InplaceMethodExtractor(editor, range, targetClass, popupProvider, initialMethodName)
      inplaceExtractor.extractAndRunTemplate(linkedSetOf())
      if (methodName != null) {
        inplaceExtractor.setMethodName(methodName)
      }
    }
    val endTime = System.currentTimeMillis()
    InplaceExtractMethodCollector.previewUpdated.log(endTime - startTime)
  }

}