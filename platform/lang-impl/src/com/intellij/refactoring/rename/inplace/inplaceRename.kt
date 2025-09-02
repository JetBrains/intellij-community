// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateResultListener.TemplateResult
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.codeInsight.template.impl.Variable
import com.intellij.ide.nls.NlsMessages
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.command.impl.StartMarkAction.AlreadyStartedException
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.refactoring.InplaceRefactoringContinuation
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.*
import com.intellij.refactoring.rename.api.RenameValidationResult.Companion.OK
import com.intellij.refactoring.rename.api.RenameValidationResult.Companion.RenameValidationResultData
import com.intellij.refactoring.rename.api.RenameValidationResult.Companion.RenameValidationResultProblemLevel
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext.IN_COMMENTS_AND_STRINGS
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext.IN_PLAIN_TEXT
import com.intellij.refactoring.rename.impl.*
import com.intellij.refactoring.rename.inplace.InplaceRefactoring.PRIMARY_VARIABLE_NAME
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil.createRenameSettingsInlay
import com.intellij.ui.LightweightHint
import com.intellij.util.Query
import com.intellij.util.asSafely
import javax.swing.JComponent

/**
 * @return `false` if the template cannot be started,
 * e.g., when usage under caret isn't supported
 */
internal fun inplaceRename(project: Project, editor: Editor, target: RenameTarget): Boolean {
  val targetPointer: Pointer<out RenameTarget> = target.createPointer()

  val validateName = target.validator()::validate

  fun performRename(newName: String) {
    val validation = validateName(newName)
    if ((validation as? RenameValidationResultData)?.level == RenameValidationResultProblemLevel.ERROR) {
      return
    }
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val restoredTarget = targetPointer.dereference() ?: return
    val options = renameOptions(project, restoredTarget)
    rename(project, targetPointer, newName, options)
  }

  val document: Document = editor.document
  val file: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                      ?: return false

  val hostEditor: Editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
  val hostDocument: Document = PsiDocumentManagerBase.getTopLevelDocument(document)
  val hostFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(hostDocument)
                          ?: return false

  val usageQuery: Query<out RenameUsage> = buildUsageQuery(
    project, target,
    RenameOptions(TextOptions(commentStringOccurrences = true, textOccurrences = true), LocalSearchScope(hostFile))
  )
  val usages: Collection<RenameUsage> = usageQuery.findAll()
  val psiUsages: List<PsiRenameUsage> = usages.filterIsInstance<PsiRenameUsage>()

  val segmentsLimit = Registry.intValue("inplace.rename.segments.limit", -1)
  if (segmentsLimit != -1 && psiUsages.size > segmentsLimit) {
    return false
  }

  val offset: Int = editor.caretModel.offset
  val originUsage: PsiRenameUsage? = psiUsages.find { usage: PsiRenameUsage ->
    usage.file == file &&
    usage.range.containsOffset(offset)
  }
  if (originUsage == null) {
    // e.g. when started from Java "var" keyword, the keyword itself is not a usage
    // => there are no usages containing the current offset
    // => fall back to rename with a dialog
    return false
  }
  if (originUsage !is ModifiableRenameUsage) {
    return false
  }
  if (originUsage.fileUpdater !== idFileRangeUpdater) {
    val originUsageRange: TextRange = usageRangeInHost(hostFile, originUsage)
                                      ?: return false
    val hostDocumentContent = hostDocument.text
    val originUsageText = originUsageRange.substring(hostDocumentContent)
    if (!originUsage.declaration || originUsageText != target.targetName) {
      // TODO support starting inplace rename from a usage with a custom updater,
      //  e.g., starting rename from `foo` reference to `getFoo` method.
      //  This will require an ability to obtain the new name by a usage text,
      //  and we don't need this ability when we are 100% sure they are the same (idFileRangeUpdater).
      return false
    }
  }
  var textOptions: TextOptions = getTextOptions(target)
  val data = prepareTemplate(hostDocument, hostFile, originUsage, psiUsages, textOptionsRef = { textOptions })
             ?: return false
  val commandName: String = RefactoringBundle.message("rename.command.name.0.in.place.template", target.presentation().presentableText)
  val startMarkAction: StartMarkAction = try {
    InplaceRefactoring.startMarkAction(project, hostEditor, commandName)
  }
  catch (e: AlreadyStartedException) {
    InplaceRefactoring.unableToStartWarning(project, hostEditor)
    // This happens when there is an InplaceRefactoring started, including "old" VariableInplaceRenamer.
    // At this point there is no RenameTarget inplace rename in progress, otherwise it would've been handled by InplaceRenameContinuation.
    // Returning `false` would've allowed to perform rename with a dialog, which is undesirable.
    return true
  }
  val finishMarkAction = Runnable {
    FinishMarkAction.finish(project, hostEditor, startMarkAction)
  }

  WriteCommandAction.writeCommandAction(project).withName(commandName).run<Throwable> {
    try {
      val templateState = runLiveTemplate(project, hostEditor, data, ::performRename)
      val templateHighlighting = highlightTemplateVariables(project, hostEditor, data.template, templateState, textOptions)
      templateHighlighting.updateHighlighters(textOptions)
      createRenameSettingsInlay(templateState, templateState.getOriginSegmentEndOffset(), textOptions) { newOptions ->
        textOptions = newOptions
        templateState.update()
        templateHighlighting.updateHighlighters(textOptions)
        setTextOptions(targetPointer, textOptions)
      }
      registerTemplateValidation(templateState, editor, validateName)
      Disposer.register(templateState, templateHighlighting)
      Disposer.register(templateState, storeInplaceContinuation(hostEditor, InplaceRenameContinuation(targetPointer)))
      Disposer.register(templateState, finishMarkAction::run)
    }
    catch (e: Throwable) {
      finishMarkAction.run()
      throw e
    }
  }
  return true
}

private class TemplateData(
  val template: Template,
  val templateSegmentRanges: List<TextRange>
)

private data class SegmentData(
  val range: TextRange,
  val variableName: String,
  val expression: Expression,
)

private const val usageVariablePrefix = "inplace_usage_"
internal const val commentStringUsageVariablePrefix: String = "comment_string_usage_"
internal const val plainTextUsageVariablePrefix: String = "plain_text_usage_"

private fun prepareTemplate(
  hostDocument: Document,
  hostFile: PsiFile,
  originUsage: PsiRenameUsage,
  usages: Collection<PsiRenameUsage>,
  textOptionsRef: () -> TextOptions,
): TemplateData? {
  val originUsageRange: TextRange = usageRangeInHost(hostFile, originUsage)
                                    ?: return null // can't start inplace rename from a usage broken into pieces
  val hostDocumentContent = hostDocument.text
  val originUsageText = originUsageRange.substring(hostDocumentContent)
  val originSegment = SegmentData(
    originUsageRange, PRIMARY_VARIABLE_NAME,
    MyLookupExpression(originUsageText, null, null, null, false, null)
  )

  val isCommentStringActive = { textOptionsRef().commentStringOccurrences == true }
  val isPlainTextActive = { textOptionsRef().textOccurrences == true }

  val segments = ArrayList<SegmentData>(usages.size)
  segments += originSegment
  var variableCounter = 0
  for (usage in usages) {
    if (usage === originUsage) {
      continue // already handled
    }
    if (usage !is ModifiableRenameUsage) {
      continue // don't know how to update the usage
    }
    val fileUpdater = usage.fileUpdater
    if (fileUpdater !is PsiRenameUsageRangeUpdater) {
      continue
    }
    val usageRangeInHost: TextRange = usageRangeInHost(hostFile, usage)
                                      ?: continue // the usage is inside injection, and it's split into several chunks in host
    val usageTextByName = if (fileUpdater === idFileRangeUpdater)
      fileUpdater.usageTextByName
    else {
      val usagePtr = (usage as ModifiableRenameUsage).createPointer();
      { newName: String ->
        usagePtr.dereference()?.fileUpdater?.asSafely<PsiRenameUsageRangeUpdater>()?.usageTextByName?.invoke(newName) ?: newName
      }
    }
    val segment = if (usage is TextRenameUsage) {
      val originalText = usageRangeInHost.substring(hostDocumentContent)
      when (usage.context) {
        IN_COMMENTS_AND_STRINGS -> SegmentData(
          range = usageRangeInHost,
          variableName = commentStringUsageVariablePrefix + variableCounter,
          expression = InplaceRenameTextUsageExpression(usageTextByName, originalText, isCommentStringActive)
        )
        IN_PLAIN_TEXT -> SegmentData(
          range = usageRangeInHost,
          variableName = plainTextUsageVariablePrefix + variableCounter,
          expression = InplaceRenameTextUsageExpression(usageTextByName, originalText, isPlainTextActive)
        )
      }
    }
    else {
      SegmentData(
        range = usageRangeInHost,
        variableName = usageVariablePrefix + variableCounter,
        expression = InplaceRenameUsageExpression(usageTextByName),
      )
    }
    segments += segment
    variableCounter++
  }
  segments.sortWith(Comparator.comparing(SegmentData::range, Segment.BY_START_OFFSET_THEN_END_OFFSET))

  val template: Template = buildTemplate(hostFile.project, hostDocumentContent, segments)
  assignTemplateExpressionsToVariables(template, originSegment, segments)
  return TemplateData(template, segments.map(SegmentData::range))
}

private fun buildTemplate(
  project: Project,
  hostDocumentContent: String,
  segments: List<SegmentData>,
): Template {
  val template = TemplateManager.getInstance(project).createTemplate("", "")
  var lastOffset = 0
  for ((range, variableName, _) in segments) {
    template.addTextSegment(hostDocumentContent.substring(lastOffset, range.startOffset))
    template.addVariableSegment(variableName)
    lastOffset = range.endOffset
  }
  template.addTextSegment(hostDocumentContent.substring(lastOffset))
  return template
}

private fun assignTemplateExpressionsToVariables(
  template: Template,
  originSegment: SegmentData,
  segments: List<SegmentData>,
) {
  // add primary variable first, because variables added before it won't be recomputed
  template.addVariable(Variable(originSegment.variableName, originSegment.expression, originSegment.expression, true, false))
  for ((_, variableName, expression) in segments) {
    if (variableName == originSegment.variableName) {
      continue
    }
    template.addVariable(Variable(variableName, expression, null, false, false))
  }
}

private fun runLiveTemplate(
  project: Project,
  hostEditor: Editor,
  data: TemplateData,
  newNameConsumer: (String) -> Unit,
): TemplateState {

  val template = data.template
  template.setInline(true)
  template.setToIndent(false)
  template.isToShortenLongNames = false
  template.isToReformat = false

  val restoreCaretAndSelection: Runnable = moveCaretForTemplate(hostEditor) // required by `TemplateManager#runTemplate`
  val restoreDocument = deleteInplaceTemplateSegments(project, hostEditor.document, data.templateSegmentRanges)
  val state: TemplateState = TemplateManager.getInstance(project).runTemplate(hostEditor, template)
  DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(state)
  state.addTemplateResultListener { result: TemplateResult ->
    when (result) {
      TemplateResult.Canceled -> Unit // don't restore document inside undo
      TemplateResult.BrokenOff -> {
        restoreDocument.run()
      }
      TemplateResult.Finished -> {
        val newName: String = state.getNewName()
        restoreDocument.run()
        newNameConsumer(newName)
      }
    }
  }
  restoreCaretAndSelection.run()
  return state
}

/**
 * @return a handle to restore caret position and selection after template initialization
 */
private fun moveCaretForTemplate(editor: Editor): Runnable {
  val caretModel: CaretModel = editor.caretModel
  val offset: Int = caretModel.offset
  val selectedRange: TextRange? = editor.selectionModel.let { selectionModel ->
    if (selectionModel.hasSelection()) {
      TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
    }
    else {
      null
    }
  }

  caretModel.moveToOffset(0)

  return Runnable {
    InplaceRefactoring.restoreOldCaretPositionAndSelection(editor, offset) {
      if (selectedRange != null) {
        VariableInplaceRenamer.restoreSelection(editor, selectedRange)
      }
    }
  }
}

private fun setTextOptions(targetPointer: Pointer<out RenameTarget>, newOptions: TextOptions) {
  targetPointer.dereference()?.let { restoredTarget ->
    setTextOptions(restoredTarget, newOptions)
  }
}

internal fun TemplateState.getNewName(): String {
  return requireNotNull(getVariableValue(PRIMARY_VARIABLE_NAME)).text
}

private fun TemplateState.getOriginSegmentEndOffset(): Int {
  return requireNotNull(getVariableRange(PRIMARY_VARIABLE_NAME)).endOffset
}

private fun storeInplaceContinuation(editor: Editor, continuation: InplaceRefactoringContinuation): Disposable {
  editor.putUserData(InplaceRefactoringContinuation.INPLACE_REFACTORING_CONTINUATION, continuation)
  editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, ImaginaryInplaceRefactoring)
  return Disposable {
    editor.putUserData(InplaceRefactoringContinuation.INPLACE_REFACTORING_CONTINUATION, null)
    editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null)
  }
}

// TODO consider reusing in com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
private fun registerTemplateValidation(templateState: TemplateState,
                                       editor: Editor,
                                       nameValidator: (String) -> RenameValidationResult) {

  var showWarningConfirmation = false

  fun validateAndShowHint() {
    val newName = templateState.getNewName()
    if (newName.isEmpty()) return
    val validationResult = nameValidator(newName)
    if (validationResult is RenameValidationResultData) {
      ApplicationManager.getApplication().invokeLater {
        showEditorHint(
          editor,
          if (validationResult.level == RenameValidationResultProblemLevel.WARNING)
            HintUtil.createWarningLabel(validationResult.message(newName) + createWarningConfirmationMessage(showWarningConfirmation))
          else
            HintUtil.createErrorLabel(validationResult.message(newName))
        )
      }
    }
  }

  val preventAction = PreventInvalidTemplateFinishAction(
    {
      when (val result = nameValidator(templateState.getNewName())) {
        is OK -> null
        is RenameValidationResultData -> when (result.level) {
          RenameValidationResultProblemLevel.WARNING -> {
            if (!showWarningConfirmation) true
            else null
          }
          RenameValidationResultProblemLevel.ERROR -> {
            false
          }
        }
      }
    },
    {
      showWarningConfirmation = it
      validateAndShowHint()
    }
  )
  preventAction.registerCustomShortcutSet(ActionUtil.getShortcutSet("NextTemplateVariable"),
                                          editor.component, templateState)

  editor.document.addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      showWarningConfirmation = false
      validateAndShowHint()
    }
  }, templateState)

  editor.caretModel.addCaretListener(object : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      validateAndShowHint()
    }
  }, templateState)

  validateAndShowHint()
}

private fun showEditorHint(editor: Editor, label: JComponent) {
  val hint = LightweightHint(label)
  val flags = HintManager.HIDE_BY_ESCAPE or HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE
  HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.ABOVE, flags, 0, false)
}

private fun createWarningConfirmationMessage(showWarningConfirmation: Boolean): @NlsSafe String {
  return if (showWarningConfirmation)
    HtmlBuilder().append(HtmlChunk.p().addText(
      RefactoringBundle.message(
        "inplace.refactoring.press.again.to.complete",
        NlsMessages.formatOrList(
          ActionUtil.getShortcutSet("NextTemplateVariable").shortcuts.map { KeymapUtil.getShortcutText(it) }
        ))
    )).toString()
  else ""
}

private class PreventInvalidTemplateFinishAction(
  private val update: () -> Boolean?,
  private val action: (Boolean) -> Unit,
) : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = this.update() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    action(this.update() ?: false)
  }
}
