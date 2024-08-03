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

private val SELECTION_HIGHLIGHTS = Key<Collection<RangeHighlighter>>("SELECTION_HIGHLIGHTS")
private val HIGHLIGHTED_TEXT = Key<String>("HIGHLIGHTED_TEXT")

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
  @JvmField val alarm = Alarm(coroutineScope = coroutineScope, threadToUse = Alarm.ThreadToUse.SWING_THREAD)

  companion object {
    @TestOnly
    fun enableListenersInTest(project: Project, parentDisposable: Disposable) {
      val d = project.service<BackgroundHighlighterPerProject>()
      val coroutineScope = d.coroutineScope.childScope("Test Background Highlighter(disposable=$parentDisposable)")
      Disposer.register(parentDisposable, Disposable {
        coroutineScope.cancel()
      })
      registerListeners(project = project, parentDisposable = parentDisposable, alarm = service<BackgroundHighlighter>().alarm, coroutineScope = coroutineScope)
    }
  }

  suspend fun runActivity(project: Project) {
    val perProjectDisposable = project.serviceAsync<BackgroundHighlighterPerProject>()
    registerListeners(
      project = project,
      parentDisposable = perProjectDisposable,
      alarm = alarm,
      coroutineScope = perProjectDisposable.coroutineScope
    )
  }
}

@Service(Service.Level.PROJECT)
private class BackgroundHighlighterPerProject(@JvmField val coroutineScope: CoroutineScope): Disposable.Default

private fun registerListeners(
  project: Project,
  parentDisposable: Disposable,
  alarm: Alarm,
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
        onCaretUpdate(editor = e.editor, project = project, alarm = alarm, executor = executor)
      }
    }

    override fun caretAdded(e: CaretEvent) {
      if (e.caret === e.editor.caretModel.primaryCaret) {
        onCaretUpdate(editor = e.editor, project = project, alarm = alarm, executor = executor)
      }
    }

    override fun caretRemoved(e: CaretEvent) {
      onCaretUpdate(editor = e.editor, project = project, alarm = alarm, executor = executor)
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

      updateHighlighted(project = project, editor = editor, alarm = alarm, executor = executor)
    }
  }, parentDisposable)

  eventMulticaster.addDocumentListener(object : DocumentListener {
    override fun documentChanged(e: DocumentEvent) {
      alarm.cancelAllRequests()
      editorFactory.editors(e.document, project).forEach {
        updateHighlighted(project = project, editor = it, alarm = alarm, executor = executor)
      }
    }
  }, parentDisposable)

  val connection = project.messageBus.connect(coroutineScope)
  connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
    override fun selectionChanged(e: FileEditorManagerEvent) {
      alarm.cancelAllRequests()
      val oldEditor = e.oldEditor
      if (oldEditor is TextEditor) {
        clearBraces(project = project, editor = oldEditor.editor, alarm = alarm)
      }

      val newEditor = e.newEditor
      if (newEditor is TextEditor) {
        val editor = newEditor.editor
        updateHighlighted(project = project, editor = editor, alarm = alarm, executor = executor)
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

    updateHighlighted(project = project, editor = state.editor, alarm = alarm, executor = executor)
    state.addTemplateStateListener(object : TemplateEditingAdapter() {
      override fun templateFinished(template: Template, brokenOff: Boolean) {
        updateHighlighted(project = project, editor = state.editor, alarm = alarm, executor = executor)
      }
    })
  })
}

private fun onCaretUpdate(editor: Editor, project: Project, alarm: Alarm, executor: Executor) {
  alarm.cancelAllRequests()
  val selectionModel = editor.selectionModel
  // don't update braces in case of the active selection.
  if (editor.project === project && !selectionModel.hasSelection()) {
    updateHighlighted(project = project, editor = editor, alarm = alarm, executor = executor)
  }
}

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
    .expireWhen { document.modificationStamp != stamp || editor.isDisposed }
    .finishOnUiThread(ModalityState.nonModal()) { results ->
      if (document.modificationStamp != stamp || results.isEmpty()) {
        return@finishOnUiThread
      }

      removeSelectionHighlights(editor)
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
      editor.putUserData(SELECTION_HIGHLIGHTS, highlighters)
    }
    .submit(executor)
  return true
}

private fun clearBraces(project: Project, editor: Editor, alarm: Alarm) {
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

private fun removeSelectionHighlights(editor: Editor) {
  editor.getUserData(SELECTION_HIGHLIGHTS)?.let { oldHighlighters ->
    editor.putUserData(SELECTION_HIGHLIGHTS, null)
    val markupModel = editor.markupModel
    for (highlighter in oldHighlighters) {
      markupModel.removeHighlighter(highlighter)
    }
  }
  editor.putUserData(HIGHLIGHTED_TEXT, null)
}

@RequiresEdt
private fun updateHighlighted(project: Project, editor: Editor, alarm: Alarm, executor: Executor) {
  if (editor.document.isInBulkUpdate || !BackgroundHighlightingUtil.isValidEditor(editor)) {
    return
  }

  BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(
    project, editor,
    { newFile: PsiFile, newEditor: Editor ->
      val offsetBefore = editor.caretModel.offset
      submitIdentifierHighlighterPass(
        hostEditor = editor,
        offsetBefore = offsetBefore,
        newFile = newFile,
        newEditor = newEditor,
        executor = executor,
      )
      HeavyBraceHighlighter.match(newFile, offsetBefore)
    },
    { newFile: PsiFile, newEditor: Editor, maybeMatch: Pair<TextRange, TextRange>? ->
      val handler = BraceHighlightingHandler(project = project, editor = newEditor, alarm = alarm, psiFile = newFile)
      if (maybeMatch == null) {
        handler.updateBraces()
      }
      else {
        val codeInsightSettings = CodeInsightSettings.getInstance()
        if (BackgroundHighlightingUtil.needMatching(newEditor, codeInsightSettings)) {
          val fileType = PsiUtilBase.getPsiFileAtOffset(newFile, maybeMatch.first.startOffset).fileType
          handler.clearBraceHighlighters()
          handler.highlightBraces(
            lBrace = maybeMatch.first,
            rBrace = maybeMatch.second,
            matched = true,
            scopeHighlighting = false,
            fileType = fileType,
          )
        }
      }
    })
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
                                                             false) { pass?.doCollectInformation(it) }
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