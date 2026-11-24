package com.intellij.platform.lsp

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCodeLensCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeVisionFixture
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
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
      // editorFixture opens the empty file during fixture setup, before the test body can set up expectations, and code vision
      // sometimes asks for the lenses of the empty document then. The tests expect only the request sent after the text is set.
      allowUnmatched = { DOCUMENT_SYNC + CODE_LENS },
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
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      // editorFixture opens the empty file during fixture setup, and after the registration code vision sometimes asks for the lenses
      // of the empty document, before the test sets the text. The tests expect only the request sent after the text is set.
      allowUnmatched = { DOCUMENT_SYNC + CODE_LENS },
    )

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

  @Nested
  inner class GutterIcons {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = GutterLensTestCustomization(),
      configureServerCapabilities = {
        codeLensProvider = CodeLensOptions()
      },
      // editorFixture opens the empty shared file during fixture setup, and code vision sometimes asks for the lenses of an
      // empty document. Every test expects only the request that carries its own text.
      allowUnmatched = { DOCUMENT_SYNC + CODE_LENS },
    )

    private val codeInsight by codeInsightFixture(projectFixture, tempDirFixture)

    @Test
    fun `an allowlisted code lens renders as a gutter icon`() = timeoutRunBlocking {
      val virtualFile = codeInsight.configureLspFile("gutter-lens.txt")
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val document = codeInsight.editor.document

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && document.textLength > 0
      }) {
        listOf(
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("$(play) Run", GUTTER_LENS_COMMAND), null),
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("References", "test.references"), null),
        )
      }

      writeCommandAction(project, "") { document.setText("fun main() {}") }

      waitUntilAssertSucceeds(message = "The gutter icon of the code lens is not shown") {
        codeInsight.doHighlighting()
        assertEquals(listOf("Run"), codeInsight.gutterTooltips())
      }

      serverSession.awaitExpected()
    }

    @Test
    fun `allowlisted code lenses of one line share one gutter icon`() = timeoutRunBlocking {
      val virtualFile = codeInsight.configureLspFile("gutter-lens-shared.txt")
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val document = codeInsight.editor.document

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && document.textLength > 0
      }) {
        listOf(
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("$(play) Run", GUTTER_LENS_COMMAND), null),
          CodeLens(Range(Position(0, 4), Position(0, 5)), Command("$(debug) Debug", GUTTER_LENS_COMMAND), null),
        )
      }

      writeCommandAction(project, "") { document.setText("fun main() {}") }

      waitUntilAssertSucceeds(message = "The gutter icon of the code lenses is not shown") {
        codeInsight.doHighlighting()
        assertEquals(listOf("Run<br>Debug"), codeInsight.gutterTooltips())
      }

      serverSession.awaitExpected()
    }

    @Test
    fun `a gutter click runs the command of the code lens`() = timeoutRunBlocking {
      val virtualFile = codeInsight.configureLspFile("gutter-lens-click.txt")
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val document = codeInsight.editor.document

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && document.textLength > 0
      }) {
        listOf(CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", GUTTER_LENS_COMMAND), null))
      }

      writeCommandAction(project, "") { document.setText("fun main() {}") }

      waitUntilAssertSucceeds(message = "The gutter icon of the code lens is not shown") {
        codeInsight.doHighlighting()
        assertEquals(1, codeInsight.gutterTooltips().size)
      }

      val commandExecuted = serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == GUTTER_LENS_COMMAND }) { }

      val renderer = codeInsight.findAllGutters().single() as LineMarkerGutterIconRenderer<*>
      val (handler, anchor) = readAction {
        val info = renderer.lineMarkerInfo
        @Suppress("UNCHECKED_CAST")
        (info.navigationHandler as GutterIconNavigationHandler<PsiElement>) to info.element
      }
      // The click handler of a gutter icon runs on the EDT, like the click handler of a code vision entry.
      withContext(Dispatchers.EDT) { handler.navigate(null, anchor) }

      commandExecuted.await()
      serverSession.awaitExpected()
    }

    @Test
    fun `an allowlisted code lens is not shown as code vision`() = timeoutRunBlocking {
      val psiFile = sharedFileFixture.get()
      val codeInsightContext = readAction { psiFile.codeInsightContext }

      val virtualFile = psiFile.virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && editorFixture.get().document.textLength > 0
      }) {
        listOf(
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", GUTTER_LENS_COMMAND), null),
          CodeLens(Range(Position(0, 0), Position(0, 1)), Command("References", "test.references"), null),
        )
      }

      checkCodeLensRetrying("""
        /*<# block [References] #>*/
        fun main() {}
      """.trimIndent(), codeInsightContext)

      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class GutterIconsWithoutAllowlist {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        codeLensProvider = CodeLensOptions()
      },
      allowUnmatched = { DOCUMENT_SYNC + CODE_LENS },
    )

    private val codeInsight by codeInsightFixture(projectFixture, tempDirFixture)

    @Test
    fun `a code lens is not rendered in the gutter by default`() = timeoutRunBlocking {
      val virtualFile = codeInsight.configureLspFile("gutter-lens-default.txt")
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val client = fakeLspIntegration.client
      val document = codeInsight.editor.document

      serverSession.expectRequest(serverSession.CODE_LENS, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && document.textLength > 0
      }) {
        listOf(CodeLens(Range(Position(0, 0), Position(0, 1)), Command("Run", GUTTER_LENS_COMMAND), null))
      }

      writeCommandAction(project, "") { document.setText("fun main() {}") }

      // The absence of a gutter icon is only meaningful once the lens has arrived, and the cache is where it lands first.
      waitUntilAssertSucceeds(message = "the code lens must reach the cache") {
        codeInsight.doHighlighting()
        val lenses = withContext(Dispatchers.IO) {
          readAction { client.highlightingCacheRegistry.codeLensCache.getHighlightings(virtualFile) }
        }
        assertTrue(lenses.isNotEmpty())
      }

      codeInsight.doHighlighting()
      assertEquals(emptyList<String?>(), codeInsight.gutterTooltips())

      serverSession.awaitExpected()
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

private const val GUTTER_LENS_COMMAND = "test.run"

private class GutterLensTestCustomization : LspCustomization() {
  override val codeLensCustomizer: LspCodeLensCustomizer = object : LspCodeLensSupport() {
    override val gutterLensCommands: Set<String> = setOf(GUTTER_LENS_COMMAND)
  }
}

/**
 * Opens an empty file, so a test can set up its expectations before the text arrives.
 *
 * A code lens response restarts the daemon, which counts as a model change for the strict test assertion of a highlighting
 * pass. Allow it, like the other reactive LSP feature tests do.
 */
private fun CodeInsightTestFixture.configureLspFile(name: String): VirtualFile {
  (this as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
  return configureByText(name, "").virtualFile
}

private suspend fun CodeInsightTestFixture.gutterTooltips(): List<String?> {
  // findAllGutters runs highlighting itself, so it must stay outside the read action.
  val gutters = findAllGutters()
  return readAction { gutters.map { it.tooltipText } }
}