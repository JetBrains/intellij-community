// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateManagerUtilBase
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindResult
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.Alarm
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor

private val SELECTION_HIGHLIGHTS = Key<SelectionHighlights>("SELECTION_HIGHLIGHTS")
private class SelectionHighlights(val text: String, val highlighters: Collection<RangeHighlighter>)

private class HighlightIdentifiersKey
private class HighlightSelectionKey

private class BackgroundHighlighterProjectActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment && !app.isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (IdentifierHighlighterPassFactory.isEnabled()) {
      serviceAsync<BackgroundHighlighter>().runActivity(project)
    }
  }
}

/**
 * Listens for editor events and starts brace/identifier highlighting in the background
 */
@Service
internal class BackgroundHighlighter(coroutineScope: CoroutineScope) {
  @JvmField val alarm = Alarm(coroutineScope, Alarm.ThreadToUse.SWING_THREAD)

  companion object {
    @TestOnly
    fun enableListenersInTest(project: Project, parentDisposable: Disposable) {
      val d = project.service<BackgroundHighlighterPerProject>()
      val coroutineScope = d.coroutineScope.childScope("Test Background Highlighter(disposable=$parentDisposable)")
      Disposer.register(parentDisposable, Disposable {
        coroutineScope.cancel()
      })
      service<BackgroundHighlighter>().registerListeners(project, parentDisposable, coroutineScope)
    }
  }

  suspend fun runActivity(project: Project) {
    val perProjectDisposable = project.serviceAsync<BackgroundHighlighterPerProject>()
    registerListeners(project, perProjectDisposable, perProjectDisposable.coroutineScope)
  }

  private fun registerListeners(
    project: Project,
    parentDisposable: Disposable,
    coroutineScope: CoroutineScope,
  ) {
    val editorFactory = EditorFactory.getInstance()
    val eventMulticaster = editorFactory.eventMulticaster

    val executor = Executor { task ->
      coroutineScope.launch {
        task.run()
      }
    }

    eventMulticaster.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(e: CaretEvent) {
        if (e.caret === e.editor.caretModel.primaryCaret) {
          onCaretUpdate(e.editor, project, executor)
        }
      }

      override fun caretAdded(e: CaretEvent) {
        if (e.caret === e.editor.caretModel.primaryCaret) {
          onCaretUpdate(e.editor, project, executor)
        }
      }

      override fun caretRemoved(e: CaretEvent) {
        onCaretUpdate(e.editor, project, executor)
      }
    }, parentDisposable)

    eventMulticaster.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        alarm.cancelAllRequests()
        val editor = e.editor
        if (editor.project !== project) {
          return
        }

        if (!highlightSelection(project, editor, executor)) {
          removeSelectionHighlights(editor)
        }

        val oldRange = e.oldRange
        val newRange = e.newRange
        if (oldRange != null && newRange != null && oldRange.isEmpty == newRange.isEmpty) {
          // don't update braces in case of active/absent selection.
          return
        }

        updateHighlighted(project, editor, executor)
      }
    }, parentDisposable)

    eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        alarm.cancelAllRequests()
        editorFactory.editors(e.document, project).forEach {
          updateHighlighted(project, it, executor)
          if (!highlightSelection(project, it, executor)) {
            removeSelectionHighlights(it)
          }
        }
      }
    }, parentDisposable)

    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(e: FileEditorManagerEvent) {
        alarm.cancelAllRequests()
        val oldEditor = e.oldEditor
        if (oldEditor is TextEditor) {
          clearBraces(project, oldEditor.editor)
        }

        val newEditor = e.newEditor
        if (newEditor is TextEditor) {
          val editor = newEditor.editor
          updateHighlighted(project, editor, executor)
          if (!highlightSelection(project, editor, executor)) {
            removeSelectionHighlights(editor)
          }
        }
      }
    })

    connection.subscribe<TemplateManagerListener>(TemplateManager.TEMPLATE_STARTED_TOPIC, TemplateManagerListener { state ->
      if (state.isFinished) {
        return@TemplateManagerListener
      }

      updateHighlighted(project, state.editor, executor)
      state.addTemplateStateListener(object : TemplateEditingAdapter() {
        override fun templateFinished(template: Template, brokenOff: Boolean) {
          updateHighlighted(project, state.editor, executor)
        }
      })
    })
  }
  private fun onCaretUpdate(editor: Editor, project: Project, executor: Executor) {
    alarm.cancelAllRequests()
    if (editor.project !== project) return

    // don't update braces in case of the active selection.
    if (!editor.selectionModel.hasSelection()) {
      updateHighlighted(project, editor, executor)
    }

    if (!highlightSelection(project, editor, executor)) {
      removeSelectionHighlights(editor)
    }
  }

  @RequiresEdt
  private fun updateHighlighted(project: Project, editor: Editor, executor: Executor) {
    if (editor.document.isInBulkUpdate || !BackgroundHighlightingUtil.isValidEditor(editor)) {
      return
    }

    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(
      project, editor,
      { newFile: PsiFile, newEditor: Editor ->
        val offsetBefore = editor.caretModel.offset
        submitIdentifierHighlighterPass(editor, offsetBefore, newFile, newEditor, executor)
        HeavyBraceHighlighter.match(newFile, offsetBefore)
      },
      { newFile: PsiFile, newEditor: Editor, maybeMatch: Pair<TextRange, TextRange>? ->
        val handler = BraceHighlightingHandler(project, newEditor, alarm, newFile)
        if (maybeMatch == null) {
          alarm.cancelAllRequests()
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

  private fun clearBraces(project: Project, editor: Editor) {
    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread<Any?>(
      project,
      editor,
      { _: PsiFile?, _: Editor? -> null },
      { foundFile: PsiFile, newEditor: Editor, _: Any? ->
        val handler = BraceHighlightingHandler(
          project,
          newEditor, alarm,
          foundFile,
        )
        handler.clearBraceHighlighters()
      },
    )
  }
}

@Service(Service.Level.PROJECT)
private class BackgroundHighlighterPerProject(@JvmField val coroutineScope: CoroutineScope): Disposable.Default

@RequiresEdt
private fun highlightSelection(project: Project, editor: Editor, executor: Executor): Boolean {
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
    // don't highlight selected text when template is active
    return false
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
  if (toFind.isBlank() || toFind.contains("\n")) {
    return false
  }

  val previous = editor.getUserData(SELECTION_HIGHLIGHTS)?.text
  if (toFind == previous) {
    return true
  }

  val findManager = FindManager.getInstance(project)
  val findModel = FindModel()
  findModel.copyFrom(findManager.findInFileModel)
  findModel.isRegularExpressions = false
  findModel.stringToFind = toFind
  val threshold = intValue("editor.highlight.selected.text.max.occurrences.threshold", 50)
  ReadAction.nonBlocking<List<FindResult>> {
    if (!BackgroundHighlightingUtil.isValidEditor(editor)) return@nonBlocking emptyList<FindResult>()
    var result = findManager.findString(sequence, 0, findModel, null)
    val results = ArrayList<FindResult>()
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
    .expireWhen { document.modificationStamp != stamp || !BackgroundHighlightingUtil.isValidEditor(editor) }
    .finishOnUiThread(ModalityState.nonModal()) { results ->
      if (!BackgroundHighlightingUtil.isValidEditor(editor)) return@finishOnUiThread
      removeSelectionHighlights(editor)
      if (document.modificationStamp != stamp || results.isEmpty()) {
        return@finishOnUiThread
      }

      val highlighters = ArrayList<RangeHighlighter>()
      val markupModel = editor.markupModel
      for (result in results) {
        val startOffset = result.startOffset
        val endOffset = result.endOffset
        if (startOffset == start && endOffset == end) {
          continue
        }

        highlighters.add(markupModel.addRangeHighlighter(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, startOffset, endOffset,
                                                         HighlightManagerImpl.OCCURRENCE_LAYER,
                                                         HighlighterTargetArea.EXACT_RANGE))
      }
      editor.putUserData(SELECTION_HIGHLIGHTS, SelectionHighlights(toFind, highlighters))
    }
    .submit(executor)
  return true
}

private fun removeSelectionHighlights(editor: Editor) {
  val highlighters = editor.getUserData(SELECTION_HIGHLIGHTS)?.highlighters ?: return
  editor.putUserData(SELECTION_HIGHLIGHTS, null)
  val markupModel = editor.markupModel
  for (highlighter in highlighters) {
    markupModel.removeHighlighter(highlighter)
  }
}

private fun submitIdentifierHighlighterPass(
  hostEditor: Editor,
  offsetBefore: Int,
  newFile: PsiFile,
  newEditor: Editor,
  executor: Executor,
) {
  ReadAction.nonBlocking<IdentifierHighlighterPass?> {
    if (!newFile.isValid) {
      return@nonBlocking null
    }

    val textLength = newFile.textLength
    if (textLength == -1 || hostEditor.isDisposed) {
      // sometimes some crazy stuff is returned (EA-248725)
      return@nonBlocking null
    }

    val visibleRange = ProperTextRange.from(0, textLength)
    val pass = IdentifierHighlighterPassFactory().createHighlightingPass(newFile, newEditor, visibleRange)
    val indicator = DaemonProgressIndicator()
    @Suppress("DEPRECATION")
    ProgressIndicatorUtils.runWithWriteActionPriority(
      {
        val hostPsiFile = PsiDocumentManager.getInstance(newFile.project).getPsiFile(hostEditor.document)
                          ?: return@runWithWriteActionPriority
        HighlightingSessionImpl.runInsideHighlightingSession(hostPsiFile,
                                                             hostEditor.colorsScheme,
                                                             ProperTextRange.create(hostPsiFile.textRange),
                                                             false) {
          try {
            pass?.doCollectInformation(it)
          }
          catch (_: IndexNotReadyException) {
          }
        }
      },
      indicator,
    )
    pass
  }
    .expireWhen { !BackgroundHighlightingUtil.isValidEditor(hostEditor) || hostEditor.caretModel.offset != offsetBefore }
    .coalesceBy(HighlightIdentifiersKey::class.java, hostEditor)
    .finishOnUiThread(ModalityState.stateForComponent(hostEditor.component)) { it?.doAdditionalCodeBlockHighlighting() }
    .submit(executor)
}