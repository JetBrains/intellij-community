// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateManagerUtilBase
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindResult
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity.Companion.POST_STARTUP_ACTIVITY
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.TestOnly

private val SELECTION_HIGHLIGHTS = Key<Collection<RangeHighlighter>>("SELECTION_HIGHLIGHTS")
private val HIGHLIGHTED_TEXT = Key<String>("HIGHLIGHTED_TEXT")

private class HighlightIdentifiersKey

private class HighlightSelectionKey

/**
 * Listens for editor events and starts brace/identifier highlighting in the background
 */
internal class BackgroundHighlighter {
  private val alarm = Alarm()

  fun runActivity(project: Project) {
    val parentDisposable = ExtensionPointUtil.createExtensionDisposable(this, POST_STARTUP_ACTIVITY)
    Disposer.register(project, parentDisposable)

    registerListeners(project, parentDisposable, alarm)
  }

  companion object {
    fun registerListeners(project: Project, parentDisposable: Disposable, alarm: Alarm) {
      val eventMulticaster = EditorFactory.getInstance().eventMulticaster

      eventMulticaster.addCaretListener(object : CaretListener {
        override fun caretPositionChanged(e: CaretEvent) {
          if (e.caret !== e.editor.caretModel.primaryCaret) return
          onCaretUpdate(e.editor, project, alarm)
        }

        override fun caretAdded(e: CaretEvent) {
          if (e.caret !== e.editor.caretModel.primaryCaret) return
          onCaretUpdate(e.editor, project, alarm)
        }

        override fun caretRemoved(e: CaretEvent) {
          onCaretUpdate(e.editor, project, alarm)
        }
      }, parentDisposable)

      val selectionListener: SelectionListener = object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
          alarm.cancelAllRequests()
          val editor = e.editor
          if (editor.project !== project) {
            return
          }

          if (!highlightSelection(project, editor)) {
            removeSelectionHighlights(editor)
          }

          val oldRange = e.oldRange
          val newRange = e.newRange
          if (oldRange != null && newRange != null && oldRange.isEmpty == newRange.isEmpty) {
            // Don't update braces in case of active/absent selection.
            return
          }
          updateHighlighted(project, editor, alarm)
        }
      }
      eventMulticaster.addSelectionListener(selectionListener, parentDisposable)

      val documentListener: DocumentListener = object : DocumentListener {
        override fun documentChanged(e: DocumentEvent) {
          alarm.cancelAllRequests()
          EditorFactory.getInstance().editors(e.document, project).forEach { editor: Editor -> updateHighlighted(project, editor, alarm) }
        }
      }
      eventMulticaster.addDocumentListener(documentListener, parentDisposable)

      val connection = project.messageBus.connect(parentDisposable)
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun selectionChanged(e: FileEditorManagerEvent) {
          alarm.cancelAllRequests()
          val oldEditor = e.oldEditor
          if (oldEditor is TextEditor) {
            clearBraces(project, oldEditor.editor, alarm)
          }
          val newEditor = e.newEditor
          if (newEditor is TextEditor) {
            val editor = newEditor.editor
            updateHighlighted(project, editor, alarm)
            if (!highlightSelection(project, editor)) {
              removeSelectionHighlights(editor)
            }
          }
        }
      })

      connection.subscribe<TemplateManagerListener>(TemplateManager.TEMPLATE_STARTED_TOPIC,
                                                    TemplateManagerListener { state: TemplateState ->
                                                      if (state.isFinished) return@TemplateManagerListener
                                                      updateHighlighted(project, state.editor, alarm)
                                                      state.addTemplateStateListener(object : TemplateEditingAdapter() {
                                                        override fun templateFinished(template: Template, brokenOff: Boolean) {
                                                          updateHighlighted(project, state.editor, alarm)
                                                        }
                                                      })
                                                    })
    }

    fun getAlarm(): Alarm {
      return POST_STARTUP_ACTIVITY.findExtensionOrFail(BackgroundHighlighterProjectActivity::class.java).impl.alarm
    }

    @TestOnly
    fun enableListenersInTest(project: Project, disposable: Disposable) {
      registerListeners(project, disposable, getAlarm())
    }
  }
}

private fun onCaretUpdate(editor: Editor, project: Project, alarm: Alarm) {
  alarm.cancelAllRequests()
  val selectionModel = editor.selectionModel
  // Don't update braces in case of the active selection.
  if (editor.project !== project || selectionModel.hasSelection()) {
    return
  }
  updateHighlighted(project, editor, alarm)
}

private fun highlightSelection(project: Project, editor: Editor): Boolean {
  ThreadingAssertions.assertEventDispatchThread()
  val document = editor.document
  val stamp = document.modificationStamp
  if (document.isInBulkUpdate || !BackgroundHighlightingUtil.isValidEditor(editor)) {
    return false
  }
  if (!editor.settings.isHighlightSelectionOccurrences) {
    return false
  }
  if (TemplateManagerUtilBase.getTemplateState(editor) != null) {
    return false // don't highlight selected text when template is active
  }
  val caretModel = editor.caretModel
  if (caretModel.caretCount > 1) {
    return false
  }
  val caret = caretModel.primaryCaret
  if (!caret.hasSelection()) {
    return false
  }
  val start = caret.selectionStart
  val end = caret.selectionEnd
  val sequence = document.charsSequence
  val toFind = sequence.subSequence(start, end).toString()
  if (toFind.trim { it <= ' ' }.isEmpty() || toFind.contains("\n")) {
    return false
  }
  val previous = editor.getUserData(HIGHLIGHTED_TEXT)
  if (toFind == previous) {
    return true
  }
  editor.putUserData(HIGHLIGHTED_TEXT, toFind)
  val findManager = FindManager.getInstance(project)
  val findModel = FindModel()
  findModel.copyFrom(findManager.findInFileModel)
  findModel.isRegularExpressions = false
  findModel.stringToFind = toFind
  val threshold = intValue("editor.highlight.selected.text.max.occurrences.threshold", 50)
  ReadAction.nonBlocking<List<FindResult>> {
    var result = findManager.findString(sequence, 0, findModel, null)
    val results: MutableList<FindResult> = ArrayList()
    var count = 0
    while (result.isStringFound && count < LivePreviewController.MATCHES_LIMIT) {
      count++
      if (count > threshold) {
        return@nonBlocking emptyList<FindResult>()
      }
      results.add(result)
      result = findManager.findString(sequence, result.endOffset, findModel)
    }
    results
  }
    .coalesceBy(HighlightSelectionKey::class.java, editor)
    .expireWhen { document.modificationStamp != stamp || editor.isDisposed }
    .finishOnUiThread(ModalityState.nonModal()) { results: List<FindResult> ->
      if (document.modificationStamp != stamp || results.isEmpty()) {
        return@finishOnUiThread
      }
      removeSelectionHighlights(editor)
      val highlighters: MutableList<RangeHighlighter> = ArrayList()
      val markupModel = editor.markupModel
      for (result in results) {
        val startOffset = result.startOffset
        val endOffset = result.endOffset
        if (startOffset == start && endOffset == end) continue
        highlighters.add(markupModel.addRangeHighlighter(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, startOffset, endOffset,
                                                         HighlightManagerImpl.OCCURRENCE_LAYER,
                                                         HighlighterTargetArea.EXACT_RANGE))
      }
      editor.putUserData(SELECTION_HIGHLIGHTS,
                         highlighters)
    }
    .submit(AppExecutorUtil.getAppExecutorService())
  return true
}

private fun clearBraces(project: Project, editor: Editor, alarm: Alarm) {
  BackgroundHighlightingUtil.lookForInjectedFileInOtherThread<Any?>(project, editor,
                                                                    { `__`: PsiFile?, `___`: Editor? -> null },
                                                                    { foundFile: PsiFile?, newEditor: Editor?, `__`: Any? ->
                                                                      val handler = BraceHighlightingHandler(project,
                                                                                                             newEditor!!, alarm,
                                                                                                             foundFile!!)
                                                                      handler.clearBraceHighlighters()
                                                                    })
}

private fun removeSelectionHighlights(editor: Editor) {
  val markupModel = editor.markupModel
  val oldHighlighters = editor.getUserData(SELECTION_HIGHLIGHTS)
  if (oldHighlighters != null) {
    editor.putUserData(SELECTION_HIGHLIGHTS, null)
    for (highlighter in oldHighlighters) {
      markupModel.removeHighlighter(highlighter)
    }
  }
  editor.putUserData(HIGHLIGHTED_TEXT, null)
}

private fun updateHighlighted(project: Project, editor: Editor, alarm: Alarm) {
  ThreadingAssertions.assertEventDispatchThread()
  if (editor.document.isInBulkUpdate) {
    return
  }
  if (!BackgroundHighlightingUtil.isValidEditor(editor)) {
    return
  }

  BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(
    project, editor,
    { newFile: PsiFile, newEditor: Editor ->
      val offsetBefore = editor.caretModel.offset
      submitIdentifierHighlighterPass(editor, offsetBefore, newFile, newEditor)
      HeavyBraceHighlighter.match(newFile, offsetBefore)
    },
    { newFile: PsiFile?, newEditor: Editor?, maybeMatch: Pair<TextRange, TextRange>? ->
      val handler = BraceHighlightingHandler(project,
                                             newEditor!!, alarm,
                                             newFile!!)
      if (maybeMatch == null) {
        handler.updateBraces()
      }
      else {
        val codeInsightSettings = CodeInsightSettings.getInstance()

        if (BackgroundHighlightingUtil.needMatching(newEditor, codeInsightSettings)) {
          val fileType = PsiUtilBase.getPsiFileAtOffset(newFile, maybeMatch.first.startOffset).fileType
          handler.clearBraceHighlighters()
          handler.highlightBraces(maybeMatch.first, maybeMatch.second, true, false, fileType)
        }
      }
    })
}

private fun submitIdentifierHighlighterPass(
  hostEditor: Editor,
  offsetBefore: Int,
  newFile: PsiFile,
  newEditor: Editor,
) {
  ReadAction.nonBlocking<IdentifierHighlighterPass?> {
    if (!newFile.isValid) {
      return@nonBlocking null
    }
    val textLength = newFile.textLength
    if ((textLength == -1) or hostEditor.isDisposed) {
      // sometimes some crazy stuff is returned (EA-248725)
      return@nonBlocking null
    }

    val visibleRange = ProperTextRange.from(0, textLength)
    val pass = IdentifierHighlighterPassFactory().createHighlightingPass(newFile, newEditor, visibleRange)
    val indicator = DaemonProgressIndicator()
    ProgressIndicatorUtils.runWithWriteActionPriority({
                                                        val hostPsiFile = PsiDocumentManager.getInstance(
                                                          newFile.project).getPsiFile(
                                                          hostEditor.document)
                                                        if (hostPsiFile == null) return@runWithWriteActionPriority
                                                        HighlightingSessionImpl.runInsideHighlightingSession(
                                                          hostPsiFile, hostEditor.colorsScheme,
                                                          ProperTextRange.create(
                                                            hostPsiFile.textRange), false
                                                        ) { session: HighlightingSession? ->
                                                          pass?.doCollectInformation(session!!)
                                                        }
                                                      }, indicator)
    pass
  }
    .expireWhen {
      !BackgroundHighlightingUtil.isValidEditor(hostEditor) ||
      hostEditor.caretModel.offset != offsetBefore
    }
    .coalesceBy(HighlightIdentifiersKey::class.java, hostEditor)
    .finishOnUiThread(ModalityState.stateForComponent(hostEditor.component)
    ) { it?.doAdditionalCodeBlockHighlighting() }
    .submit(AppExecutorUtil.getAppExecutorService())
}