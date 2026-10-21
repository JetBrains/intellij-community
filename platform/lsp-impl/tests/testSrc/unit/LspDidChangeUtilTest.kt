// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.unit

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.documentSync.LspDidChangeUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.io.path.writeText

@TestApplication
internal class LspDidChangeUtilTest {
  private val tempDir by tempPathFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  @TestFor(issues = ["CPP-52687", "IDEA-394234", "IDEA-394276"])
  fun `a change in an in-memory file is not handled`(): Unit = timeoutRunBlocking {
    val file = LightVirtualFile("preview.txt", "hello")
    assertNull(fileToHandleAfterChange(file))
  }

  @Test
  fun `a change in a local file is handled`(): Unit = timeoutRunBlocking {
    val path = tempDir.resolve("local.txt").also { it.writeText("hello") }
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    assertNotNull(file)
    assertEquals(file, fileToHandleAfterChange(file))
  }

  private suspend fun fileToHandleAfterChange(file: VirtualFile): VirtualFile? {
    val document = readAction { FileDocumentManager.getInstance().getDocument(file) }
    assertNotNull(document)

    var fileToHandle: VirtualFile? = null
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        fileToHandle = LspDidChangeUtil.getFileToHandle(event)
      }
    }, disposable)

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(null) { document.insertString(0, "x") }
      FileDocumentManager.getInstance().saveDocument(document)
    }

    return fileToHandle
  }
}
