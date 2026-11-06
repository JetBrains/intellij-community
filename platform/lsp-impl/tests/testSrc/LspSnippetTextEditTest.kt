// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SnippetTextEdit
import org.eclipse.lsp4j.StringValue
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class LspSnippetTextEditTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    configureServerCapabilities = {
      codeActionProvider = Either.forLeft(true)
    }
  )

  private val disposable by disposableFixture()

  @Test
  fun `snippet edit in the active editor starts a live template`() = timeoutRunBlocking {
    TemplateManagerImpl.setTemplateTesting(disposable)
    val virtualFile = codeInsightFixture.configureByText("test.txt", "fun foo() {}").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)

    applyEdits(serverSession, virtualFile, snippetEdit(Range(Position(0, 4), Position(0, 9)), $$"${1:bar}($2)$0"))

    withContext(Dispatchers.EDT) {
      val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
      assertNotNull(templateState)
      assertEquals("fun bar() {}", codeInsightFixture.editor.document.text)
      assertEquals(TextRange(4, 7), templateState!!.currentVariableRange)

      codeInsightFixture.type("baz")
      nextTab(templateState)
      codeInsightFixture.type("x")
      nextTab(templateState)
      assertTrue(templateState.isFinished)
      codeInsightFixture.checkResult("fun baz(x)<caret> {}")
    }
  }

  @Test
  fun `snippet edit mixed with text edits`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "a b c").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)

    applyEdits(
      serverSession, virtualFile,
      Either.forLeft(TextEdit(Range(Position(0, 4), Position(0, 5)), "CCC")),
      snippetEdit(Range(Position(0, 2), Position(0, 3)), $$"${1:B}$0!"),
      Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 1)), "AAA")),
    )

    // The live template is not interactive in tests, so it finishes at once, and the caret goes to `$0`
    codeInsightFixture.checkResult("AAA B<caret>! CCC")
  }

  @Test
  fun `only the first snippet edit in the active editor goes live`() = timeoutRunBlocking {
    TemplateManagerImpl.setTemplateTesting(disposable)
    val virtualFile = codeInsightFixture.configureByText("test.txt", "one two").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)

    applyEdits(
      serverSession, virtualFile,
      snippetEdit(Range(Position(0, 0), Position(0, 3)), $$"${1:1}"),
      snippetEdit(Range(Position(0, 4), Position(0, 7)), $$"${1:2}"),
    )

    withContext(Dispatchers.EDT) {
      assertEquals("1 2", codeInsightFixture.editor.document.text)
      val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
      assertNotNull(templateState)
      assertEquals(TextRange(0, 1), templateState!!.currentVariableRange)
      nextTab(templateState)
      assertTrue(templateState.isFinished)
    }
  }

  @Test
  fun `snippet edit of a document that is not in the active editor is applied as plain text`() = timeoutRunBlocking {
    TemplateManagerImpl.setTemplateTesting(disposable)
    val activeFile = codeInsightFixture.configureByText("active.txt", "active").virtualFile
    val otherFile = codeInsightFixture.addFileToProject("other.txt", "other").virtualFile
    val serverSession = fakeLspIntegration.serverSession(activeFile)

    applyEdits(serverSession, otherFile, snippetEdit(Range(Position(0, 0), Position(0, 5)), $$"${1:first} ${2|one,two|} $3$1$0!"))

    readAction {
      assertEquals("first one first!", FileDocumentManager.getInstance().getDocument(otherFile)?.text)
    }
    withContext(Dispatchers.EDT) {
      assertNull(TemplateManagerImpl.getTemplateState(codeInsightFixture.editor))
      codeInsightFixture.checkResult("active")
    }
  }

  @Test
  fun `a later change of the same document keeps the live snippet in place`() = timeoutRunBlocking {
    TemplateManagerImpl.setTemplateTesting(disposable)
    val virtualFile = codeInsightFixture.configureByText("test.txt", "a b").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    val response = serverSession.sendRequest(serverSession.APPLY_EDIT) {
      ApplyWorkspaceEditParams(WorkspaceEdit(listOf(
        Either.forLeft(TextDocumentEdit(VersionedTextDocumentIdentifier(fileUri, null),
                                        listOf(snippetEdit(Range(Position(0, 2), Position(0, 3)), $$"${1:bee}")))),
        Either.forLeft(TextDocumentEdit(VersionedTextDocumentIdentifier(fileUri, null),
                                        listOf(Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 1)), "alpha"))))),
      )))
    }

    assertTrue(response.isApplied)
    withContext(Dispatchers.EDT) {
      assertEquals("alpha bee", codeInsightFixture.editor.document.text)
      assertEquals(TextRange(6, 9), TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)?.currentVariableRange)
    }
  }

  @Test
  fun `snippet edit without tab stops puts the caret at its end mark`() = timeoutRunBlocking {
    TemplateManagerImpl.setTemplateTesting(disposable)
    val virtualFile = codeInsightFixture.configureByText("test.txt", "x").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)

    applyEdits(serverSession, virtualFile, snippetEdit(Range(Position(0, 0), Position(0, 1)), $$"foo($0)"))

    withContext(Dispatchers.EDT) {
      assertNull(TemplateManagerImpl.getTemplateState(codeInsightFixture.editor))
      codeInsightFixture.checkResult("foo(<caret>)")
    }
  }

  @Test
  fun `multi-line snippet edit keeps the indentation from the server`() = timeoutRunBlocking {
    TemplateManagerImpl.setTemplateTesting(disposable)
    val virtualFile = codeInsightFixture.configureByText("test.txt", "fun f() {\n    x\n}").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)

    applyEdits(serverSession, virtualFile,
               snippetEdit(Range(Position(1, 4), Position(1, 5)), $$"if (${1:cond}) {\r\n        $0\r\n    }"))

    withContext(Dispatchers.EDT) {
      val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
      assertNotNull(templateState)
      assertEquals("fun f() {\n    if (cond) {\n        \n    }\n}", codeInsightFixture.editor.document.text)
      nextTab(templateState!!)
      assertTrue(templateState.isFinished)
      codeInsightFixture.checkResult("fun f() {\n    if (cond) {\n        <caret>\n    }\n}")
    }
  }

  @Test
  fun `one undo reverts the text edits and the live snippet`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "a b c").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)

    applyEdits(
      serverSession, virtualFile,
      Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 1)), "AAA")),
      snippetEdit(Range(Position(0, 2), Position(0, 3)), $$"${1:B}$0!"),
    )

    withContext(Dispatchers.EDT) {
      assertEquals("AAA B! c", codeInsightFixture.editor.document.text)
      codeInsightFixture.performEditorAction(IdeActions.ACTION_UNDO)
      assertEquals("a b c", codeInsightFixture.editor.document.text)
    }
  }

  @Test
  fun `code action with a snippet edit`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "x").virtualFile
    awaitFileOpenedByLspServer(project, virtualFile)
    val serverSession = fakeLspIntegration.serverSession(virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
      listOf(
        Either.forRight(CodeAction().apply {
          title = "Insert snippet"
          edit = WorkspaceEdit(listOf(
            Either.forLeft(TextDocumentEdit(
              VersionedTextDocumentIdentifier(fileUri, null),
              listOf(snippetEdit(Range(Position(0, 0), Position(0, 1)), $$"foo(${1:arg})$0;")),
            ))
          ))
        })
      )
    }

    withContext(Dispatchers.EDT) {
      val intention = codeInsightFixture.findSingleIntention("Insert snippet")
      assertEquals("foo(arg);", codeInsightFixture.getIntentionPreviewText(intention))
      codeInsightFixture.launchAction(intention)
    }
    serverSession.awaitExpected()
    // The live template is not interactive in tests, so it finishes at once, and the caret goes to `$0`
    codeInsightFixture.checkResult("foo(arg)<caret>;")
  }

  private suspend fun applyEdits(serverSession: FakeLspServerSession, file: VirtualFile, vararg edits: Either<TextEdit, SnippetTextEdit>) {
    val response = serverSession.sendRequest(serverSession.APPLY_EDIT) {
      ApplyWorkspaceEditParams(WorkspaceEdit(listOf(
        Either.forLeft(TextDocumentEdit(VersionedTextDocumentIdentifier(serverSession.fileUri(file), null), edits.toList()))
      )))
    }
    assertTrue(response.isApplied)
  }

  private fun nextTab(templateState: TemplateState) =
    CommandProcessor.getInstance().executeCommand(project, { templateState.nextTab() }, null, null)

  private fun snippetEdit(range: Range, snippet: String): Either<TextEdit, SnippetTextEdit> =
    Either.forRight(SnippetTextEdit(range, StringValue("snippet", snippet)))
}
