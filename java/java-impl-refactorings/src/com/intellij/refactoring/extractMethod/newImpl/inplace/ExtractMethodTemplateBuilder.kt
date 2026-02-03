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
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider

data class TemplateField(val fieldRange: TextRange,
                         val updateRanges: List<TextRange> = emptyList(),
                         val completionHint: @NlsContexts.PopupAdvertisement String? = null,
                         val completionNames: List<String> = emptyList(),
                         val validator: (TextRange) -> Boolean = { true }
) {
  fun withCompletionNames(completionNames: List<String>): TemplateField {
    return copy(completionNames = completionNames)
  }

  fun withCompletionHint(completionAdvertisement: @NlsContexts.PopupAdvertisement String): TemplateField {
    return copy(completionHint = completionAdvertisement)
  }

  fun withValidation(validator: (TextRange) -> Boolean): TemplateField {
    return copy(validator = validator)
  }
}

data class ExtractMethodTemplateBuilder(
  private val editor: Editor,
  private val commandName: @NlsContexts.Command String,
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

  fun createTemplate(file: PsiFile, templateFields: List<TemplateField>): TemplateState {
    val project = file.project
    val document = editor.document
    return WriteCommandAction.writeCommandAction(project).withName(commandName).withGroupId(commandName).compute(ThrowableComputable {
      val builder = TemplateBuilderImpl(file)
      templateFields.forEachIndexed { i, templateField ->
        val defaultText = document.getText(templateField.fieldRange)
        val completionNames = templateField.completionNames
        val expression = ConstantNode(defaultText).withLookupStrings(completionNames).withPopupAdvertisement(templateField.completionHint)
        builder.replaceRange(templateField.fieldRange, "Primary_$i", expression, true)
        templateField.updateRanges.forEachIndexed { j, range ->
          builder.replaceElement(range, "Secondary_${i}_${j}", " Primary_$i", false)
        }
      }
      builder.setVariableOrdering { first, second -> StringUtil.compare(first.name, second.name, false) }
      val template = builder.buildInlineTemplate()
      template.isToShortenLongNames = false
      template.isToReformat = false
      template.setToIndent(false)
      editor.caretModel.moveToOffset(file.textRange.startOffset)
      val templateState = TemplateManager.getInstance(project).runTemplate(editor, template)
      if (templateState.isFinished) {
        Disposer.dispose(disposable)
      }
      else {
        setupImaginaryInplaceRenamer(templateState, restartHandler)
        Disposer.register(templateState) { SuggestedRefactoringProvider.getInstance(project).reset() }
        DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(templateState)
        setTemplateValidator(templateState) { range -> templateFields[templateState.currentVariableNumber].validator.invoke(range) }
        Disposer.register(templateState, disposable)
        templateState.addTemplateStateListener(object: TemplateEditingAdapter(){
          override fun templateCancelled(template: Template?) {
            if (UndoManager.getInstance(project).isUndoOrRedoInProgress) return
            onBroken.invoke()
          }

          override fun templateFinished(template: Template, brokenOff: Boolean) {
            if (brokenOff){
              onBroken.invoke()
              return
            }
            onSuccess.invoke()
          }
        })
      }
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

  private fun setTemplateValidator(templateState: TemplateState, validator: (TextRange) -> Boolean) {
    val actionName = IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE
    val manager = EditorActionManager.getInstance()
    val defaultHandler = manager.getActionHandler(actionName)
    Disposer.register(templateState) { manager.setActionHandler(actionName, defaultHandler) }
    manager.setActionHandler(actionName, object : EditorActionHandler() {
      override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val textRange = templateState.currentVariableRange ?: return
        if (!validator.invoke(textRange)) return
        defaultHandler.execute(editor, caret, dataContext)
      }
    })
  }
}