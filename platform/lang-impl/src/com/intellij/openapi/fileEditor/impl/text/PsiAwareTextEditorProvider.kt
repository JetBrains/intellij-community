// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl.Companion.createAsyncEditorLoader
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import java.util.function.Supplier

private const val FOLDING_ELEMENT: @NonNls String = "folding"
private val EDITOR_LOADER_EP = ExtensionPointName<TextEditorInitializer>("com.intellij.textEditorInitializer")

open class PsiAwareTextEditorProvider : TextEditorProvider(), AsyncFileEditorProvider {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return PsiAwareTextEditorImpl(project = project, file = file, provider = this)
  }

  override suspend fun createEditorBuilder(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val asyncLoader = createAsyncEditorLoader(provider = this, project = project)

    val fileDocumentManager = FileDocumentManager.getInstance()
    val document = fileDocumentManager.getCachedDocument(file) ?: readAction {
      fileDocumentManager.getDocument(file, project)!!
    }

    val editorDeferred = CompletableDeferred<EditorEx>()
    val editorSupplier = suspend { editorDeferred.await() }

    val factory = EditorFactory.getInstance() as EditorFactoryImpl
    val task = asyncLoader.coroutineScope.async(CoroutineName("TextEditorInitializer")) {
      coroutineScope {
        for (item in EDITOR_LOADER_EP.filterableLazySequence()) {
          if (item.pluginDescriptor.pluginId != PluginManagerCore.CORE_ID) {
            thisLogger().error("Only core plugin can define ${EDITOR_LOADER_EP.name}: ${item.pluginDescriptor}")
            continue
          }

          val initializer = item.instance ?: continue
          launch(CoroutineName(item.implementationClassName)) {
            catchingExceptionsAsync {
              initializer.initializeEditor(project = project, file = file, document = document, editorSupplier = editorSupplier)
            }
          }
        }
      }

      val psiManager = project.serviceAsync<PsiManager>()
      val daemonCodeAnalyzer = project.serviceAsync<DaemonCodeAnalyzer>()
      span("DaemonCodeAnalyzer.restart") {
        readActionBlocking {
          daemonCodeAnalyzer.restart(psiManager.findFile(file) ?: return@readActionBlocking)
        }
      }
    }

    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        val editor = factory.createMainEditor(document, project, file)
        val textEditor = PsiAwareTextEditorImpl(project = project, file = file, editor = editor, asyncLoader = asyncLoader)
        editorDeferred.complete(textEditor.editor)
        asyncLoader.start(textEditor = textEditor, tasks = listOf(task))
        return textEditor
      }
    }
  }

  override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState {
    val state = super<TextEditorProvider>.readState(element, project, file) as TextEditorState

    // foldings
    val child = element.getChild(FOLDING_ELEMENT)
    if (child != null) {
      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document == null) {
        state.setDelayedFoldState(MyDelayedFoldingState(project, file, child))
      }
      else {
        state.foldingState = CodeFoldingManager.getInstance(project).readFoldingState(child, document)
      }
    }
    return state
  }

  override fun writeState(state: FileEditorState, project: Project, element: Element) {
    super<TextEditorProvider>.writeState(state, project, element)

    state as TextEditorState

    // foldings
    val foldingState = state.foldingState
    if (foldingState == null) {
      val delayedProducer = state.delayedFoldState
      if (delayedProducer is MyDelayedFoldingState) {
        element.addContent(delayedProducer.serializedState)
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
    // Save folding only on FULL level. It's very expensive to commit a document on every type (caused by undo).
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

private class MyDelayedFoldingState(private val project: Project,
                                    private val file: VirtualFile,
                                    state: Element) : Supplier<CodeFoldingState?> {
  private val _serializedState: Element = JDOMUtil.internElement(state)

  override fun get(): CodeFoldingState? {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
    return CodeFoldingManager.getInstance(project).readFoldingState(_serializedState, document)
  }

  val serializedState: Element
    get() = _serializedState.clone()
}

private inline fun <T : Any> catchingExceptionsAsync(computable: () -> T?): T? {
  try {
    return computable()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logger<AsyncFileEditorProvider>().warn("Exception during editor loading", if (e is ControlFlowException) RuntimeException(e) else e)
    return null
  }
}