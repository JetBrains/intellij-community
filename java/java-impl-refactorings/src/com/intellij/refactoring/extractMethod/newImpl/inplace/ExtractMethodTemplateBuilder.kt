// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider

data class ExtractMethodTemplateBuilder(
  private val editor: Editor,
  private val commandName: @NlsContexts.Command String,
  private val completionNames: List<String> = emptyList(),
  private val completionAdvertisement: @NlsContexts.PopupAdvertisement String? = null,
  private val validator: (TextRange) -> Boolean = { true },
  private val onBroken: () -> Unit = {},
  private val onSuccess: () -> Unit = {},
  private val disposable: Disposable = Disposer.newDisposable(),
  private val restartHandler: Class<out RefactoringActionHandler>? = null
){

  /**
   * Allows specific [RefactoringActionHandler] to be invoked during the template editing
   * @see com.intellij.refactoring.rename.inplace.InplaceRefactoring.canStartAnotherRefactoring
   */
  fun enableRestartForHandler(restartHandler: Class<out RefactoringActionHandler>): ExtractMethodTemplateBuilder {
    return copy(restartHandler = restartHandler)
  }

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

  fun withCompletionAdvertisement(completionAdvertisement: @NlsContexts.PopupAdvertisement String): ExtractMethodTemplateBuilder {
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
      setupImaginaryInplaceRenamer(templateState, restartHandler)
      Disposer.register(templateState) { SuggestedRefactoringProvider.getInstance(project).reset() }
      DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(templateState)
      setTemplateValidator(templateState) { state ->
        val variableRange = state.currentVariableRange ?: return@setTemplateValidator false
        validator.invoke(variableRange)
      }
      Disposer.register(templateState, disposable)
      templateState.addTemplateStateListener(object: TemplateEditingAdapter(){
        override fun templateFinished(template: Template, brokenOff: Boolean) {
          if (brokenOff){
            onBroken.invoke()
            return
          }
          onSuccess.invoke()
        }
      })
      return@ThrowableComputable templateState
    })
  }

  /**
   * Disables default expression completion inside the template.
   * @param restartHandler handler which can be invoked during the template editing
   */
  private fun setupImaginaryInplaceRenamer(templateState: TemplateState, restartHandler: Class<out RefactoringActionHandler>?){
    val project = templateState.project
    val dummy = object: InplaceRefactoring(templateState.editor, null, project){
      override fun shouldSelectAll() = false
      override fun performRefactoring() = false
      override fun getCommandName(): String { throw NotImplementedError() }
      override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement? ): Boolean {
        return restartHandler != null && restartHandler.isInstance(handler) && isCaretInsideTemplate(templateState)
      }
    }
    templateState.editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, dummy)
    Disposer.register(templateState) { templateState.editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null) }
  }

  private fun isCaretInsideTemplate(templateState: TemplateState): Boolean {
    val editor = templateState.editor
    val templateRange = templateState.currentVariableRange?.grown(1) ?: TextRange.EMPTY_RANGE
    val editorOffsets = if (editor.selectionModel.hasSelection()) {
      listOf(editor.caretModel.offset, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd - 1)
    }
    else {
      listOf(editor.caretModel.offset)
    }
    return editorOffsets.all { it in templateRange }
  }

  private fun setTemplateValidator(templateState: TemplateState, validator: (TemplateState) -> Boolean){
    val manager = EditorActionManager.getInstance()
    val actionName = "NextTemplateVariable"
    val defaultHandler = manager.getActionHandler(actionName)
    Disposer.register(templateState) { manager.setActionHandler(actionName, defaultHandler) }
    manager.setActionHandler(actionName, object : EditorActionHandler() {
      override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (!validator.invoke(templateState)) return
        defaultHandler.execute(editor, caret, dataContext)
      }
    })
  }
}