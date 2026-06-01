package com.intellij.platform.lsp

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.logging.LanguageServiceLoggerService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.exists
import kotlin.io.path.readText

@TestApplication
internal class LspMessageLoggingTest {
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

  private val lspLogger = Logger.getInstance(LanguageServiceLoggerService::class.java)

  @AfterEach
  fun restoreLogLevel() {
    lspLogger.setLevel(LogLevel.INFO)
  }

  @Test
  fun `inbound and outbound messages are logged when debug logging is enabled`() = timeoutRunBlocking {
    lspLogger.setLevel(LogLevel.DEBUG)

    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val uri = serverSession.fileUri(virtualFile)

    serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
      PublishDiagnosticsParams(uri, listOf(
        Diagnostic(
          Range(Position(0, 0), Position(0, 5)),
          "test-inbound-logging-marker",
          DiagnosticSeverity.Error, "test",
        )
      ))
    }

    val logPaths = LanguageServiceLoggerService.getInstance().getActiveLogPaths()
    var foundInbound = false
    var foundOutbound = false
    withTimeoutOrNull(5000) {
      var found = false

      while (!found) {
        for (logPath in logPaths) {
          if (!logPath.exists())
            continue
          val content = logPath.readText()
          for (line in content.lines()) {
            if ("IN " in line && "test-inbound-logging-marker" in line)
              foundInbound = true
            if ("OUT" in line && "initialize" in line)
              foundOutbound = true
          }
          found = foundInbound && foundOutbound

          if (found)
            break
        }
        if (!found)
          delay(200)
      }
    }

    assertTrue(
      foundInbound,
      "Active log files should contain 'IN' entries with the server notification content",
    )

    assertTrue(
      foundOutbound,
      "Active log files should contain 'OUT' entries with the server notification content",
    )
  }
}
