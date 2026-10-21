// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

@TestApplication
internal class CodeReviewCommentNavigatorTest {
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  private val project get() = projectFixture.get()

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `comment in the current file moves the caret without opening an editor`(): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("current.txt", "first\nsecond\nthird\n")
    val editorManager = mockEditorManager()

    withEditor(file) { editor ->
      withContext(Dispatchers.EDT) {
        CodeReviewCommentNavigator(project).navigate(editor, changeFor(file), 2)
        assertEquals(2, editor.caretModel.logicalPosition.line)
      }
      verify(exactly = 0) { editorManager.requestOpenEditor(any(), any()) }
    }
  }

  @Test
  fun `comment in another file opens the file without focus`(): Unit = timeoutRunBlocking {
    val currentFile = LightVirtualFile("current.txt", "first\nsecond\nthird\n")
    val targetFile = LightVirtualFile("target.txt", "first\nsecond\nthird\n")
    val descriptor = slot<FileEditorNavigatable>()
    val request = slot<FileEditorOpenRequest>()
    mockEditorManager {
      every { requestOpenEditor(capture(descriptor), capture(request)) } returns CompletableFuture()
    }

    withEditor(currentFile) { editor ->
      withContext(Dispatchers.EDT) {
        CodeReviewCommentNavigator(project).navigate(editor, changeFor(targetFile), 2)
      }
      assertFalse(request.captured.requestFocus, "Opening the editor must not take focus from the thread.")
      assertTrue(request.captured.selectAsCurrent, "The target editor must become the current one.")
      assertSame(targetFile, descriptor.captured.file)
      assertEquals(2, (descriptor.captured as OpenFileDescriptor).line)
    }
  }

  private fun mockEditorManager(stubs: FileEditorManager.() -> Unit = {}): FileEditorManager {
    val editorManager = mockk<FileEditorManager>(relaxed = true) {
      every { runWhenLoaded(any(), any()) } answers { secondArg<Runnable>().run() }
      stubs()
    }
    project.replaceService(FileEditorManager::class.java, editorManager, disposableFixture.get())
    return editorManager
  }

  private fun changeFor(file: VirtualFile): RefComparisonChange = mockk {
    every { filePathAfter } returns mockk {
      every { virtualFile } returns file
    }
  }

  private suspend fun withEditor(file: VirtualFile, action: suspend (Editor) -> Unit) {
    val editor = withContext(Dispatchers.EDT) {
      val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
      EditorFactory.getInstance().createEditor(document)
    }
    try {
      action(editor)
    }
    finally {
      withContext(Dispatchers.EDT) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }
}
