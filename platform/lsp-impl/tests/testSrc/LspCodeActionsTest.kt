package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
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
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
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

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
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
    val serverSession = configureServerSession(project, virtualFile)
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
