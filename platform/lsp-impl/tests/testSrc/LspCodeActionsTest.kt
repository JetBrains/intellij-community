package com.intellij.platform.lsp

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
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
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
      // No daemon runs in this test: instantiate the pass registrar, so it assigns LspHighlightingApplier.GROUP_ID.
      TextEditorHighlightingPassRegistrar.getInstance(project)
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val document = codeInsightFixture.editor.document
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
        PublishDiagnosticsParams(
          fileUri,
          listOf(Diagnostic(Range(Position(0, 0), Position(0, 5)), "problem", DiagnosticSeverity.Error, "fake"))
        )
      }
      var highlight: HighlightInfo? = null
      waitUntilAssertSucceeds(message = "the diagnostic must reach the markup") {
        highlight = errorHighlight(document)
        assertNotNull(highlight)
      }
      assertNull(highlight!!.findRegisteredQuickFix { descriptor, _ -> descriptor.action },
                 "no quick fixes before the registration")

      serverSession.expectRequest(serverSession.CODE_ACTION, {
        it.textDocument.uri == fileUri && it.context.only == listOf(CodeActionKind.QuickFix)
      }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Fix the problem"
          kind = CodeActionKind.QuickFix
        }))
      }
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          codeActionKinds = listOf(CodeActionKind.QuickFix)
        })))
      }

      var quickFix: IntentionAction? = null
      waitUntilAssertSucceeds(message = "the registration must add the quick fix to the diagnostic on screen") {
        quickFix = errorHighlight(document)?.findRegisteredQuickFix { descriptor, _ -> descriptor.action }
        assertNotNull(quickFix)
      }
      // A quick fix asks the server for its code action on the first `getText()` call, and only in a background thread.
      val quickFixText = withContext(Dispatchers.IO) { readAction { quickFix!!.text } }
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

    @Test
    fun `code action resolve uses the resolve provider of the dynamic registration`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          resolveProvider = true
        })))
      }

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Replace hello"
          data = "resolve-token"
        }))
      }
      serverSession.expectRequest(serverSession.CODE_ACTION_RESOLVE, { it.data?.toString() == "\"resolve-token\"" }) {
        CodeAction().apply {
          title = "Replace hello"
          edit = WorkspaceEdit(mapOf(fileUri to listOf(TextEdit(Range(Position(0, 0), Position(0, 5)), "bye"))))
        }
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.launchAction("Replace hello")
      }
      serverSession.awaitExpected()
      codeInsightFixture.checkResult("bye world")
    }

    @Suppress("AssertBetweenInconvertibleTypes") // Qodana bug
    @Test
    fun `code action resolve is not sent when the dynamic registration has no resolve provider`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-actions", "textDocument/codeAction", CodeActionRegistrationOptions())))
      }

      // The action has no edit, so the client needs a resolve. The command keeps the action available without the resolve.
      val codeAction = CodeAction().apply {
        title = "Run the command"
        data = "resolve-token"
        command = Command("Run the command", "run")
      }
      val codeActionRequest = serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(codeAction))
      }

      // `isAvailable()` decides about the resolve and sends it synchronously, before the list is ready.
      val intentions = withContext(Dispatchers.EDT) { codeInsightFixture.availableIntentions }
      codeActionRequest.await()

      assertTrue(intentions.any { it.text == "Run the command" }, "Expected the action in the intentions list")
      assertEquals(emptyList<Any>(), serverSession.receivedRequests(serverSession.CODE_ACTION_RESOLVE),
                   "Expected no codeAction/resolve when the server does not support it")
    }
  }

  private suspend fun errorHighlight(document: Document): HighlightInfo? = readAction {
    val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return@readAction null
    markupModel.allHighlighters
      .mapNotNull { HighlightInfo.fromRangeHighlighter(it) }
      .singleOrNull { it.severity == HighlightSeverity.ERROR }
  }
}
