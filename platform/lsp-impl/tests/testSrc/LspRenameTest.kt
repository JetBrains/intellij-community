// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspRenameSupport
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiFile
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@TestApplication
internal class LspRenameTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private suspend fun triggerRename() {
    withContext(Dispatchers.EDT) {
      codeInsightFixture.testAction(RenameElementAction())
    }
  }

  @AfterEach
  fun waitForAsyncTaskCompletion() {
    // Wait for all pending non-blocking read actions and their EDT continuations (e.g., write actions
    // scheduled by LspDocumentListener) to complete before fixture teardown, so that background tasks
    // don't race with resource cleanup and tests can finish gracefully.
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    }
  }

  @Nested
  inner class PrepareRename {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = object : LspCustomization() {
        override val renameCustomizer = object : LspRenameSupport() {
          override fun shouldRunRename(psiFile: PsiFile): Boolean = true
        }
      },
      configureServerCapabilities = {
        renameProvider = Either.forRight(RenameOptions().apply {
          prepareProvider = true
        })
      },
    )

    @Test
    fun `prepareRename returns range and placeholder`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forSecond(PrepareRenameResult(Range(Position(0, 0), Position(0, 5)), "hello"))
      }

      // when
      triggerRename()

      // then
      serverSession.awaitExpected()
      withContext(Dispatchers.EDT) {
        val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
        assertNotNull(templateState, "Template should be active")

        val selectionModel = codeInsightFixture.editor.selectionModel
        assertEquals(0, selectionModel.selectionStart, "Selection should start at position 0")
        assertEquals(5, selectionModel.selectionEnd, "Selection should end at position 5")
        assertEquals("hello", selectionModel.selectedText, "Selected text should be 'hello'")
      }
    }

    @Test
    fun `prepareRename with range only uses document text`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forFirst(Range(Position(0, 0), Position(0, 5)))
      }

      // when
      triggerRename()

      // then
      serverSession.awaitExpected()
      withContext(Dispatchers.EDT) {
        val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
        assertNotNull(templateState, "Template should be active")

        val selectionModel = codeInsightFixture.editor.selectionModel
        assertEquals(0, selectionModel.selectionStart, "Selection should start at position 0")
        assertEquals(5, selectionModel.selectionEnd, "Selection should end at position 5")
        assertEquals("hello", selectionModel.selectedText, "Selected text should be 'hello'")
      }
    }

    @Test
    fun `prepareRename with defaultBehavior falls back to word range`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forThird(PrepareRenameDefaultBehavior(true))
      }

      // when
      triggerRename()

      // then
      serverSession.awaitExpected()
      withContext(Dispatchers.EDT) {
        val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
        assertNotNull(templateState, "Template should be active")

        val selectionModel = codeInsightFixture.editor.selectionModel
        assertEquals(0, selectionModel.selectionStart, "Selection should start at position 0")
        assertEquals(5, selectionModel.selectionEnd, "Selection should end at position 5")
        assertEquals("hello", selectionModel.selectedText, "Selected text should be 'hello'")
      }
    }
  }

  @Nested
  inner class PrepareRenameDisabled {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = object : LspCustomization() {
        override val renameCustomizer = object : LspRenameSupport() {
          override fun shouldRunRename(psiFile: PsiFile): Boolean = true
        }
      },
      configureServerCapabilities = {
        renameProvider = Either.forRight(RenameOptions().apply {
          prepareProvider = false
        })
      },
    )

    @Test
    fun `prepareProvider disabled falls back to word range`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      configureServerSession(project, virtualFile)

      // when
      triggerRename()

      // then
      withContext(Dispatchers.EDT) {
        val templateState = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
        assertNotNull(templateState, "Template should be active")

        val selectionModel = codeInsightFixture.editor.selectionModel
        assertEquals(0, selectionModel.selectionStart, "Selection should start at position 0")
        assertEquals(5, selectionModel.selectionEnd, "Selection should end at position 5")
        assertEquals("hello", selectionModel.selectedText, "Selected text should be 'hello'")
      }
    }
  }

  @Nested
  inner class PerformRename {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = object : LspCustomization() {
        override val renameCustomizer = object : LspRenameSupport() {
          override fun shouldRunRename(psiFile: PsiFile): Boolean = true
        }
      },
      configureServerCapabilities = {
        renameProvider = Either.forRight(RenameOptions().apply {
          prepareProvider = true
        })
      },
    )

    @Test
    fun `rename applies workspace edit from server`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forSecond(PrepareRenameResult(Range(Position(0, 0), Position(0, 5)), "hello"))
      }

      serverSession.expectRequest(serverSession.RENAME, {
        it.textDocument.uri == fileUri && it.newName == "greetings"
      }) {
        WorkspaceEdit(mapOf(fileUri to listOf(TextEdit(Range(Position(0, 0), Position(0, 5)), "greetings"))))
      }

      // when
      triggerRename()
      codeInsightFixture.type("greetings\n")

      // then
      serverSession.awaitExpected()
      assertEquals("greetings world", codeInsightFixture.editor.document.text)
    }

    @Test
    fun `undoing rename template restores original document and does not send server request`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forSecond(PrepareRenameResult(Range(Position(0, 0), Position(0, 5)), "hello"))
      }

      // when
      triggerRename()
      codeInsightFixture.type("greetings")
      codeInsightFixture.performEditorAction(IdeActions.ACTION_UNDO)

      // then
      serverSession.awaitExpected()
      assertEquals("hello world", codeInsightFixture.editor.document.text)
    }

    @Test
    fun `aborting rename does not send server request`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forSecond(PrepareRenameResult(Range(Position(0, 0), Position(0, 5)), "hello"))
      }

      // when
      triggerRename()
      val state = TemplateManagerImpl.getTemplateState(codeInsightFixture.editor)
      codeInsightFixture.type("greetings")
      WriteCommandAction.runWriteCommandAction(project) { state?.gotoEnd(true) }

      // then
      serverSession.awaitExpected()
      assertEquals("hello world", codeInsightFixture.editor.document.text)
    }

    @Test
    fun `rename applies edits on multiple lines`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val text = """
        foo bar
        foo baz
        foo qux
        """.trimIndent()

      val virtualFile = codeInsightFixture.configureByText("test.txt", text).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forSecond(PrepareRenameResult(Range(Position(0, 0), Position(0, 3)), "foo"))
      }

      serverSession.expectRequest(serverSession.RENAME, {
        it.textDocument.uri == fileUri && it.newName == "renamed"
      }) {
        WorkspaceEdit(mapOf(fileUri to listOf(
          TextEdit(Range(Position(0, 0), Position(0, 3)), "renamed"),
          TextEdit(Range(Position(1, 0), Position(1, 3)), "renamed"),
          TextEdit(Range(Position(2, 0), Position(2, 3)), "renamed")
        )))
      }

      // when
      triggerRename()
      codeInsightFixture.type("renamed\n")

      // then
      serverSession.awaitExpected()
      val expectedText = """
        renamed bar
        renamed baz
        renamed qux
        """.trimIndent()
      assertEquals(expectedText, codeInsightFixture.editor.document.text)
    }

    @Test
    fun `rename XML tag via LSP when native PSI rename is also available`() = timeoutRunBlocking {
      // given
      TemplateManagerImpl.setTemplateTesting(project)

      val virtualFile = codeInsightFixture.configureByText("test.xml", "<tag>text</tag>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.PREPARE_RENAME, { it.textDocument.uri == fileUri }) {
        Either3.forSecond(PrepareRenameResult(Range(Position(0, 1), Position(0, 4)), "tag"))
      }

      serverSession.expectRequest(serverSession.RENAME, {
        it.textDocument.uri == fileUri && it.newName == "newtag"
      }) {
        WorkspaceEdit(mapOf(fileUri to listOf(
          TextEdit(Range(Position(0, 1), Position(0, 4)), "newtag"),
          TextEdit(Range(Position(0, 11), Position(0, 14)), "newtag"),
        )))
      }

      // when
      triggerRename()
      codeInsightFixture.type("newtag\n")

      // then
      serverSession.awaitExpected()
      assertEquals("<newtag>text</newtag>", codeInsightFixture.editor.document.text)
    }
  }
}
