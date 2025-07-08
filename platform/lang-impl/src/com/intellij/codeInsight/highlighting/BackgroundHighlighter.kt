// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.application.options.editor.EditorOptionsListener
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingResult.Companion.EMPTY_RESULT
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingResult.Companion.WRONG_DOCUMENT_VERSION
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateManagerUtilBase
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindResult
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.Alarm
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.Runnable
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * Listens for editor events and starts brace/identifier highlighting in the background
 */
@Service
@ApiStatus.Internal
class BackgroundHighlighter(coroutineScope: CoroutineScope) {
  @JvmField val alarm: Alarm = Alarm(coroutineScope, Alarm.ThreadToUse.SWING_THREAD)

  /**
   * register a callback which runs whenever the caret/selection/whatever changes (see [registerListeners])
   */
  @ApiStatus.Internal
  fun registerListeners(
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
          onCaretUpdate(e.editor, project, executor, coroutineScope)
        }
      }

      override fun caretAdded(e: CaretEvent) {
        if (e.caret === e.editor.caretModel.primaryCaret) {
          onCaretUpdate(e.editor, project, executor, coroutineScope)
        }
      }

      override fun caretRemoved(e: CaretEvent) {
        onCaretUpdate(e.editor, project, executor, coroutineScope)
      }
    }, parentDisposable)

    eventMulticaster.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        alarm.cancelAllRequests()
        val editor = e.editor
        if (editor.project !== project) {
          return
        }

        highlightSelection(project, editor, executor)

        val oldRange = e.oldRange
        val newRange = e.newRange
        if (oldRange != null && newRange != null && oldRange.isEmpty == newRange.isEmpty) {
          // don't update braces in case of active/absent selection.
          return
        }

        updateHighlighted(project, editor, coroutineScope)
      }
    }, parentDisposable)

    eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        alarm.cancelAllRequests()
        editorFactory.editors(e.document, project).forEach {
          updateHighlighted(project, it, coroutineScope)
          highlightSelection(project, it, executor)
        }
      }
    }, parentDisposable)

    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(e: FileEditorManagerEvent) {
        alarm.cancelAllRequests()
        val oldEditor = e.oldEditor
        if (oldEditor is TextEditor) {
          BraceHighlightingHandler.clearBraceHighlighters(oldEditor.editor)
        }

        val newEditor = e.newEditor
        if (newEditor is TextEditor) {
          val editor = newEditor.editor
          updateHighlighted(project, editor, coroutineScope)
          highlightSelection(project, editor, executor)
        }
      }
    })

    connection.subscribe<TemplateManagerListener>(TemplateManager.TEMPLATE_STARTED_TOPIC, TemplateManagerListener { state ->
      if (state.isFinished) {
        return@TemplateManagerListener
      }

      updateHighlighted(project, state.editor, coroutineScope)
      state.addTemplateStateListener(object : TemplateEditingAdapter() {
        override fun templateFinished(template: Template, brokenOff: Boolean) {
          updateHighlighted(project, state.editor, coroutineScope)
        }
      })
    })
    connection.subscribe(EditorOptionsListener.OPTIONS_PANEL_TOPIC, EditorOptionsListener {
      clearAllIdentifierHighlighters()
    })
    DocumentAfterCommitListener.listen(project, parentDisposable) { document ->
      editorFactory.editors(document, project).forEach {
        updateHighlighted(project, it, coroutineScope)
        highlightSelection(project, it, executor)
      }
    }
  }

  private fun clearAllIdentifierHighlighters() {
    for (project in ProjectManager.getInstance().openProjects) {
      for (fileEditor in FileEditorManager.getInstance(project).allEditors) {
        if (fileEditor is TextEditor) {
          val document = fileEditor.editor.document
          IdentifierHighlighterUpdater.clearMyHighlights(document, project)
        }
      }
    }
  }

  private fun onCaretUpdate(editor: Editor, project: Project, executor: Executor, coroutineScope: CoroutineScope) {
    alarm.cancelAllRequests()
    if (editor.project !== project) return

    // don't update braces in case of the active selection.
    if (!editor.selectionModel.hasSelection()) {
      updateHighlighted(project, editor, coroutineScope)
    }
    highlightSelection(project, editor, executor)
  }

  @RequiresEdt
  private fun updateHighlighted(project: Project, hostEditor: Editor, coroutineScope: CoroutineScope) {
    val hostDocument = hostEditor.document
    if (hostDocument.isInBulkUpdate || !BackgroundHighlightingUtil.isValidEditor(hostEditor)) {
      return
    }
    val offsetBefore = hostEditor.caretModel.offset
    val visibleRange = hostEditor.calculateVisibleRange()
    val needMatching = BackgroundHighlightingUtil.needMatching(hostEditor, CodeInsightSettings.getInstance())
    coroutineScope.launch(context = CoroutineName("BackgroundHighlighter.updateHighlighted(${hostEditor.document})")) {
      val job:Job = coroutineContext.job
      val oldJob = (hostEditor as UserDataHolderEx).getAndUpdateUserData(BACKGROUND_TASK) {
        job
      }
      oldJob?.cancel()
      // clear user data only after the nested launches completed, since they assume the job is still in the editor user data, see isEditorUpToDate
      job.invokeOnCompletion {
        (hostEditor as UserDataHolderEx).getAndUpdateUserData(BACKGROUND_TASK) {
          oldJob -> if (oldJob == job) null else oldJob // remove my job, but don't touch the job if not mine because it might be the newest job
        }
      }

      val documentModStampBefore = hostDocument.modificationStamp
      val injected = readAction { BackgroundHighlightingUtil.findInjected(hostEditor, project, offsetBefore) } ?: return@launch
      val newPsiFile = injected.first
      val newEditor = injected.second
      val modalityState: CoroutineContext = ModalityState.stateForComponent(hostEditor.component).asContextElement()
      val maybeMatch = if (needMatching) {
        readAction {
          HeavyBraceHighlighter.match(newPsiFile, offsetBefore)
        }
      }
      else {
        null
      }
      // launch nested coroutines that finish only after the main job is finished, to be ble to wait for identifier highlighting to apply, see waitForIdentifierHighlighting
      launch(Dispatchers.EDT + modalityState) {
        if (isEditorUpToDate(hostEditor, offsetBefore, newEditor, newPsiFile, documentModStampBefore, job)) {
          applyBraceMatching(project, newEditor, newPsiFile, maybeMatch, needMatching)
        }
      }

      val identPass = readAction {
        createPass(newPsiFile, hostEditor, newEditor)
      }
      if (identPass != null) {
        var result = EMPTY_RESULT
        var infos = listOf<HighlightInfo>()
        try {
          result = identPass.doCollectInformation(project, visibleRange)
          if (result == WRONG_DOCUMENT_VERSION) {
            launch(Dispatchers.EDT + modalityState) {
              updateHighlighted(project, hostEditor, coroutineScope)
            }
            return@launch
          }
          infos = readAction {
            identPass.createHighlightInfos(result)
          }
        }
        catch (_: IndexNotReadyException) {
        }
        launch(Dispatchers.EDT + modalityState) {
          if (isEditorUpToDate(hostEditor, offsetBefore, newEditor, newPsiFile, documentModStampBefore, job)) {
            UpdateHighlightersUtil.setHighlightersToSingleEditor(project, hostEditor, 0, hostDocument.textLength, infos, hostEditor.colorsScheme, IdentifierHighlighterUpdater.id)
            identPass.doAdditionalCodeBlockHighlighting(result)
          }
        }
      }
    }
  }

  @RequiresEdt
  private suspend fun isEditorUpToDate(
    hostEditor: Editor,
    offsetBefore: Int,
    newEditor: Editor,
    newPsiFile: PsiFile,
    documentModStampBefore: Long,
    job: Job,
  ): Boolean {
    return BackgroundHighlightingUtil.isValidEditor(hostEditor)
           && hostEditor.caretModel.offset == offsetBefore
           && (newEditor == hostEditor || BackgroundHighlightingUtil.isValidEditor(newEditor))
           && hostEditor.document.modificationStamp == documentModStampBefore
           && readAction { newPsiFile.isValid() }
           && hostEditor.getUserData(BACKGROUND_TASK) == job
  }

  @RequiresEdt
  fun applyBraceMatching(
    project: Project,
    newEditor: Editor,
    newPsiFile: PsiFile,
    maybeMatch: Pair<TextRange, TextRange>?,
    needMatching: Boolean,
  ) {
    val handler = BraceHighlightingHandler(project, newEditor, alarm, newPsiFile)
    if (maybeMatch == null) {
      alarm.cancelAllRequests()
      handler.updateBraces(needMatching)
    }
    else {
      val fileType = PsiUtilBase.getPsiFileAtOffset(newPsiFile, maybeMatch.first.startOffset).fileType
      BraceHighlightingHandler.clearBraceHighlighters(newEditor)
      handler.highlightBraces(maybeMatch.first, maybeMatch.second, matched = true, scopeHighlighting = false, fileType = fileType)
    }
  }

  companion object {
    @TestOnly
    fun runWithEnabledListenersInTest(project: Project, r: Runnable) {
      val perProjectDisposable = project.service<BackgroundHighlighterPerProject>()
      val parentDisposable = Disposer.newDisposable()
      val coroutineScope = perProjectDisposable.coroutineScope.childScope("Test Background Highlighter(disposable=$parentDisposable)")
      Disposer.register(parentDisposable) {
        coroutineScope.cancel()
      }

      service<BackgroundHighlighter>().registerListeners(project, parentDisposable, coroutineScope)
      try {
        r.run()
      }
      finally {
        Disposer.dispose(parentDisposable)
      }
    }

    @RequiresReadLock
    fun createPass(newPsiFile: PsiFile, hostEditor: Editor, newEditor: Editor): IdentifierHighlighterUpdater? {
      if (newPsiFile.isValid) {
        val textLength = newPsiFile.textLength
        val project = newPsiFile.project
        val hostPsiFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(newPsiFile)
        // sometimes some crazy stuff is returned (EA-248725)
        if (textLength != -1 && !hostEditor.isDisposed && IdentifierHighlighterPassFactory.shouldHighlightIdentifiers(newPsiFile, newEditor) && hostPsiFile != null) {
          val context = EditorContextManager.getEditorContext(newEditor, project)
          val pass = IdentifierHighlighterUpdater(newPsiFile, newEditor, context, hostPsiFile)
          return pass
        }
      }
      return null
    }

    private val BACKGROUND_TASK: Key<Job> = Key.create("BACKGROUND_TASK")
    @TestOnly
    fun waitForIdentifierHighlighting(editor: Editor) {
      val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
      val job = hostEditor.getUserData(BACKGROUND_TASK)
      if (job != null) {
        while (!job.isCompleted) {
          if (EDT.isCurrentThreadEdt()) {
            UIUtil.dispatchAllInvocationEvents()
          }
          else {
            UIUtil.pump()
          }
        }
      }
      if (LOG.isDebugEnabled) {
        LOG.debug("waitForIdentifierHighlighting($editor): waited $job")
      }
    }
  }
}

private val LOG: Logger = Logger.getInstance(BackgroundHighlighter::class.java)

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class BackgroundHighlighterPerProject(@JvmField val coroutineScope: CoroutineScope): Disposable.Default

@RequiresEdt
private fun selectionRangeToFind(editor: Editor): TextRange? {
  val document = editor.document
  if (document.isInBulkUpdate || !BackgroundHighlightingUtil.isValidEditor(editor)) {
    return null
  }

  if (!editor.settings.isHighlightSelectionOccurrences) {
    return null
  }

  if (TemplateManagerUtilBase.getTemplateState(editor) != null) {
    // don't highlight selected text when template is active
    return null
  }

  val caretModel = editor.caretModel
  if (caretModel.caretCount > 1) {
    return null
  }

  val caret = caretModel.primaryCaret
  if (!caret.hasSelection()) {
    return null
  }
  return caret.selectionRange
}

@RequiresEdt
private fun highlightSelection(project: Project, editor: Editor, executor: Executor): Boolean {
  ThreadingAssertions.assertEventDispatchThread()
  val document = editor.document
  val selectionRange = selectionRangeToFind(editor)
  val sequence = document.charsSequence
  val toFind = selectionRange?.subSequence(sequence)?.toString()
  if (toFind == null || toFind.isBlank() || toFind.contains("\n")) {
    removeSelectionHighlights(editor)
    return false
  }
  val stamp = document.modificationStamp

  if (toFind == editor.getUserData(SELECTION_HIGHLIGHTS)?.text) {
    return true
  }

  val findManager = FindManager.getInstance(project)
  val findModel = FindModel()
  findModel.copyFrom(findManager.findInFileModel)
  findModel.isRegularExpressions = false
  findModel.stringToFind = toFind
  val threshold = intValue("editor.highlight.selected.text.max.occurrences.threshold", 50)
  ReadAction.nonBlocking<List<FindResult>> {
    if (!BackgroundHighlightingUtil.isValidEditor(editor)) return@nonBlocking emptyList()
    val sequence = document.charsSequence
    var result = findManager.findString(sequence, 0, findModel, null)
    val results = ArrayList<FindResult>()
    var count = 0
    while (result.isStringFound && count < LivePreviewController.MATCHES_LIMIT) {
      count++
      if (count > threshold) {
        return@nonBlocking emptyList()
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
        if (TextRange.areSegmentsEqual(selectionRange, result)) {
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
  val highlighters = (editor as UserDataHolderEx).getAndUpdateUserData(SELECTION_HIGHLIGHTS) { null }?.highlighters ?: return
  val markupModel = editor.markupModel
  for (highlighter in highlighters) {
    markupModel.removeHighlighter(highlighter)
  }
}

private class SelectionHighlights(val text: String, val highlighters: Collection<RangeHighlighter>)
private val SELECTION_HIGHLIGHTS = Key<SelectionHighlights>("SELECTION_HIGHLIGHTS")
private class HighlightSelectionKey
