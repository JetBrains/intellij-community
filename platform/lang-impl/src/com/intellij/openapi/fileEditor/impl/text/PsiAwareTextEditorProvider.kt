// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.writeIntentReadAction
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
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.editor.impl.zombie.Necropolis
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

private const val FOLDING_ELEMENT: @NonNls String = "folding"

open class PsiAwareTextEditorProvider : TextEditorProvider(), AsyncFileEditorProvider {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return PsiAwareTextEditorImpl(project = project, file = file, provider = this)
  }

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): TextEditor {
    val asyncLoader = createAsyncEditorLoader(
      provider = this,
      project = project,
      fileForTelemetry = file,
      editorCoroutineScope = editorCoroutineScope,
    )

    return coroutineScope {
      val effectiveDocument = document!!

      // trigger opening of persistent maps in advance
      project.serviceAsync<Necropolis>()

      val highlighterDeferred = async(CoroutineName("editor highlighter creating")) {
        val scheme = serviceAsync<EditorColorsManager>().globalScheme
        val editorHighlighterFactory = serviceAsync<EditorHighlighterFactory>()
        // two separate read actions to avoid one long-running - https://youtrack.jetbrains.com/issue/IJPL-796
        val highlighter = readActionBlocking {
          editorHighlighterFactory.createEditorHighlighter(file = file, editorColorScheme = scheme, project = project)
        }
        // editor.setHighlighter also sets text, but we set it here to avoid executing related work in EDT
        // (the document text is compared, so, double work is not performed)
        highlighter.setText(effectiveDocument.immutableCharSequence)
        highlighter
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
      val highlighter = highlighterDeferred.await()

      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          val editor = factory.createMainEditor(
            document = effectiveDocument,
            project = project,
            file = file,
            highlighter = highlighter,
            afterCreation = {
              it.putUserData(AsyncEditorLoader.ASYNC_LOADER, asyncLoader)
            },
          )
          editorDeferred.complete(editor)
          editor.gutterComponentEx.setInitialIconAreaWidth(EditorGutterLayout.getInitialGutterWidth())
          val component = createPsiAwareTextEditorComponent(file = file, editor = editor)
          val textEditor = PsiAwareTextEditorImpl(project = project, file = file, component = component, asyncLoader = asyncLoader)
          asyncLoader.start(textEditor = textEditor, task = task)
          textEditor
        }
      }
    }
  }

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

      val necropolis = project.serviceAsync<Necropolis>()
      necropolis.spawnZombies(project, file, document, editorSupplier, highlighterReady)

      val editor = editorSupplier()
      span("editor languageSupplier set", Dispatchers.EDT) {
        editor.settings.setLanguageSupplier { TextEditorImpl.getDocumentLanguage(editor) }
      }
    }
  }

  override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState {
    val state = super<TextEditorProvider>.readState(element, project, file) as TextEditorState

    // foldings
    element.getChild(FOLDING_ELEMENT)?.let { foldingState ->
      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document == null) {
        state.setDelayedFoldState(PsiAwareTextEditorDelayedFoldingState(project = project, file = file, state = foldingState))
      }
      else {
        state.foldingState = CodeFoldingManager.getInstance(project).readFoldingState(foldingState, document)
      }
    }
    return state
  }

  override fun writeState(state: FileEditorState, project: Project, element: Element) {
    super<TextEditorProvider>.writeState(state = state, project = project, element = element)

    state as TextEditorState

    // foldings
    val foldingState = state.foldingState
    if (foldingState == null) {
      val delayedProducer = state.delayedFoldState
      if (delayedProducer is PsiAwareTextEditorDelayedFoldingState) {
        element.addContent(delayedProducer.cloneSerializedState())
      }
    }
    else {
      val e = Element(FOLDING_ELEMENT)
      try {
        CodeFoldingManager.getInstance(project).writeFoldingState(foldingState, e)
      }
      catch (ignored: WriteExternalException) {
      }
      if (!e.isEmpty) {
        element.addContent(e)
      }
    }
  }

  override fun getStateImpl(project: Project?, editor: Editor, level: FileEditorStateLevel): TextEditorState {
    val state = super.getStateImpl(project, editor, level)
    // Save folding only on FULL level. It's costly to commit a document on every type (caused by undo).
    if (FileEditorStateLevel.FULL == level) {
      // Folding
      if (project != null && !project.isDisposed && !editor.isDisposed && project.isInitialized) {
        state.foldingState = CodeFoldingManager.getInstance(project).saveFoldingState(editor)
      }
      else {
        state.foldingState = null
      }
    }
    return state
  }

  override fun setStateImpl(project: Project?, editor: Editor, state: TextEditorState, exactState: Boolean) {
    super.setStateImpl(project = project, editor = editor, state = state, exactState = exactState)

    // folding
    val foldState = state.foldingState
    // folding state is restored by PsiAwareTextEditorImpl.loadEditorInBackground, that's why here we check isEditorLoaded
    if (project != null && foldState != null && isEditorLoaded(editor)) {
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      if (!psiDocumentManager.isCommitted(editor.document)) {
        psiDocumentManager.commitDocument(editor.document)
        logger<PsiAwareTextEditorProvider>()
          .error("File should be parsed when changing editor state, otherwise UI might be frozen for a considerable time")
      }
      editor.foldingModel.runBatchFoldingOperation { CodeFoldingManager.getInstance(project).restoreFoldingState(editor, foldState) }
    }
  }

  override fun createWrapperForEditor(editor: Editor): EditorWrapper = PsiAwareEditorWrapper(editor)

  private inner class PsiAwareEditorWrapper(editor: Editor) : EditorWrapper(editor) {
    private val backgroundHighlighter = editor.project?.let { TextEditorBackgroundHighlighter(it, editor) }

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = backgroundHighlighter
  }
}

private class PsiAwareTextEditorDelayedFoldingState(private val project: Project,
                                                    private val file: VirtualFile,
                                                    private val state: Element) : Supplier<CodeFoldingState?> {
  override fun get(): CodeFoldingState? {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
    return CodeFoldingManager.getInstance(project).readFoldingState(state, document)
  }

  fun cloneSerializedState(): Element = state.clone()
}