package com.intellij.platform.lsp

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.application.readAction
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.lspServerSupportFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeVisionFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.delay
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class LspCodeLensTest {

  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  private val sharedFileFixture = sourceRootFixture.psiFileFixture("test.txt", "")
  private val editorFixture = sharedFileFixture.editorFixture()

  @Suppress("unused")
  private val lspServerSupport by projectFixture.lspServerSupportFixture(
    configureServerCapabilities = {
      codeLensProvider = CodeLensOptions()
    },
  )
  private val codeVision by codeVisionFixture(editorFixture, sharedFileFixture)

  @Test
  fun `code lens rendered as code vision`() = timeoutRunBlocking {
    val psiFile = sharedFileFixture.get()
    val codeInsightContext = readAction { psiFile.codeInsightContext }

    val virtualFile = psiFile.virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.CODE_LENS, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && editorFixture.get().document.textLength > 0
    }) {
      listOf(
        CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", "run"), null)
      )
    }

    checkCodeLensRetrying("""
      /*<# block [Run] #>*/
      fun main() {}
    """.trimIndent(), codeInsightContext)

    serverSession.awaitExpected()
  }

  @Test
  fun `two code lens on the same line rendered`() = timeoutRunBlocking {
    val psiFile = sharedFileFixture.get()
    val codeInsightContext = readAction { psiFile.codeInsightContext }

    val virtualFile = psiFile.virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.CODE_LENS, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && editorFixture.get().document.textLength > 0
    }) {
      listOf(
        CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", "run"), null),
        CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Test", "test"), null),
      )
    }

    checkCodeLensRetrying("""
      /*<# block [Run   Test] #>*/
      fun main() {}
    """.trimIndent(), codeInsightContext)

    serverSession.awaitExpected()
  }

  // The code lens cache is not guaranteed to update between `doHighlighting` and `codeVisionHost.calculateCodeVisionSync`,
  // so a retry with delay is necessary
  private suspend fun checkCodeLensRetrying(expectedText: String, codeInsightContext: CodeInsightContext) {
    var lastError: AssertionError? = null
    repeat(3) {
      try {
        codeVision.testProviders(codeInsightContext, expectedText, "LspCodeVisionProvider")
        return
      }
      catch (e: AssertionError) {
        lastError = e
        delay(100.milliseconds)
      }
    }
    fail("Code lens aren't rendered after retries", lastError)
  }
}