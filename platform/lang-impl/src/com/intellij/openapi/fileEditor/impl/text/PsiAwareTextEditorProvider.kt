// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.impl.EditorGutterLayout
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.zombie.Necropolis
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.createdFileEditorSink
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private const val FOLDING_ELEMENT: @NonNls String = "folding"

open class PsiAwareTextEditorProvider : TextEditorProvider(), AsyncFileEditorProvider {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val interceptedEditor = ImplicitSplitModeEditorBinder.tryBindSuppliedEditorToBackend(this, project, file)
    if (interceptedEditor != null) return interceptedEditor

    return PsiAwareTextEditorImpl(project = project, file = file, provider = this)
  }

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): TextEditor {
    val interceptedEditor = ImplicitSplitModeEditorBinder.tryBindSuppliedEditorToBackendAsync(this, project, file, document, editorCoroutineScope)
    if (interceptedEditor != null) return interceptedEditor

    val asyncLoader = createAsyncEditorLoader(
      provider = this,
      project = project,
      fileForTelemetry = file,
      editorCoroutineScope = editorCoroutineScope,
    )

    // if cancellation lands between the editor creation and the consumption of the result, coroutineScope discards the created editor
    val createdEditors = createdFileEditorSink()
    return coroutineScope {
      val effectiveDocument = document!!

      // trigger opening of persistent maps in advance
      span("editor necropolis preload") {
        Necropolis.getInstanceAsync(project)
      }

      // the editor listeners on EDT can detect the file type from the content, which asks for the project encoding
      project.serviceAsync<EncodingProjectManager>()

      val highlighterDeferred = async(CoroutineName("editor highlighter creating")) {
        prepareHighlighter(project, file, effectiveDocument)
      }

      val editorDeferred = CompletableDeferred<EditorEx>()

      val task = createInitTask(
        asyncLoader = asyncLoader,
        editorDeferred = editorDeferred,
        highlighterDeferred = highlighterDeferred,
        project = project,
        file = file,
        document = effectiveDocument,
      )

      val factory = serviceAsync<EditorFactory>() as EditorFactoryImpl
      var pendingEditor: EditorFactoryImpl.PendingMainEditor? = null
      var disposeUnclaimedEditor: (() -> Unit)? = null

      try {
        if (canPrepareEditorShell() && !highlighterDeferred.isCompleted) {
          span("editor shell construction on EDT", Dispatchers.EDT) {
            if (highlighterDeferred.isCompleted) return@span

            val pending = factory.prepareMainEditor(effectiveDocument, project, file)
            pendingEditor = pending
            disposeUnclaimedEditor = pending::close
          }
        }
        val highlighter = span("editor prepared highlighter await") { highlighterDeferred.await() }
        val textEditor = span("initialize text editor on EDT", Dispatchers.EDT) {
          val editor = pendingEditor?.complete(highlighter) {
            it.putUserData(AsyncEditorLoader.ASYNC_LOADER, asyncLoader)
          } ?: initializeEditor(factory, effectiveDocument, project, file, highlighter, asyncLoader)

          disposeUnclaimedEditor = { factory.releaseEditor(editor) }
          editorDeferred.complete(editor)
          editor.gutterComponentEx.setInitialIconAreaWidth(EditorGutterLayout.getInitialGutterWidth())
          val component = createPsiAwareTextEditorComponent(file = file, editor = editor)
          val textEditor = PsiAwareTextEditorImpl(project = project, file = file, component = component, asyncLoader = asyncLoader)
          disposeUnclaimedEditor = { Disposer.dispose(textEditor) }
          asyncLoader.start(textEditor = textEditor, task = task)
          if (createdEditors != null) {
            createdEditors.register(textEditor)
            disposeUnclaimedEditor = null
          }
          textEditor
        }
        disposeUnclaimedEditor = null
        textEditor
      }
      finally {
        val dispose = disposeUnclaimedEditor
        if (dispose != null) {
          withContext(NonCancellable + Dispatchers.EDT) {
            dispose()
          }
        }
      }
    }
  }

  /** The shell path bypasses [initializeEditor] and is available only for the base provider. */
  private fun canPrepareEditorShell(): Boolean {
    return SystemProperties.getBooleanProperty("idea.editor.highlighter.prepare.in.parallel", true) &&
           javaClass == PsiAwareTextEditorProvider::class.java
  }

  private suspend fun prepareHighlighter(project: Project, file: VirtualFile, document: Document): EditorHighlighter {
    val scheme = serviceAsync<EditorColorsManager>().globalScheme
    val factory = serviceAsync<EditorHighlighterFactory>()
    val highlighter = span("editor highlighter factory") {
      readActionBlocking {
        factory.createEditorHighlighter(file = file, editorColorScheme = scheme, project = project)
      }
    }
    span("editor highlighter tokenization") {
      highlighter.setText(document.immutableCharSequence)
    }
    if (document.immutableCharSequence.isNotEmpty()) {
      // Load the highlighter attributes before EDT initialization.
      highlighter.createIterator(0).textAttributes
    }
    return highlighter
  }

  @ApiStatus.Internal
  @ApiStatus.OverrideOnly
  protected open fun initializeEditor(
    factory: EditorFactoryImpl,
    effectiveDocument: Document,
    project: Project,
    file: VirtualFile,
    highlighter: EditorHighlighter,
    asyncLoader: AsyncEditorLoader,
  ): EditorImpl = factory.createMainEditor(
    document = effectiveDocument,
    project = project,
    file = file,
    highlighter = highlighter,
    afterCreation = {
      it.putUserData(AsyncEditorLoader.ASYNC_LOADER, asyncLoader)
    },
  )

  // Deferred<Unit> - to handle error by loader
  private fun createInitTask(
    asyncLoader: AsyncEditorLoader,
    editorDeferred: CompletableDeferred<EditorEx>,
    highlighterDeferred: Deferred<EditorHighlighter>,
    project: Project,
    file: VirtualFile,
    document: Document,
  ): Deferred<Unit> {
    return asyncLoader.coroutineScope.async(CoroutineName("call TextEditorInitializers")) {
      val editorSupplier = suspend { editorDeferred.await() }
      val highlighterReady = suspend { highlighterDeferred.join() }

      val necropolis = Necropolis.getInstanceAsync(project)
      span("editor cached markup restoring") {
        necropolis?.spawnZombies(project, file, document, editorSupplier, highlighterReady)
      }

      val editor = editorSupplier()
      span("editor languageSupplier set", Dispatchers.EDT) {
        editor.settings.setLanguageSupplier { TextEditorImpl.getDocumentLanguage(editor) }
      }
    }
  }

  override fun readState(element: Element, project: Project, file: Lazy<VirtualFile?>): FileEditorState {
    val state = super<TextEditorProvider>.readState(element, project, file) as TextEditorState
    val foldingElement = element.getChild(FOLDING_ELEMENT)
    if (foldingElement == null) return state

    // This code is called from loadState() of EditorHistoryManager init
    // never use read action here, it leads to deadlocks
    // delay folding state computation till they are needed

    return state.withLazyFoldingState {
      val vFile = file.value ?: return@withLazyFoldingState null

      val document = if (BinaryFileTypeDecompilers.getInstance().hasDecompiler(vFile)) {
        // otherwise we will decompile files and cause performance issues
        FileDocumentManager.getInstance().getCachedDocument(vFile)
      }
      else {
        FileDocumentManager.getInstance().getDocument(vFile)
      }

      if (document != null) {
        CodeFoldingManager.getInstance(project).readFoldingState(foldingElement, document)
      }
      else {
        null
      }
    }
  }

  override fun writeState(state: FileEditorState, project: Project, element: Element) {
    super<TextEditorProvider>.writeState(state = state, project = project, element = element)

    state as TextEditorState

    // foldings
    val foldingState = state.foldingState
    if (foldingState != null) {
      val e = Element(FOLDING_ELEMENT)
      try {
        CodeFoldingManager.getInstance(project).writeFoldingState(foldingState, e)
      }
      catch (_: WriteExternalException) {
      }
      if (!e.isEmpty) {
        element.addContent(e)
      }
    }
  }

  override fun getStateImpl(project: Project?, editor: Editor, level: FileEditorStateLevel): TextEditorState {
    val state = super.getStateImpl(project, editor, level)
    // Save folding only on FULL level. It's costly to commit a document on every type (caused by undo).
    if (FileEditorStateLevel.FULL == level && project != null && !project.isDisposed && !editor.isDisposed && project.isInitialized) {
      return state.withFoldingState(CodeFoldingManager.getInstance(project).saveFoldingState(editor))
    }
    return state
  }

  override fun setStateImpl(project: Project?, editor: Editor, state: TextEditorState, exactState: Boolean) {
    super.setStateImpl(project = project, editor = editor, state = state, exactState = exactState)

    // folding
    val foldState = state.foldingState
    if (project != null && foldState != null) {
      if (isEditorLoaded(editor)) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        if (!psiDocumentManager.isCommitted(editor.document)) {
          psiDocumentManager.commitDocument(editor.document)
          logger<PsiAwareTextEditorProvider>()
            .error("File should be parsed when changing editor state, otherwise UI might be frozen for a considerable time")
        }
      }
      CodeFoldingManager.getInstance(project).restoreFoldingState(editor, foldState)
    }
  }

  override fun createWrapperForEditor(editor: Editor): EditorWrapper = PsiAwareEditorWrapper(editor)

  private inner class PsiAwareEditorWrapper(editor: Editor) : EditorWrapper(editor) {
    private val backgroundHighlighter = editor.project?.let { TextEditorBackgroundHighlighter(it, editor) }

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = backgroundHighlighter
  }
}
