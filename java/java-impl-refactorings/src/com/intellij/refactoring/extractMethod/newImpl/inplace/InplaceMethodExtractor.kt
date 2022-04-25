// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Command
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addInlaySettingsElement
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeBasedDisposable
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createChangeSignatureGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createGreedyRangeMarker
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createNavigationGotIt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createPreview
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.findElementAt
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.showInEditor
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider
import com.intellij.refactoring.suggested.range
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
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

private fun disableCompletionInTemplate(project: Project, templateState: TemplateState){
  val dummy = object: InplaceRefactoring(templateState.editor, null, project){
    override fun shouldSelectAll() = false
    override fun performRefactoring() = false
    override fun getCommandName(): String { throw NotImplementedError() }
  }
  templateState.editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, dummy)
  Disposer.register(templateState) { templateState.editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null) }
}

data class ExtractMethodTemplateBuilder(
  private val editor: Editor,
  private val commandName: @Command String,
  private val completionNames: List<String> = emptyList(),
  private val completionAdvertisement: @PopupAdvertisement String? = null,
  private val validator: (TextRange) -> Boolean = { true },
  private val onBroken: () -> Unit = {},
  private val onSuccess: () -> Unit = {},
  private val disposable: Disposable = Disposable { }
){

  fun disposeWithTemplate(disposable: Disposable): ExtractMethodTemplateBuilder {
    return copy(disposable = disposable)
  }

  fun onBroken(onBroken: () -> Unit): ExtractMethodTemplateBuilder {
    return copy(onBroken = onBroken)
  }

  fun onSuccess(onSuccess: () -> Unit): ExtractMethodTemplateBuilder {
    return copy(onSuccess = onSuccess)
  }

  fun withCompletionNames(completionNames: List<String>): ExtractMethodTemplateBuilder {
    return copy(completionNames = completionNames)
  }

  fun withCompletionAdvertisement(completionAdvertisement: @PopupAdvertisement String): ExtractMethodTemplateBuilder {
    return copy(completionAdvertisement = completionAdvertisement)
  }

  fun withValidation(validator: (TextRange) -> Boolean): ExtractMethodTemplateBuilder {
    return copy(validator = validator)
  }

  fun createTemplate(file: PsiFile, methodIdentifier: TextRange, callIdentifier: TextRange): TemplateState {
    val project = file.project
    val document = editor.document
    val defaultText = document.getText(callIdentifier)
    return WriteCommandAction.writeCommandAction(project).withName(commandName).withGroupId(commandName).compute(ThrowableComputable {
      val builder = TemplateBuilderImpl(file)
      val expression = ConstantNode(defaultText).withLookupStrings(completionNames).withPopupAdvertisement(completionAdvertisement)
      builder.replaceRange(callIdentifier, "PrimaryVariable", expression, true)
      builder.replaceElement(methodIdentifier, "SecondaryVariable", "PrimaryVariable", false)
      val template = builder.buildInlineTemplate()
      template.isToShortenLongNames = false
      template.isToReformat = false
      template.setToIndent(false)
      editor.caretModel.moveToOffset(file.textRange.startOffset)
      val templateState = TemplateManager.getInstance(project).runTemplate(editor, template)
      disableCompletionInTemplate(project, templateState)
      Disposer.register(templateState) { SuggestedRefactoringProvider.getInstance(project).reset() }
      DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(templateState)

      val methodMarker = document.createRangeMarker(methodIdentifier).apply { isGreedyToRight = true }
      val callMarker = document.createRangeMarker(callIdentifier).apply { isGreedyToRight = true }
      fun setMethodName(text: String){
        runWriteAction {
          callMarker.range?.also { range ->
            editor.document.replaceString(range.startOffset, range.endOffset, text)
          }
          methodMarker.range?.also { range ->
            editor.document.replaceString(range.startOffset, range.endOffset, text)
          }
          PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        }
      }

      var shouldDispose = true
      Disposer.register(templateState) {
        if (shouldDispose) Disposer.dispose(disposable)
      }
      templateState.addTemplateStateListener(object: TemplateEditingAdapter(){
        override fun templateFinished(template: Template, brokenOff: Boolean) {
          val methodRange = methodMarker.range
          val callRange = callMarker.range
          if (brokenOff || methodRange == null || callRange == null){
            onBroken.invoke()
            return
          }
          val isValid = try {
            validator.invoke(callRange)
          } catch (e: RefactoringErrorHintException){
            false
          }
          if (!isValid) {
            shouldDispose = false
            val modifiedText = document.getText(callRange)
            setMethodName(defaultText)
            createTemplate(file, methodIdentifier, callIdentifier)
            setMethodName(modifiedText)
            return
          }
          onSuccess.invoke()
        }
      })
      return@ThrowableComputable templateState
    })
  }
}

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

  private val extractor: DuplicatesMethodExtractor = DuplicatesMethodExtractor()

  private val editorState = EditorState(editor)

  private val file: PsiFile = targetClass.containingFile

  private var methodIdentifierRange: RangeMarker? = null

  private var callIdentifierRange: RangeMarker? = null

  private val disposable = Disposer.newDisposable()

  private val project = file.project

  fun extractAndRunTemplate(suggestedNames: LinkedHashSet<String>) {
    try {
      val elements = ExtractSelector().suggestElementsToExtract(file, range)
      MethodExtractor.sendRefactoringStartedEvent(elements.toTypedArray())
      val (callElements, method) = extractor.extract(targetClass, elements, initialMethodName, popupProvider.makeStatic ?: false)
      val callExpression = PsiTreeUtil.findChildOfType(callElements.first(), PsiMethodCallExpression::class.java, false)
                           ?: throw IllegalStateException()
      val methodIdentifier = method.nameIdentifier ?: throw IllegalStateException()
      val callIdentifier = callExpression.methodExpression.referenceNameElement ?: throw IllegalStateException()

      methodIdentifierRange = createGreedyRangeMarker(editor.document, methodIdentifier.textRange)
      callIdentifierRange = createGreedyRangeMarker(editor.document, callIdentifier.textRange)

      val callRange = TextRange(callElements.first().textRange.startOffset, callElements.last().textRange.endOffset)
      val codePreview = createPreview(editor, method.textRange, methodIdentifier.textRange.endOffset, callRange,
                                      callIdentifier.textRange.endOffset)
      Disposer.register(disposable, codePreview)

      val templateState = ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
        .withCompletionNames(suggestedNames.toList())
        .withCompletionAdvertisement(InplaceRefactoring.getPopupOptionsAdvertisement())
        .onBroken { editorState.revert() }
        .onSuccess {
          val range = callIdentifierRange?.range ?: return@onSuccess
          val methodName = editor.document.getText(range)
          val extractedMethod = findElementAt<PsiMethod>(file, methodIdentifierRange) ?: return@onSuccess
          InplaceExtractMethodCollector.executed.log(initialMethodName != methodName)
          installGotItTooltips(editor, callIdentifierRange?.range, methodIdentifierRange?.range)
          MethodExtractor.sendRefactoringDoneEvent(extractedMethod)
          extractor.postprocess(editor, extractedMethod)
        }
        .disposeWithTemplate(disposable)
        .withValidation { variableRange ->
          val errorMessage = getIdentifierError(file, variableRange)
          if (errorMessage != null) {
            CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, ExtractMethodHandler.getRefactoringName(), null)
          }
          errorMessage == null
        }
        .createTemplate(file, methodIdentifier.textRange, callIdentifier.textRange)
      afterTemplateStart(templateState)
    } catch (e: Throwable) {
      Disposer.dispose(disposable)
      throw e
    }
  }

  private fun revertState() {
    editorState.revert()
  }

  private fun afterTemplateStart(templateState: TemplateState) {
    setActiveExtractor(editor, this)
    popupProvider.setChangeListener {
      val shouldAnnotate = popupProvider.annotate
      if (shouldAnnotate != null) {
        PropertiesComponent.getInstance(project).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, shouldAnnotate, true)
      }
      restartInplace()
    }
    popupProvider.setShowDialogAction { actionEvent -> restartInDialog(actionEvent == null) }
    addInlaySettingsElement(templateState, popupProvider)?.also { inlay ->
      Disposer.register(disposable, inlay)
    }
  }

  private fun getIdentifierError(file: PsiFile, variableRange: TextRange): @Nls String? {
    val methodName = file.viewProvider.document.getText(variableRange)
    val call = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, PsiMethodCallExpression::class.java, false)
    return if (! PsiNameHelper.getInstance(project).isIdentifier(methodName)) {
      JavaRefactoringBundle.message("extract.method.error.invalid.name")
    } else if (call?.resolveMethod() == null) {
      JavaRefactoringBundle.message("extract.method.error.method.conflict")
    } else {
      null
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
    InplaceExtractMethodCollector.openExtractDialog.log(project, isLinkUsed)
    revertState()
    val elements = ExtractSelector().suggestElementsToExtract(targetClass.containingFile, range)
    val methodRange = callIdentifierRange?.range
    val methodName = if (methodRange != null) editor.document.getText(methodRange) else ""
    extractor.extractInDialog(targetClass, elements, methodName, popupProvider.makeStatic ?: false)
  }

  private fun restartInplace() {
    val identifierRange = callIdentifierRange?.range
    val methodName = if (identifierRange != null) editor.document.getText(identifierRange) else null
    revertState()
    WriteCommandAction.writeCommandAction(project).withName(ExtractMethodHandler.getRefactoringName()).run<Throwable> {
      val inplaceExtractor = InplaceMethodExtractor(editor, range, targetClass, popupProvider, initialMethodName)
      inplaceExtractor.extractAndRunTemplate(linkedSetOf())
      if (methodName != null) {
        inplaceExtractor.setMethodName(methodName)
      }
    }
  }

}