// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateResultListener.TemplateResult
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.codeInsight.template.impl.Variable
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.command.impl.StartMarkAction.AlreadyStartedException
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.refactoring.InplaceRefactoringContinuation
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.*
import com.intellij.refactoring.rename.impl.PsiRenameUsageRangeUpdater
import com.intellij.refactoring.rename.impl.RenameOptions
import com.intellij.refactoring.rename.impl.buildUsageQuery
import com.intellij.refactoring.rename.impl.rename
import com.intellij.refactoring.rename.inplace.InplaceRefactoring.PRIMARY_VARIABLE_NAME
import com.intellij.util.Query
import java.util.*

/**
 * @return `false` if the template cannot be started,
 * e.g., when usage under caret isn't supported
 */
internal fun inplaceRename(project: Project, editor: Editor, target: RenameTarget): Boolean {
  val targetPointer: Pointer<out RenameTarget> = target.createPointer()

  fun performRename(newName: String) {
    // TODO obtain options from the inlay button UI, see registry `enable.rename.options.inplace`
    val options = RenameOptions(
      renameTextOccurrences = true,
      renameCommentsStringsOccurrences = true,
      searchScope = GlobalSearchScope.allScope(project)
    )
    rename(project, targetPointer, newName, options)
  }

  val document: Document = editor.document
  val file: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                      ?: return false

  val hostEditor: Editor = (editor as? EditorWindow)?.delegate ?: editor
  val hostDocument: Document = (document as? DocumentWindow)?.delegate ?: document
  val hostFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(hostDocument)
                          ?: return false

  val usageQuery: Query<out RenameUsage> = buildUsageQuery(project, target, RenameOptions(true, true, LocalSearchScope(hostFile)))
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
  if (originUsage !is ModifiableRenameUsage || originUsage.fileUpdater !== idFileRangeUpdater) {
    // TODO support starting inplace rename from a usage with a custom updater,
    //  e.g., starting rename from `foo` reference to `getFoo` method.
    //  This will require an ability to obtain the new name by a usage text,
    //  and we don't need this ability when we are 100% sure they are the same (idFileRangeUpdater).
    return false
  }

  val data = prepareTemplate(hostDocument, hostFile, psiUsages, originUsage)
             ?: return false
  val commandName: String = RefactoringBundle.message("rename.command.name.0.in.place.template", target.presentation.presentableText)
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

  WriteCommandAction
    .writeCommandAction(project)
    .withName(commandName)
    .run<Throwable> {
      try {
        val disposable = runLiveTemplate(project, hostEditor, data, ::performRename)
        Disposer.register(disposable, storeInplaceContinuation(hostEditor, InplaceRenameContinuation(targetPointer)))
        Disposer.register(disposable, finishMarkAction::run)
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

private fun prepareTemplate(
  hostDocument: Document,
  hostFile: PsiFile,
  psiUsages: Collection<PsiRenameUsage>,
  originUsage: PsiRenameUsage
): TemplateData? {
  val originUsageRange: TextRange = usageRangeInHost(hostFile, originUsage)
                                    ?: return null // can't start inplace rename from a usage broken into pieces

  val usageReplacements = HashMap<TextRange, TextReplacement>()
  for (psiUsage: PsiRenameUsage in psiUsages) {
    if (psiUsage === originUsage) {
      continue // already handled
    }
    if (psiUsage !is ModifiableRenameUsage) {
      continue // don't know how to update the usage
    }
    val fileUpdater = psiUsage.fileUpdater
    if (fileUpdater !is PsiRenameUsageRangeUpdater) {
      continue
    }
    val usageRangeInHost: TextRange = usageRangeInHost(hostFile, psiUsage)
                                      ?: continue // the usage is inside injection, and it's split into several chunks in host
    if (usageReplacements.containsKey(usageRangeInHost)) {
      continue
    }
    usageReplacements[usageRangeInHost] = fileUpdater.textReplacement
  }

  val allVariables: SortedMap<TextRange, String> = templateVariableNames(originUsageRange, usageReplacements.keys)
  val hostDocumentContent = hostDocument.text
  val template: Template = buildTemplate(hostFile.project, hostDocumentContent, allVariables)
  val originUsageText = originUsageRange.substring(hostDocumentContent)
  assignTemplateExpressionsToVariables(template, originUsageText, usageReplacements, allVariables)
  return TemplateData(template, allVariables.keys.toList())
}

private fun templateVariableNames(originUsageRange: TextRange, usageRanges: Collection<TextRange>): SortedMap<TextRange, String> {
  val allVariables = TreeMap<TextRange, String>(Segment.BY_START_OFFSET_THEN_END_OFFSET)
  allVariables[originUsageRange] = PRIMARY_VARIABLE_NAME
  var usageCounter = 0
  for (usageRange in usageRanges) {
    allVariables[usageRange] = "inplace_usage_${usageCounter++}"
  }
  return allVariables
}

private fun buildTemplate(
  project: Project,
  hostDocumentContent: String,
  allVariables: SortedMap<TextRange, String>
): Template {
  val template = TemplateManager.getInstance(project).createTemplate("", "")
  var lastOffset = 0
  for ((usageRange, variableName) in allVariables) {
    template.addTextSegment(hostDocumentContent.substring(lastOffset, usageRange.startOffset))
    template.addVariableSegment(variableName)
    lastOffset = usageRange.endOffset
  }
  template.addTextSegment(hostDocumentContent.substring(lastOffset))
  return template
}

private fun assignTemplateExpressionsToVariables(
  template: Template,
  originUsageText: String,
  usageReplacements: HashMap<TextRange, TextReplacement>,
  allVariables: SortedMap<TextRange, String>
) {
  // add primary variable first, because variables added before it won't be recomputed
  val originUsageExpression = MyLookupExpression(originUsageText, null, null, null, false, null)
  template.addVariable(Variable(PRIMARY_VARIABLE_NAME, originUsageExpression, originUsageExpression, true, false))
  for ((usageRange, textReplacement) in usageReplacements) {
    val variableName = requireNotNull(allVariables[usageRange])
    val usageExpression = InplaceRenameUsageExpression(textReplacement)
    template.addVariable(Variable(variableName, usageExpression, null, false, false))
  }
}

private fun runLiveTemplate(
  project: Project,
  hostEditor: Editor,
  data: TemplateData,
  newNameConsumer: (String) -> Unit
): Disposable {

  val template = data.template
  template.setInline(true)
  template.setToIndent(false)
  template.isToShortenLongNames = false
  template.isToReformat = false

  val restoreCaretAndSelection: Runnable = moveCaretForTemplate(hostEditor) // required by `TemplateManager#runTemplate`
  val restoreDocument = deleteInplaceTemplateSegments(project, hostEditor.document, data.templateSegmentRanges)
  val state: TemplateState = TemplateManager.getInstance(project).runTemplate(hostEditor, template)
  DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(state)
  Disposer.register(state, highlightTemplateVariables(project, hostEditor, template, state))
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
 * @return a handle to remove highlights
 */
private fun highlightTemplateVariables(
  project: Project,
  editor: Editor,
  template: Template,
  templateState: TemplateState
): Disposable {
  val highlighters = ArrayList<RangeHighlighter>()
  val highlightManager: HighlightManager = HighlightManager.getInstance(project)
  for (i in 0 until templateState.segmentsCount) {
    val range = templateState.getSegmentRange(i)
    val key = if (template.getSegmentName(i) == PRIMARY_VARIABLE_NAME) {
      EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
    }
    else {
      EditorColors.SEARCH_RESULT_ATTRIBUTES
    }
    highlightManager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, key, 0, highlighters)
  }
  for (highlighter: RangeHighlighter in highlighters) {
    highlighter.isGreedyToLeft = true
    highlighter.isGreedyToRight = true
  }
  return Disposable {
    for (highlighter: RangeHighlighter in highlighters) {
      highlightManager.removeSegmentHighlighter(editor, highlighter)
    }
  }
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

fun TemplateState.getNewName(): String {
  return requireNotNull(getVariableValue(PRIMARY_VARIABLE_NAME)).text
}

private fun storeInplaceContinuation(editor: Editor, continuation: InplaceRefactoringContinuation): Disposable {
  editor.putUserData(InplaceRefactoringContinuation.INPLACE_REFACTORING_CONTINUATION, continuation)
  editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, ImaginaryInplaceRefactoring)
  return Disposable {
    editor.putUserData(InplaceRefactoringContinuation.INPLACE_REFACTORING_CONTINUATION, null)
    editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null)
  }
}
