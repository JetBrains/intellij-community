package com.intellij.platform.lsp

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeVisionFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CodeLensRegistrationOptions
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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

  private val codeVision by codeVisionFixture(editorFixture, sharedFileFixture)

  @Nested
  inner class StaticRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        codeLensProvider = CodeLensOptions()
      },
    )

    @Test
    fun `code lens rendered as code vision`() = timeoutRunBlocking {
      val psiFile = sharedFileFixture.get()
      val codeInsightContext = readAction { psiFile.codeInsightContext }

      val virtualFile = psiFile.virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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

    @Test
    fun `codicon tokens are stripped from a code lens title`() = timeoutRunBlocking {
      val psiFile = sharedFileFixture.get()
      val codeInsightContext = readAction { psiFile.codeInsightContext }

      val virtualFile = psiFile.virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && editorFixture.get().document.textLength > 0
      }) {
        listOf(
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("$(play) Run", "run"), null),
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("$(debug) Debug", "debug"), null),
        )
      }

      checkCodeLensRetrying("""
        /*<# block [Run   Debug] #>*/
        fun main() {}
      """.trimIndent(), codeInsightContext)

      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `code lens resolve uses the resolve provider of the dynamic registration`() = timeoutRunBlocking {
      val psiFile = sharedFileFixture.get()
      val codeInsightContext = readAction { psiFile.codeInsightContext }

      val virtualFile = psiFile.virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-lens", "textDocument/codeLens", CodeLensRegistrationOptions().apply {
          resolveProvider = true
        })))
      }

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && editorFixture.get().document.textLength > 0
      }) {
        listOf(
          CodeLens(Range(Position(0, 0), Position(0, 1)), null, "lens-data")
        )
      }
      serverSession.expectRequest(serverSession.CODE_LENS_RESOLVE, { it.data?.toString() == "\"lens-data\"" }) {
        CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", "run"), "lens-data")
      }

      checkCodeLensRetrying("""
        /*<# block [Run] #>*/
        fun main() {}
      """.trimIndent(), codeInsightContext)

      serverSession.awaitExpected()
    }

    @Test
    fun `code lens resolve is sent when any matching dynamic registration has a resolve provider`() = timeoutRunBlocking {
      val psiFile = sharedFileFixture.get()
      val codeInsightContext = readAction { psiFile.codeInsightContext }

      val virtualFile = psiFile.virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(
          Registration("code-lens-1", "textDocument/codeLens", CodeLensRegistrationOptions()),
          Registration("code-lens-2", "textDocument/codeLens", CodeLensRegistrationOptions().apply {
            resolveProvider = true
          }),
          Registration("code-lens-3", "textDocument/codeLens", CodeLensRegistrationOptions()),
        ))
      }

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && editorFixture.get().document.textLength > 0
      }) {
        listOf(
          CodeLens(Range(Position(0, 0), Position(0, 1)), null, "lens-data")
        )
      }
      serverSession.expectRequest(serverSession.CODE_LENS_RESOLVE, { it.data?.toString() == "\"lens-data\"" }) {
        CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", "run"), "lens-data")
      }

      checkCodeLensRetrying("""
        /*<# block [Run] #>*/
        fun main() {}
      """.trimIndent(), codeInsightContext)

      serverSession.awaitExpected()
    }

    @Suppress("AssertBetweenInconvertibleTypes") // Qodana bug
    @Test
    fun `code lens resolve is not sent when the dynamic registration has no resolve provider`() = timeoutRunBlocking {
      val virtualFile = sharedFileFixture.get().virtualFile
      val document = editorFixture.get().document
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val client = fakeLspIntegration.client
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("code-lens", "textDocument/codeLens", CodeLensRegistrationOptions())))
      }

      // A lens without a command needs a resolve.
      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && document.textLength > 0
      }) {
        listOf(CodeLens(Range(Position(0, 0), Position(0, 1)), null, "lens-data"))
      }

      writeCommandAction(project, "") {
        document.setText("fun main() {}")
      }
      // The cache sends the resolve inside the code lens request, before it stores the lenses.
      waitUntilAssertSucceeds(message = "the code lens must reach the cache") {
        val lenses = withContext(Dispatchers.IO) {
          readAction { client.highlightingCacheRegistry.codeLensCache.getHighlightings(virtualFile) }
        }
        assertTrue(lenses.isNotEmpty())
      }

      assertEquals(emptyList<Any>(), serverSession.receivedRequests(serverSession.CODE_LENS_RESOLVE),
                   "Expected no codeLens/resolve when the server does not support it")
    }
  }

  // The code lens cache is not guaranteed to update between `doHighlighting` and `codeVisionHost.calculateCodeVisionSync`,
  // so a retry with delay is necessary
  private suspend fun checkCodeLensRetrying(expectedText: String, codeInsightContext: CodeInsightContext) {
    waitUntilAssertSucceeds(message = "Code lens aren't rendered") {
      codeVision.testProviders(codeInsightContext, expectedText, "LspCodeVisionProvider")
    }
  }
}