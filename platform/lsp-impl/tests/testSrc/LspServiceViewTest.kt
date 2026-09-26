package com.intellij.platform.lsp

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewContributor
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewSupport
import com.intellij.platform.lsp.impl.serviceView.LspTrafficPayloadHyperlinkInfo
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class LspServiceViewTest {
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
  fun `lsp server is shown in services view and its console receives messages`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    val contributor = LspServiceViewContributor()
    val clients = contributor.getServices(project)
    assertEquals(1, clients.size, "The started LSP server should be listed in the Services view")
    val lspClient = clients.single()

    serverSession.sendNotification(serverSession.LOG_MESSAGE) { MessageParams(MessageType.Warning, "log-message-marker") }
    serverSession.sendNotification(serverSession.LOG_MESSAGE) { MessageParams(MessageType.Error, "error-message-marker") }
    serverSession.sendNotification(serverSession.SHOW_MESSAGE) { MessageParams(MessageType.Info, "show-message-marker") }

    val console = requireNotNull(LspServiceViewSupport.getInstance(project).getOrCreateConsole(lspClient)) {
      "A console should exist for a tracked LSP client"
    }
    val markers = listOf("log-message-marker", "error-message-marker", "show-message-marker")
    var consoleText = ""
    while (markers.any { it !in consoleText }) {
      delay(50.milliseconds)
      consoleText = withContext(Dispatchers.EDT) { console.getConsoleTextForTests() }
    }

    assertTrue("Starting" in consoleText, "Console should contain the server starting lifecycle message, got:\n$consoleText")
    assertTrue("Server initialized" in consoleText, "Console should contain the server initialized lifecycle message, got:\n$consoleText")
    assertTrue("WARN" in consoleText, "The window/logMessage warning should be printed with its level tag, got:\n$consoleText")
    assertTrue("ERROR" in consoleText, "The window/logMessage error should be printed with its level tag, got:\n$consoleText")
    assertTrue("window/showMessage: show-message-marker" in consoleText,
               "The window/showMessage message should be printed with the method tag, got:\n$consoleText")
    assertTrue("request 'initialize' (id=" in consoleText,
               "Outbound JSON-RPC requests should be printed, got:\n$consoleText")
    assertTrue("response (id=" in consoleText,
               "Inbound JSON-RPC responses should be printed, got:\n$consoleText")
    assertTrue("notification 'window/logMessage'" in consoleText,
               "Inbound JSON-RPC notifications should be printed, got:\n$consoleText")

    withContext(Dispatchers.EDT) {
      val consoleView = console.getConsoleViewForTests()

      // the markers include the level tag because the bare marker also occurs earlier, in the raw `IN` traffic payload
      assertContentType(consoleView, "WARN  log-message-marker", ConsoleViewContentType.LOG_WARNING_OUTPUT)
      assertContentType(consoleView, "ERROR error-message-marker", ConsoleViewContentType.LOG_ERROR_OUTPUT)
      assertContentType(consoleView, "window/showMessage: show-message-marker", ConsoleViewContentType.LOG_INFO_OUTPUT)
      assertContentType(consoleView, "Server initialized", ConsoleViewContentType.SYSTEM_OUTPUT)
      assertContentType(consoleView, "OUT  ", ConsoleViewContentType.LOG_DEBUG_OUTPUT)
      assertContentType(consoleView, "IN   ", ConsoleViewContentType.LOG_VERBOSE_OUTPUT)

      val trafficLinks = findTrafficHyperlinks(consoleView)
      val initializeRequestLink = requireNotNull(trafficLinks["request 'initialize'"]) {
        "The 'initialize' request header should be a hyperlink, found links: ${trafficLinks.keys}"
      }
      assertTrue("\"initialize\"" in initializeRequestLink.json,
                 "The hyperlink should carry the raw request payload, got: ${initializeRequestLink.json.take(200)}")
      assertTrue(initializeRequestLink.header.startsWith("→"), "An outbound payload popup title should use the outbound arrow")

      val logMessageNotificationLink = requireNotNull(trafficLinks["notification 'window/logMessage'"]) {
        "The 'window/logMessage' notification header should be a hyperlink, found links: ${trafficLinks.keys}"
      }
      assertTrue("log-message-marker" in logMessageNotificationLink.json,
                 "The hyperlink should carry the raw notification payload, got: ${logMessageNotificationLink.json.take(200)}")
      assertTrue(logMessageNotificationLink.header.startsWith("←"), "An inbound payload popup title should use the inbound arrow")
    }

    @Suppress("DEPRECATION")
    LspServerManager.getInstance(project).stopServers(FakeLspServerSupportProvider::class.java)
    while (contributor.getServices(project).isNotEmpty()) {
      delay(50.milliseconds)
    }
  }

  private fun assertContentType(consoleView: ConsoleViewImpl, marker: String, expected: ConsoleViewContentType) {
    val editor = requireNotNull(consoleView.editor) { "The console editor should be initialized" }
    val offset = editor.document.text.indexOf(marker)
    assertTrue(offset >= 0, "Marker '$marker' should be present in the console text")
    // console tokens are highlighted in the document markup model, see ConsoleTokenUtil
    val markupModel = DocumentMarkupModel.forDocument(editor.document, consoleView.project, true)
    val coveringKeys = markupModel.allHighlighters
      .filter { it.isValid && it.startOffset <= offset && offset < it.endOffset }
      .mapNotNull { it.textAttributesKey }
    val expectedKey = requireNotNull(expected.attributesKey) { "The expected content type should have an attributes key" }
    assertTrue(expectedKey in coveringKeys,
               "Marker '$marker' should be highlighted as ${expectedKey.externalName}, actual keys: " +
               coveringKeys.map { it.externalName })
  }

  /** Maps the hyperlink text (up to the `(id=...)` part) to its [LspTrafficPayloadHyperlinkInfo]. */
  private fun findTrafficHyperlinks(consoleView: ConsoleViewImpl): Map<String, LspTrafficPayloadHyperlinkInfo> {
    val editor = requireNotNull(consoleView.editor) { "The console editor should be initialized" }
    return editor.markupModel.allHighlighters
      .mapNotNull { highlighter ->
        val info = EditorHyperlinkSupport.getHyperlinkInfo(highlighter) as? LspTrafficPayloadHyperlinkInfo ?: return@mapNotNull null
        val text = editor.document.getText(highlighter.textRange).substringBefore(" (id=")
        text to info
      }
      .toMap()
  }
}
