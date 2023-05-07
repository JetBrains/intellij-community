// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.SlowOperations
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

private const val FOLDING_ELEMENT: @NonNls String = "folding"

open class PsiAwareTextEditorProvider : TextEditorProvider(), AsyncFileEditorProvider {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    SlowOperations.knownIssue("IDEA-307300, EA-680898").use { return PsiAwareTextEditorImpl(project, file, this) }
  }

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val document = ApplicationManager.getApplication().runReadAction<Document?, RuntimeException> {
      ProjectLocator.computeWithPreferredProject<Document?, RuntimeException>(file, project) {
        FileDocumentManager.getInstance().getDocument(file)
      }
    }!!
    val factory = EditorFactory.getInstance() as EditorFactoryImpl
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        val editor = factory.createMainEditor(document, project, file)
        return PsiAwareTextEditorImpl(project, file, this@PsiAwareTextEditorProvider, editor)
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
    super.setStateImpl(project, editor, state, exactState)

    // folding
    val foldState = state.foldingState
    // folding state is restored by PsiAwareTextEditorImpl.loadEditorInBackground, that's why here we check isEditorLoaded
    if (project != null && foldState != null && isEditorLoaded(editor)) {
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      if (!psiDocumentManager.isCommitted(editor.document)) {
        psiDocumentManager.commitDocument(editor.document)
        LOG.error("File should be parsed when changing editor state, otherwise UI might be frozen for a considerable time")
      }
      editor.foldingModel.runBatchFoldingOperation { CodeFoldingManager.getInstance(project).restoreFoldingState(editor, foldState) }
    }
  }

  override fun createWrapperForEditor(editor: Editor): EditorWrapper = PsiAwareEditorWrapper(editor)

  private inner class PsiAwareEditorWrapper(editor: Editor) : EditorWrapper(editor) {
    private val backgroundHighlighter: TextEditorBackgroundHighlighter?

    init {
      val project = editor.project
      backgroundHighlighter = project?.let { TextEditorBackgroundHighlighter(it, editor) }
    }

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = backgroundHighlighter

    override fun isValid(): Boolean = !editor.isDisposed
  }
}

private class MyDelayedFoldingState(private val project: Project,
                                    private val file: VirtualFile,
                                    state: Element) : Supplier<CodeFoldingState?> {
  private val _serializedState: Element

  init {
    _serializedState = JDOMUtil.internElement(state)
  }

  override fun get(): CodeFoldingState? {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
    return CodeFoldingManager.getInstance(project).readFoldingState(_serializedState, document)
  }

  val serializedState: Element
    get() = _serializedState.clone()
}

