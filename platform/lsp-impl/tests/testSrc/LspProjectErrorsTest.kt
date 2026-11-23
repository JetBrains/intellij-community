package com.intellij.platform.lsp

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.problems.LspProjectErrors
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Project Errors tab: a push for a file the server does not have open becomes a [ProblemsCollector] row. */
@TestApplication
internal class LspProjectErrorsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

  @Test
  fun `a push for a file the server does not have open fills the Project Errors tab`(): Unit = timeoutRunBlocking {
    // the product starts the adapter in the postStartupActivity of intellij.platform.lsp.impl.problems; this test starts it the same way
    project.serviceAsync<LspProjectErrors>()

    val openFile = codeInsightFixture.configureByText("open.txt", "hello").virtualFile
    val closedFile = codeInsightFixture.addFileToProject("closed.txt", "hello world").virtualFile
    val serverSession = fakeLspIntegration.serverSession(openFile)
    val closedUri = serverSession.fileUri(closedFile)
    val collector = ProblemsCollector.getInstance(project)

    serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
      PublishDiagnosticsParams(closedUri, listOf(
        Diagnostic(Range(Position(0, 0), Position(0, 5)), "pushed error", DiagnosticSeverity.Error, "fake"),
        Diagnostic(Range(Position(0, 6), Position(0, 11)), "a warning", DiagnosticSeverity.Warning, "fake"),
      ))
    }
    waitUntilAssertSucceeds(message = "the pushed error of the unopened file must reach the Project Errors tab") {
      assertEquals(listOf("pushed error"), collector.getFileProblems(closedFile).map { it.text })
    }

    val didOpen = serverSession.expectNotification(serverSession.DID_OPEN) { it.textDocument.uri == closedUri }
    withContext(Dispatchers.EDT) {
      codeInsightFixture.openFileInEditor(closedFile)
    }
    didOpen.await()
    waitUntilAssertSucceeds(message = "the row of a file open on the server must leave the tab") {
      assertEquals(emptyList<String>(), collector.getFileProblems(closedFile).map { it.text })
    }

    val didClose = serverSession.expectNotification(serverSession.DID_CLOSE) { it.textDocument.uri == closedUri }
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).closeFile(closedFile)
    }
    didClose.await()
    waitUntilAssertSucceeds(message = "the kept push must come back to the tab after the close") {
      assertEquals(listOf("pushed error"), collector.getFileProblems(closedFile).map { it.text })
    }

    serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
      PublishDiagnosticsParams(closedUri, emptyList())
    }
    waitUntilAssertSucceeds(message = "an empty push must clear the row") {
      assertEquals(emptyList<String>(), collector.getFileProblems(closedFile).map { it.text })
    }
  }
}
