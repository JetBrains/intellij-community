package com.intellij.platform.lsp

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverRegistrationOptions
import org.eclipse.lsp4j.MarkedString
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@TestApplication
internal class LspHoverTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  /**
   * Each nested class declares its own fixture.
   * [DynamicRegistration] needs a server without the static hover capability.
   */
  private fun hoverIntegrationFixture() =
    projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        hoverProvider = Either.forLeft(true)
      },
    )

  @Nested
  inner class BasicHover {
    private val fakeLspIntegration by hoverIntegrationFixture()

    @Test
    fun `hover returns content for symbol`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(MarkupContent(MarkupKind.PLAINTEXT, "Hello documentation"), Range(Position(0, 0), Position(0, 5)))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertEquals("<div class='content'>Hello documentation</div>", html)
    }

    @Test
    fun `hover returns null when no information available`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover()
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertTrue(targets.isEmpty(), "Expected no documentation targets for empty hover")
    }
  }

  @Nested
  inner class ContentFormats {
    private val fakeLspIntegration by hoverIntegrationFixture()

    @Test
    fun `hover with plaintext content`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(MarkupContent(MarkupKind.PLAINTEXT, "plain text documentation"))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertEquals("<div class='content'>plain text documentation</div>", html)
    }

    @Test
    fun `hover with markdown content`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(MarkupContent(MarkupKind.MARKDOWN, "**bold** _italic_ `code`"))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertTrue(html.contains("<b>bold</b>"), "Expected b in: $html")
      assertTrue(html.contains("<i>italic</i>"), "Expected i in: $html")
      assertTrue(html.contains("<code>code</code>"), "Expected code in: $html")
    }

    @Test
    fun `hover with MarkedString content`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      @Suppress("DEPRECATION")
      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(listOf(Either.forRight(MarkedString("text", "foobar"))))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertTrue(html.contains("<div class='definition'>"), "Expected definition section in: $html")
      assertTrue(html.contains("foobar"), "Expected code content in: $html")
    }

    @Test
    fun `hover with multiple MarkedString contents`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      @Suppress("DEPRECATION")
      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(listOf(
          Either.forRight(MarkedString("text", "foobar")),
          Either.forRight(MarkedString("text", "bazqux")),
        ))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertTrue(html.contains("foobar"), "Expected first code block in: $html")
      assertTrue(html.contains("bazqux"), "Expected second code block in: $html")
    }

    @Test
    fun `hover with MarkupContent`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(MarkupContent(MarkupKind.MARKDOWN, "# Header\n\nSome documentation"))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertTrue(html.contains("Header"), "Expected heading in: $html")
      assertTrue(html.contains("Some documentation"), "Expected paragraph in: $html")
    }
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `hover works with a dynamic registration only`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("hover", "textDocument/hover", HoverRegistrationOptions())))
      }

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(MarkupContent(MarkupKind.PLAINTEXT, "Hello documentation"))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
    }

    @Suppress("AssertBetweenInconvertibleTypes") // Qodana bug
    @Test
    fun `hover is not requested without a dynamic registration`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      // The documentation target provider waits for the hover response synchronously.
      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }

      assertEquals(emptyList<Any>(), serverSession.receivedRequests(serverSession.HOVER),
                   "Expected no textDocument/hover without a registration")
      assertTrue(targets.isEmpty(), "Expected no documentation targets without a registration")
    }
  }
}
