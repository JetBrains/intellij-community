// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class LspApplyEditTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture()

  @Test
  fun `incorrect uri - edit is not applied`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    val response = serverSession.sendRequest(serverSession.APPLY_EDIT) {
      ApplyWorkspaceEditParams(WorkspaceEdit(mapOf(
        "file:///nonexistent/path/to/file.txt" to listOf(TextEdit(Range(Position(0, 0), Position(0, 5)), "goodbye"))
      )))
    }

    assertFalse(response.isApplied)
    assertEquals("hello world", codeInsightFixture.editor.document.text)
  }

  @Test
  fun `apply edit to a single file`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "12345").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    val response = serverSession.sendRequest(serverSession.APPLY_EDIT) {
      ApplyWorkspaceEditParams(WorkspaceEdit(mapOf(
        serverSession.fileUri(virtualFile) to listOf(TextEdit(Range(Position(0, 1), Position(0, 2)), "ABC"))
      )))
    }

    assertTrue(response.isApplied)
    assertEquals("1ABC345", codeInsightFixture.editor.document.text)
  }

  @Test
  fun `apply edit to two files`() = timeoutRunBlocking {
    val file1 = codeInsightFixture.configureByText("file1.txt", "first file").virtualFile
    val file2 = codeInsightFixture.addFileToProject("file2.txt", "second file").virtualFile
    val serverSession = configureServerSession(project, file1)

    val response = serverSession.sendRequest(serverSession.APPLY_EDIT) {
      ApplyWorkspaceEditParams(WorkspaceEdit(mapOf(
        serverSession.fileUri(file1) to listOf(TextEdit(Range(Position(0, 0), Position(0, 5)), "FIRST")),
        serverSession.fileUri(file2) to listOf(TextEdit(Range(Position(0, 0), Position(0, 6)), "SECOND")),
      )))
    }

    assertTrue(response.isApplied)
    readAction {
      assertEquals("FIRST file", FileDocumentManager.getInstance().getDocument(file1)?.text)
      assertEquals("SECOND file", FileDocumentManager.getInstance().getDocument(file2)?.text)
    }
  }

  @Test
  fun `apply edit to two files using documentChanges`() = timeoutRunBlocking {
    val file1 = codeInsightFixture.configureByText("file1.txt", "first file").virtualFile
    val file2 = codeInsightFixture.addFileToProject("file2.txt", "second file").virtualFile
    val serverSession = configureServerSession(project, file1)

    val response = serverSession.sendRequest(serverSession.APPLY_EDIT) {
      ApplyWorkspaceEditParams(WorkspaceEdit(listOf(
        Either.forLeft(TextDocumentEdit(
          VersionedTextDocumentIdentifier(serverSession.fileUri(file1), null),
          listOf(TextEdit(Range(Position(0, 0), Position(0, 5)), "FIRST")),
        )),
        Either.forLeft(TextDocumentEdit(
          VersionedTextDocumentIdentifier(serverSession.fileUri(file2), null),
          listOf(TextEdit(Range(Position(0, 0), Position(0, 6)), "SECOND")),
        )),
      )))
    }

    assertTrue(response.isApplied)
    readAction {
      assertEquals("FIRST file", FileDocumentManager.getInstance().getDocument(file1)?.text)
      assertEquals("SECOND file", FileDocumentManager.getInstance().getDocument(file2)?.text)
    }
  }
}
