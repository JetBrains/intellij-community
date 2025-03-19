// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.CodeFragmentAnalyzer
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class InplaceMethodExtractor(
  private val editor: Editor,
  private val popupProvider: ExtractMethodPopupProvider,
  private val defaultExtractor: DuplicatesMethodExtractor
) {

  companion object {
    private val INPLACE_METHOD_EXTRACTOR = Key<InplaceMethodExtractor>("InplaceMethodExtractor")

    fun getActiveExtractor(editor: Editor): InplaceMethodExtractor? {
      return TemplateManagerImpl.getTemplateState(editor)?.properties?.get(INPLACE_METHOD_EXTRACTOR) as? InplaceMethodExtractor
    }

    private fun setActiveExtractor(editor: Editor, extractor: InplaceMethodExtractor) {
      TemplateManagerImpl.getTemplateState(editor)?.properties?.put(INPLACE_METHOD_EXTRACTOR, extractor)
    }
  }

  internal val extractor: DuplicatesMethodExtractor = createExtractor(defaultExtractor, popupProvider)

  suspend fun extractAndRunTemplate(suggestedNames: List<String>) {
    val disposable = Disposer.newDisposable()
    try {
      val file = readAction { extractor.targetClass.containingFile }
      val project = readAction { file.project }
      val editorState = readAction {  EditorState(file.project, editor) }
      ExtractMethodHelper.mergeWriteCommands(editor, disposable, ExtractMethodHandler.getRefactoringName())
      val (callElements, method) = extractor.extract()

      val callExpression = readAction {
        PsiTreeUtil.findChildOfType(callElements.firstOrNull(), PsiMethodCallExpression::class.java, false)
      }
      val callIdentifier = readAction {
        callExpression?.methodExpression?.referenceNameElement
      }
      val callIdentifierRange = readAction {
        if (callIdentifier != null) createGreedyRangeMarker(editor.document, callIdentifier.textRange) else null
      }
      val callRange = if (callElements.isNotEmpty()) {
        readAction { TextRange(callElements.first().textRange.startOffset, callElements.last().textRange.endOffset) }
      } else {
        null
      }

      val methodIdentifier = readAction { method.nameIdentifier } ?: throw IllegalStateException()
      val methodIdentifierRange = readAction { createGreedyRangeMarker(editor.document, methodIdentifier.textRange) }

      withContext(Dispatchers.EDT) {
        val codePreview = createPreview(editor, method.textRange, methodIdentifier.textRange.endOffset, callRange, callIdentifierRange?.textRange?.endOffset)
        Disposer.register(disposable, codePreview)

        val templateField = if (callIdentifier != null) {
          TemplateField(callIdentifier.textRange, listOf(methodIdentifier.textRange))
        }
        else {
          TemplateField(methodIdentifier.textRange)
        }
        val templateFieldWithSettings = templateField
          .withCompletionNames(suggestedNames)
          .withCompletionHint(InplaceRefactoring.getPopupOptionsAdvertisement())
          .withValidation { variableRange -> checkReferenceIdentifier(editor, file, variableRange) }
        val templateState = ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
          .enableRestartForHandler(ExtractMethodHandler::class.java)
          .onBroken {
            editorState.revert()
          }
          .onSuccess {
            val range = callIdentifierRange?.asTextRange ?: return@onSuccess
            val methodName = editor.document.getText(range)
            val extractedMethod = findElementAt<PsiMethod>(file, methodIdentifierRange) ?: return@onSuccess
            InplaceExtractMethodCollector.executed.log(defaultExtractor.extractOptions.methodName != methodName)
            installGotItTooltips(editor, callIdentifierRange.asTextRange, methodIdentifierRange.asTextRange)
            MethodExtractor.sendRefactoringDoneEvent(extractedMethod)
            runWithModalProgressBlocking(project, ExtractMethodHandler.getRefactoringName()) {
              extractor.replaceDuplicates(editor, extractedMethod)
            }
          }
          .disposeWithTemplate(disposable)
          .createTemplate(file, listOf(templateFieldWithSettings))
        afterTemplateStart(templateState)
      }
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
    addInlaySettingsElement(templateState, popupProvider)?.also { inlay ->
      Disposer.register(templateState, inlay)
    }
  }

}

fun setupRestartOnSettingsChange(editor: Editor, popupProvider: ExtractMethodPopupProvider, defaultExtractor: DuplicatesMethodExtractor){
  val project = editor.project ?: return
  popupProvider.setChangeListener {
    val shouldAnnotate = popupProvider.annotate
    if (shouldAnnotate != null) {
      PropertiesComponent.getInstance(project).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, shouldAnnotate, true)
    }
    val makeStatic = popupProvider.makeStatic
    if (!popupProvider.staticPassFields && makeStatic != null) {
      JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = makeStatic
    }
    ExtractMethodService.getInstance(project).scope.launch {
      val templateState = TemplateManagerImpl.getTemplateState(editor) ?: return@launch
      MethodExtractor().restartInplace(templateState, defaultExtractor, popupProvider)
    }
  }
  popupProvider.setShowDialogAction { actionEvent ->
    val templateState = TemplateManagerImpl.getTemplateState(editor) ?:return@setShowDialogAction
    val extractor = createExtractor(defaultExtractor, popupProvider)
    MethodExtractor().restartInDialog(templateState, extractor, actionEvent == null)
  }
}

private fun createExtractor(defaultExtractor: DuplicatesMethodExtractor, popupProvider: ExtractMethodPopupProvider): DuplicatesMethodExtractor {
  var options = defaultExtractor.extractOptions
  if (popupProvider.makeStatic == true) {
    val analyzer = CodeFragmentAnalyzer(options.elements)
    options = ExtractMethodPipeline.withForcedStatic(analyzer, options) ?: throw IllegalStateException()
  }
  val rangeToReplace = createGreedyRangeMarker(defaultExtractor.rangeToReplace.document, defaultExtractor.rangeToReplaceOriginal)
  return DuplicatesMethodExtractor(options, defaultExtractor.targetClass, rangeToReplace)
}