package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.features.highlighting.LspDiagnosticAndLazyQuickFixes
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CodeActionRegistrationOptions
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class LspCodeActionsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Nested
  inner class StaticRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        val codeActionOptions = CodeActionOptions().apply {
          resolveProvider = true
        }
        codeActionProvider = Either.forRight(codeActionOptions)
      }
    )

    @Test
    fun `intention action with multiple edits at the same position`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "1\n2\n3\n4").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(
          Either.forRight(CodeAction().apply {
            title = "Multiple edits"
            data = "resolve-token"
          })
        )
      }

      serverSession.expectRequest(serverSession.CODE_ACTION_RESOLVE, { it.data?.toString() == "\"resolve-token\"" }) {
        CodeAction().apply {
          title = "Multiple edits"
          edit = WorkspaceEdit(mapOf(
            fileUri to listOf(
              TextEdit(Range(Position(2, 0), Position(2, 1)), "C\rD\r\n"), // replace '3' with "C\nD\n" (line breaks will be normalized)
              TextEdit(Range(Position(0, 0), Position(1, 0)), ""), // delete first line
              TextEdit(Range(Position(1, 0), Position(1, 0)), "A"), // write A
              TextEdit(Range(Position(1, 0), Position(1, 0)), "B"), // write B in the same position
            )
          ))
        }
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.launchAction("Multiple edits")
      }
      serverSession.awaitExpected()
      codeInsightFixture.checkResult("AB2\nC\nD\n\n4")
    }
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `quick fixes work with a dynamic registration only`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val client = fakeLspIntegration.client
      val diagnostic = Diagnostic(Range(Position(0, 0), Position(0, 5)), "problem", DiagnosticSeverity.Error, "fake")
      assertTrue(readAction { LspDiagnosticAndLazyQuickFixes(diagnostic).getQuickFixes(client, virtualFile) }.isEmpty(),
                 "no quick fixes before the registration")

      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          codeActionKinds = listOf(CodeActionKind.QuickFix)
        })))
      }
      serverSession.expectRequest(serverSession.CODE_ACTION, {
        it.textDocument.uri == fileUri && it.context.only == listOf(CodeActionKind.QuickFix)
      }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Fix the problem"
          kind = CodeActionKind.QuickFix
        }))
      }

      val quickFixes = readAction { LspDiagnosticAndLazyQuickFixes(diagnostic).getQuickFixes(client, virtualFile) }
      // A quick fix asks the server for its code action on the first `getText()` call, and only in a background thread.
      val quickFixText = withContext(Dispatchers.IO) { readAction { quickFixes.first().text } }
      serverSession.awaitExpected()

      assertEquals("Fix the problem", quickFixText)
    }

    @Suppress("AssertBetweenInconvertibleTypes") // Qodana bug
    @Test
    fun `intentions do not request code actions when the dynamic registration lists only quick fixes`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          codeActionKinds = listOf(CodeActionKind.QuickFix)
        })))
      }

      // The intentions list asks the server for code actions synchronously.
      withContext(Dispatchers.EDT) { codeInsightFixture.availableIntentions }

      // An intention request has no `only` filter. A quick fix request has `only = [quickfix]`.
      val intentionRequests = serverSession.receivedRequests(serverSession.CODE_ACTION).filter {
        it.textDocument.uri == fileUri && it.context.only == null
      }
      assertEquals(emptyList<Any>(), intentionRequests, "Expected no intention request for a registration that lists only quick fixes")
    }

    @Test
    fun `intentions work with a dynamic registration only`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          codeActionKinds = listOf(CodeActionKind.Refactor)
        })))
      }

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Replace hello"
          kind = CodeActionKind.Refactor
          edit = WorkspaceEdit(mapOf(fileUri to listOf(TextEdit(Range(Position(0, 0), Position(0, 5)), "bye"))))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.launchAction("Replace hello")
      }
      serverSession.awaitExpected()
      codeInsightFixture.checkResult("bye world")
    }
  }
}
