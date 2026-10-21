// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.fileEditor.impl.text.createAsyncEditorLoader
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.Test

@TestApplication
class DocumentFoldingInfoTest {
  private companion object {
    val projectFixture = projectFixture(openAfterCreation = true)
    const val TEXT = "class Sample {\n  void method() {\n    System.out.println(1);\n    System.out.println(2);\n  }\n}\n"
    val methodStart = TEXT.indexOf('{', TEXT.indexOf("void method"))
  }

  private val manager: CodeFoldingManager get() = CodeFoldingManager.getInstance(projectFixture.get())

  @Test
  fun `restore a state saved before folding initialization into another document`(): Unit = timeoutRunBlocking {
    val state = withEditor { editor ->
      withContext(Dispatchers.EDT) { manager.saveFoldingState(editor) }
    }
    withEditor { editor ->
      initializeFoldings(editor)
      withContext(Dispatchers.EDT) {
        manager.restoreFoldingState(editor, state)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    }
  }

  @Test
  fun `restore pending signatures before folding initialization`(): Unit = timeoutRunBlocking {
    val state = collapsedState()
    withEditor { editor ->
      val pending = readAction { manager.readFoldingState(state, editor.document) }
      withContext(Dispatchers.EDT) {
        manager.restoreFoldingState(editor, pending)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        assertThat(methodRegion(editor).isExpanded).isFalse()
      }
    }
  }

  @Test
  fun `initializing another editor preserves pending initialization`(): Unit = timeoutRunBlocking {
    val state = collapsedState()
    withEditor { firstEditor ->
      readAction { manager.readFoldingState(state, firstEditor.document) }
      withEditor(firstEditor.document) { secondEditor ->
        val firstUpdate = readAction { manager.updateFoldRegionsAsync(firstEditor, true) }
        val secondUpdate = readAction { manager.updateFoldRegionsAsync(secondEditor, true) }
        withContext(Dispatchers.EDT) {
          requireNotNull(firstUpdate).run()
          requireNotNull(secondUpdate).run()
          assertThat(methodRegion(firstEditor).isExpanded).isFalse()
          assertThat(methodRegion(secondEditor).isExpanded).isFalse()
        }
      }
    }
  }

  @Test
  fun `reading another state preserves pending initialization`(): Unit = timeoutRunBlocking {
    val state = collapsedState()
    withEditor { editor ->
      readAction { manager.readFoldingState(state, editor.document) }
      val update = readAction { manager.updateFoldRegionsAsync(editor, true) }
      readAction { manager.readFoldingState(Element("folding"), editor.document) }
      withContext(Dispatchers.EDT) {
        requireNotNull(update).run()
        assertThat(methodRegion(editor).isExpanded).isFalse()
      }
    }
  }

  @Test
  fun `restore a snapshot while the text editor is loading`(): Unit = timeoutRunBlocking {
    val state = withEditor { editor ->
      initializeFoldings(editor)
      withContext(Dispatchers.EDT) { saveState(editor, expanded = false) }
    }
    val editorScope = this
    withEditor { editor ->
      withContext(Dispatchers.EDT) {
        val provider = PsiAwareTextEditorProvider()
        val file = requireNotNull((editor as EditorEx).virtualFile)
        val loader = createAsyncEditorLoader(provider, projectFixture.get(), file, editorScope)
        editor.putUserData(AsyncEditorLoader.ASYNC_LOADER, loader)
        try {
          assertThat(AsyncEditorLoader.isEditorLoaded(editor)).isFalse()
          provider.setStateImpl(projectFixture.get(), editor, TextEditorState().withFoldingState(state), false)
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
          assertThat(methodRegion(editor).isExpanded).isFalse()
        }
        finally {
          editor.putUserData(AsyncEditorLoader.ASYNC_LOADER, null)
          loader.coroutineScope.cancel()
        }
      }
    }
  }

  @Test
  fun `restore pending signatures into initialized folds`(): Unit = timeoutRunBlocking {
    val state = collapsedState()
    withEditor { editor ->
      initializeFoldings(editor)
      val pending = readAction { manager.readFoldingState(state, editor.document) }
      withContext(Dispatchers.EDT) {
        assertThat(methodRegion(editor).isExpanded).isTrue()
        manager.restoreFoldingState(editor, pending)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        assertThat(methodRegion(editor).isExpanded).isFalse()
      }
    }
  }

  @Test
  fun `saving another state preserves the first snapshot`(): Unit = timeoutRunBlocking {
    withEditor { editor ->
      initializeFoldings(editor)
      withContext(Dispatchers.EDT) {
        val collapsed = saveState(editor, expanded = false)
        saveState(editor, expanded = true)
        manager.restoreFoldingState(editor, collapsed)
        assertThat(methodRegion(editor).isExpanded).isFalse()
      }
    }
  }

  @Test
  fun `restore the same snapshot twice`(): Unit = timeoutRunBlocking {
    withEditor { editor ->
      initializeFoldings(editor)
      withContext(Dispatchers.EDT) {
        val collapsed = saveState(editor, expanded = false)
        setExpanded(editor, true)
        manager.restoreFoldingState(editor, collapsed)
        assertThat(methodRegion(editor).isExpanded).isFalse()
        setExpanded(editor, true)
        manager.restoreFoldingState(editor, collapsed)
        assertThat(methodRegion(editor).isExpanded).isFalse()
      }
    }
  }

  @Test
  fun `restore a snapshot after the document changes`(): Unit = timeoutRunBlocking {
    withEditor { editor ->
      initializeFoldings(editor)
      withContext(Dispatchers.EDT) {
        val region = methodRegion(editor)
        val collapsed = saveState(editor, expanded = false)
        setExpanded(editor, true)
        edtWriteAction {
          CommandProcessor.getInstance().runUndoTransparentAction {
            editor.document.insertString(0, "// Header\n")
          }
          PsiDocumentManager.getInstance(projectFixture.get()).commitDocument(editor.document)
        }
        assertThat(region.startOffset).isEqualTo(methodStart + "// Header\n".length)
        manager.restoreFoldingState(editor, collapsed)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        assertThat(region.isExpanded).isFalse()
      }
    }
  }

  @Test
  fun `a newer snapshot supersedes pending restoration`(): Unit = timeoutRunBlocking {
    val state = collapsedState()
    withEditor { editor ->
      initializeFoldings(editor)
      val pending = readAction { manager.readFoldingState(state, editor.document) }
      withContext(Dispatchers.EDT) {
        val expanded = saveState(editor, expanded = true)
        manager.restoreFoldingState(editor, pending)
        manager.restoreFoldingState(editor, expanded)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        assertThat(methodRegion(editor).isExpanded).isTrue()
      }
    }
  }

  @Test
  fun `closing the editor cancels pending restoration`(): Unit = timeoutRunBlocking {
    val state = collapsedState()
    withEditor { editor ->
      initializeFoldings(editor)
      val pending = readAction { manager.readFoldingState(state, editor.document) }
      withContext(Dispatchers.EDT) {
        manager.restoreFoldingState(editor, pending)
        manager.releaseFoldings(editor)
        EditorFactory.getInstance().releaseEditor(editor)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    }
  }

  private suspend fun collapsedState(): Element = withEditor { editor ->
    initializeFoldings(editor)
    withContext(Dispatchers.EDT) {
      val state = saveState(editor, expanded = false)
      Element("folding").also {
        manager.writeFoldingState(state, it)
        assertThat(it.getChildren("element")).isNotEmpty()
      }
    }
  }

  private fun saveState(editor: Editor, expanded: Boolean): CodeFoldingState {
    setExpanded(editor, expanded)
    return manager.saveFoldingState(editor)
  }

  private fun setExpanded(editor: Editor, expanded: Boolean) {
    editor.foldingModel.runBatchFoldingOperation { methodRegion(editor).isExpanded = expanded }
  }

  private fun methodRegion(editor: Editor): FoldRegion =
    editor.foldingModel.allFoldRegions.first { it.startOffset == methodStart }

  private suspend fun initializeFoldings(editor: Editor) {
    val update = readAction { manager.updateFoldRegionsAsync(editor, true) }
    withContext(Dispatchers.EDT) {
      requireNotNull(update).run()
    }
  }

  private suspend fun <T> withEditor(existingDocument: Document? = null, action: suspend (Editor) -> T): T {
    val editor = withContext(Dispatchers.EDT) {
      val project = projectFixture.get()
      val document = existingDocument ?: run {
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("java")
        val file = PsiFileFactory.getInstance(project).createFileFromText("Sample.java", fileType, TEXT, 0, true)
        requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
      }
      val file = requireNotNull(FileDocumentManager.getInstance().getFile(document))
      (EditorFactory.getInstance().createEditor(document, project, file, false) as EditorEx).also {
        it.setFile(file)
      }
    }
    try {
      return action(editor)
    }
    finally {
      withContext(Dispatchers.EDT) {
        if (!editor.isDisposed) {
          manager.releaseFoldings(editor)
          EditorFactory.getInstance().releaseEditor(editor)
        }
      }
    }
  }
}
