// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.codeInsight.template.TemplateResultListener.TemplateResult
import com.intellij.codeInsight.template.impl.TemplateState
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
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.refactoring.InplaceRefactoringContinuation
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.*
import com.intellij.refactoring.rename.impl.RenameOptions
import com.intellij.refactoring.rename.impl.buildUsageQuery
import com.intellij.refactoring.rename.impl.rename
import com.intellij.refactoring.rename.inplace.InplaceRefactoring.OTHER_VARIABLE_NAME
import com.intellij.refactoring.rename.inplace.InplaceRefactoring.PRIMARY_VARIABLE_NAME
import com.intellij.util.DocumentUtil
import com.intellij.util.Query

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
  if (originUsage !is ModifiableRenameUsage || originUsage.fileUpdater !== DefaultPsiRenameUsageUpdater) {
    // the usage text will be the same as the new name
    // => usage text can be used as the new name
    //    for usages specifying DefaultPsiRenameUsageUpdater
    return false
  }

  val (builder: TemplateBuilderImpl, stateBefore: Map<RangeMarker, String>) = buildTemplate(hostDocument, hostFile, psiUsages, originUsage)
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

  WriteCommandAction
    .writeCommandAction(project)
    .withName(commandName)
    .run<Throwable> {
      val disposable = runLiveTemplate(project, hostEditor, startMarkAction, builder, stateBefore, ::performRename)
      Disposer.register(disposable, storeInplaceContinuation(hostEditor, InplaceRenameContinuation(targetPointer)))
    }
  return true
}

private data class TemplateAndStateBefore(
  val templateBuilder: TemplateBuilderImpl,
  val stateBefore: Map<RangeMarker, String>
)

private fun buildTemplate(
  hostDocument: Document,
  hostFile: PsiFile,
  psiUsages: Collection<PsiRenameUsage>,
  originUsage: PsiRenameUsage
): TemplateAndStateBefore? {
  val originUsageRange: TextRange = usageRangeInHost(hostFile, originUsage)
                                    ?: return null // can't start inplace rename from a usage broken into pieces

  val builder = TemplateBuilderImpl(hostFile)
  val stateBefore = HashMap<RangeMarker, String>()

  val hostDocumentContent = hostDocument.text
  val originUsageText = originUsageRange.substring(hostDocumentContent)
  val originExpression = MyLookupExpression(originUsageText, null, null, null, false, null)
  builder.replaceElement(hostFile, originUsageRange, PRIMARY_VARIABLE_NAME, originExpression, true)
  stateBefore[hostDocument.createRangeMarker(originUsageRange)] = originUsageText

  for (psiUsage: PsiRenameUsage in psiUsages) {
    if (psiUsage === originUsage) {
      continue // already handled
    }
    if (psiUsage !is ModifiableRenameUsage) {
      continue // don't know how to update the usage
    }
    if (psiUsage.fileUpdater !== DefaultPsiRenameUsageUpdater) {
      continue // don't know how to update the usage yet, different updaters might be supported later
    }
    val usageRangeInHost: TextRange = usageRangeInHost(hostFile, psiUsage)
                                      ?: continue // the usage is inside injection, and it's split into several chunks in host
    builder.replaceElement(hostFile, usageRangeInHost, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false)
    stateBefore[hostDocument.createRangeMarker(usageRangeInHost)] = usageRangeInHost.substring(hostDocumentContent)
  }

  val segmentsLimit = Registry.intValue("inplace.rename.segments.limit", -1)
  if (segmentsLimit != -1 && builder.elementsCount > segmentsLimit) {
    return null
  }

  for ((marker: RangeMarker, _) in stateBefore) {
    marker.isGreedyToRight = true
  }

  return TemplateAndStateBefore(builder, stateBefore)
}

private fun usageRangeInHost(hostFile: PsiFile, usage: PsiRenameUsage): TextRange? {
  return if (usage.file == hostFile) {
    usage.range
  }
  else {
    injectedToHost(hostFile.project, usage.file, usage.range)
  }
}

private fun injectedToHost(project: Project, injectedFile: PsiFile, injectedRange: TextRange): TextRange? {
  val injectedDocument: DocumentWindow = PsiDocumentManager.getInstance(project).getDocument(injectedFile) as? DocumentWindow
                                         ?: return null
  val startOffsetHostRange: TextRange = injectedDocument.getHostRange(injectedDocument.injectedToHost(injectedRange.startOffset))
                                        ?: return null
  val endOffsetHostRange: TextRange = injectedDocument.getHostRange(injectedDocument.injectedToHost(injectedRange.endOffset))
                                      ?: return null
  return if (startOffsetHostRange == endOffsetHostRange) {
    injectedDocument.injectedToHost(injectedRange)
  }
  else {
    null
  }
}

private fun runLiveTemplate(
  project: Project,
  hostEditor: Editor,
  startMarkAction: StartMarkAction,
  builder: TemplateBuilderImpl,
  stateBefore: Map<RangeMarker, String>,
  newNameConsumer: (String) -> Unit
): Disposable {

  fun revertTemplateAndFinish() {
    val hostDocument: Document = hostEditor.document
    try {
      WriteCommandAction.writeCommandAction(project).run<Throwable> {
        DocumentUtil.executeInBulk(hostDocument, true) {
          for ((marker: RangeMarker, content: String) in stateBefore) {
            hostDocument.replaceString(marker.startOffset, marker.endOffset, content)
          }
        }
        PsiDocumentManager.getInstance(project).commitDocument(hostDocument)
      }
    }
    finally {
      FinishMarkAction.finish(project, hostEditor, startMarkAction)
    }
  }

  val restoreCaretAndSelection: Runnable = moveCaretForTemplate(hostEditor) // required by `TemplateManager#runTemplate`
  try {
    val template: Template = builder.buildInlineTemplate().also {
      it.isToShortenLongNames = false
      it.isToReformat = false
    }
    val state: TemplateState = TemplateManager.getInstance(project).runTemplate(hostEditor, template)
    DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(state)
    Disposer.register(state, highlightTemplateVariables(project, hostEditor, template, state))
    state.addTemplateResultListener { result: TemplateResult ->
      when (result) {
        TemplateResult.Canceled -> {
          // don't restore document inside undo
          FinishMarkAction.finish(project, hostEditor, startMarkAction)
        }
        TemplateResult.BrokenOff -> {
          revertTemplateAndFinish()
        }
        TemplateResult.Finished -> {
          val newName: String = state.getNewName()
          revertTemplateAndFinish()
          newNameConsumer(newName)
        }
      }
    }
    restoreCaretAndSelection.run()
    return state
  }
  catch (e: Throwable) {
    FinishMarkAction.finish(project, hostEditor, startMarkAction)
    throw e
  }
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
  val highlights: Map<TextRange, TextAttributesKey> = InplaceRefactoring.variableHighlights(template, templateState)
  val highlighters = ArrayList<RangeHighlighter>()
  val highlightManager: HighlightManager = HighlightManager.getInstance(project)
  for ((range: TextRange, attributesKey: TextAttributesKey) in highlights) {
    highlightManager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, attributesKey, 0, highlighters)
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

private fun TemplateState.addTemplateResultListener(resultConsumer: (TemplateResult) -> Unit) {
  return addTemplateStateListener(TemplateResultListener(resultConsumer))
}

private fun storeInplaceContinuation(editor: Editor, continuation: InplaceRefactoringContinuation): Disposable {
  editor.putUserData(InplaceRefactoringContinuation.INPLACE_REFACTORING_CONTINUATION, continuation)
  editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, ImaginaryInplaceRefactoring)
  return Disposable {
    editor.putUserData(InplaceRefactoringContinuation.INPLACE_REFACTORING_CONTINUATION, null)
    editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null)
  }
}
