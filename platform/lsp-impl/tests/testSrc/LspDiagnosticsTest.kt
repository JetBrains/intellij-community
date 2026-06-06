package com.intellij.platform.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.SpaceTokenizingFileType
import com.intellij.platform.lsp.common.assertCustomPsiTree
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.common.problemFileHighlightFilterFixture
import com.intellij.platform.lsp.common.spaceTokenizingLanguageFixture
import com.intellij.platform.lsp.common.wolfFixture
import com.intellij.platform.lsp.testFramework.checkHighlightingRetrying
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.problems.ProblemListener
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticRegistrationOptions
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Collections


@TestApplication
internal class LspDiagnosticsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Nested
  inner class PublishDiagnostics {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture()

    @Test
    fun `publish diagnostics twice for the same document version`() = timeoutRunBlocking {
      // the following line suppresses a read problem, second diagnostic can cancel running highlighting session, and we must fix it
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """hello world""").virtualFile
      val expectedData = createExpectedDataFromText("""<error descr="foo">!</error>hello world""")
      val serverSession = configureServerSession(project, virtualFile)
      val uri = serverSession.fileUri(virtualFile)

      launch(start = CoroutineStart.UNDISPATCHED) {
        serverSession.awaitNotification(serverSession.DID_CHANGE) { it.textDocument.uri == uri }
        delay(200)
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams(uri, listOf(
            Diagnostic(Range(Position(0, 1), Position(0, 5)), "discarded", DiagnosticSeverity.Error, "fake")
          ))
        }
        delay(200)
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams(uri, listOf(
            Diagnostic(Range(Position(0, 0), Position(0, 1)), "foo", DiagnosticSeverity.Error, "fake")
          ))
        }
      }

      // register the client-side await first
      launch(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying(expectedData)
      }

      // kick off the scenario
      codeInsightFixture.type("!")
    }

    @Test
    fun `zero-length diagnostics`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error 0"></error>line 0<EOLError descr="EOL error 0"></EOLError>
      <EOLError descr="EOL error 1"></EOLError>
      line 2<EOLError descr="EOL error 2"></EOLError>
      line 3<EOLError descr="EOF error"></EOLError><EOLError descr="EOL error 3"></EOLError>""".trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      val job = launch(start = CoroutineStart.LAZY) {
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams(serverSession.fileUri(virtualFile), listOf(
            Diagnostic(Range(Position(0, 0), Position(0, 0)), "error 0", DiagnosticSeverity.Error, null),
            Diagnostic(Range(Position(0, 6), Position(0, 6)), "EOL error 0", DiagnosticSeverity.Error, null),
            Diagnostic(Range(Position(1, 0), Position(1, 0)), "EOL error 1", DiagnosticSeverity.Error, null),
            Diagnostic(Range(Position(2, 100), Position(2, 200)), "EOL error 2", DiagnosticSeverity.Error, null),
            Diagnostic(Range(Position(3, 6), Position(3, 6)), "EOL error 3", DiagnosticSeverity.Error, null),
            Diagnostic(Range(Position(4, 0), Position(4, 0)), "EOF error", DiagnosticSeverity.Error, null),
            Diagnostic(Range(Position(5, 0), Position(5, 0)), "out of range -> ignored", DiagnosticSeverity.Error, null),
          ))
        }
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying()
      }

      job.start()
    }

    /**
     * Creates warnings, verifies them, then applies document edits.
     * Verifies the editor shows the surviving warnings at their adjusted positions.
     *
     * @see PullDiagnostics.`error range update on typing`
     */
    @Test
    fun `error range update on typing`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <div class="
      <warning descr="w1">text-2xl</warning>
      <warning descr="w2">text-3xl</warning>
      <warning descr="w3">text-4xl</warning> <warning descr="w4">text-5xl</warning>
      <warning descr="w5">text-6xl</warning>
      <warning descr="w6">text-7xl</warning> <warning descr="w7">text-8xl</warning>
      <warning descr="w8">text-9xl</warning>
      "></div>""".trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val uri = serverSession.fileUri(virtualFile)

      // Phase 1: publish 8 warnings and verify all are shown
      val publishJob = launch(start = CoroutineStart.LAZY) {
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams(uri, listOf(
            Diagnostic(Range(Position(1, 0), Position(1, 8)), "w1", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(2, 0), Position(2, 8)), "w2", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(3, 0), Position(3, 8)), "w3", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(3, 9), Position(3, 17)), "w4", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(4, 0), Position(4, 8)), "w5", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(5, 0), Position(5, 8)), "w6", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(5, 9), Position(5, 17)), "w7", DiagnosticSeverity.Warning, "fake"),
            Diagnostic(Range(Position(6, 0), Position(6, 8)), "w8", DiagnosticSeverity.Warning, "fake"),
          ))
        }
      }

      val initialCheck = async(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying()
      }

      publishJob.start()
      initialCheck.await()

      // Phase 2: apply 5 edits that touch some diagnostics, then publish updated diagnostics
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.apply {
          insertString(14, "X")
          replaceString(24, 33, "Y") // remove text-3xl & \n
          insertString(42, "Q")
          insertString(51, "W")
          insertString(61, "\nfoo\nbar")
        }
      }

      val expectedData = createExpectedDataFromText("""
      <div class="
      <warning descr="w1">tXext-2xl</warning>
      tYext-4xl <warning descr="w4">text-5xl</warning>
      Q<warning descr="w5">text-6xl</warning>W
      <warning descr="w6">text-7xl</warning>
      foo
      bar <warning descr="w7">text-8xl</warning>
      <warning descr="w8">text-9xl</warning>
      "></div>""".trimIndent())

      (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(expectedData)
    }
  }

  @Nested
  inner class PullDiagnostics {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        diagnosticProvider = DiagnosticRegistrationOptions()
      },
    )

    @Test
    fun `basic pull diagnostics`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error message">hello</error> world
      """.trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "error message", DiagnosticSeverity.Error, null)
        )))
      }

      checkHighlightingByPolling()
    }

    @Test
    fun `zero-length pull diagnostics`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error 0"></error>line 0<EOLError descr="EOL error 0"></EOLError>
      <EOLError descr="EOL error 1"></EOLError>
      line 2<EOLError descr="EOL error 2"></EOLError>
      line 3<EOLError descr="EOF error"></EOLError><EOLError descr="EOL error 3"></EOLError>""".trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 0)), "error 0", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(0, 6), Position(0, 6)), "EOL error 0", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(1, 0), Position(1, 0)), "EOL error 1", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(2, 100), Position(2, 200)), "EOL error 2", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(3, 6), Position(3, 6)), "EOL error 3", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(4, 0), Position(4, 0)), "EOF error", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(5, 0), Position(5, 0)), "out of range -> ignored", DiagnosticSeverity.Error, null),
        )))
      }

      checkHighlightingByPolling()
    }

    @Test
    fun `empty pull response clears previously reported errors`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error">hello</error> world
      """.trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val uri = serverSession.fileUri(virtualFile)

      // Phase 1: server reports an error.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == uri }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null)
        )))
      }

      checkHighlightingByPolling()

      // Phase 2: edit the document, server replies with an empty diagnostics list.
      // The cache must apply that reply (clear the previous error), not treat the empty reply as "no response".
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == uri }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList()))
      }
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.insertString(0, " ")
      }
      val noErrors = createExpectedDataFromText(" hello world")
      (codeInsightFixture as CodeInsightTestFixtureImpl).checkHighlightingRetrying(noErrors, initialCheck = true)
    }

    /**
     * Creates warnings, verifies them, then applies document edits.
     * Verifies the editor shows the surviving warnings at their adjusted positions.
     *
     * @see PublishDiagnostics.`error range update on typing`
     */
    @Test
    fun `error range update on typing`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <div class="
      <warning descr="w1">text-2xl</warning>
      <warning descr="w2">text-3xl</warning>
      <warning descr="w3">text-4xl</warning> <warning descr="w4">text-5xl</warning>
      <warning descr="w5">text-6xl</warning>
      <warning descr="w6">text-7xl</warning> <warning descr="w7">text-8xl</warning>
      <warning descr="w8">text-9xl</warning>
      "></div>""".trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      // Phase 1: answer the initial pull with 8 warnings and verify all are shown.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(1, 0), Position(1, 8)), "w1", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(2, 0), Position(2, 8)), "w2", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(3, 0), Position(3, 8)), "w3", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(3, 9), Position(3, 17)), "w4", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(4, 0), Position(4, 8)), "w5", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(5, 0), Position(5, 8)), "w6", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(5, 9), Position(5, 17)), "w7", DiagnosticSeverity.Warning, null),
          Diagnostic(Range(Position(6, 0), Position(6, 8)), "w8", DiagnosticSeverity.Warning, null),
        )))
      }

      checkHighlightingByPolling()

      // Phase 2: apply 5 edits; the cache's pending-edit adjustment must produce the same surviving
      // set as the publish variant of this test. No second pull response is registered so the
      // cache keeps serving the adjusted snapshot synchronously.
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.apply {
          insertString(14, "X")
          replaceString(24, 33, "Y") // remove text-3xl & \n
          insertString(42, "Q")
          insertString(51, "W")
          insertString(61, "\nfoo\nbar")
        }
      }

      val expectedData = createExpectedDataFromText("""
      <div class="
      <warning descr="w1">tXext-2xl</warning>
      tYext-4xl <warning descr="w4">text-5xl</warning>
      Q<warning descr="w5">text-6xl</warning>W
      <warning descr="w6">text-7xl</warning>
      foo
      bar <warning descr="w7">text-8xl</warning>
      <warning descr="w8">text-9xl</warning>
      "></div>""".trimIndent())

      (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(expectedData)
    }
  }

  @Nested
  inner class WolfTheProblemSolverInterop {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        diagnosticProvider = DiagnosticRegistrationOptions()
      },
    )

    private val filterFixture = projectFixture.problemFileHighlightFilterFixture()
    private val wolf by wolfFixture(projectFixture, filterFixture)

    @Test
    fun `error diagnostics are reported to WolfTheProblemSolver`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error">hello</error> world
      """.trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null)
        )))
      }

      checkHighlightingByPolling()

      // reportErrorsToWolf runs asynchronously after highlights are applied to the editor
      waitUntilAssertSucceeds(message = "File with error diagnostics should be reported as a problem file") {
        assertTrue(wolf.isProblemFile(virtualFile))
      }
    }

    @Test
    fun `no problemsChanged fires when LSP server reports no diagnostics`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList()))
      }

      val recorder = ProblemEventRecorder().subscribe(project, codeInsightFixture.testRootDisposable)

      checkHighlightingByPolling()

      // The pass calls reportErrorsToWolf asynchronously after applying highlights; give it time to run
      // so any spurious event would be observed.
      delay(500)

      assertEquals(emptySet<VirtualFile>(), recorder.appeared)
      assertEquals(emptySet<VirtualFile>(), recorder.changed,
                   "clearProblemsFromExternalSource must not fire problemsChanged when the LSP source was never registered")
      assertEquals(emptySet<VirtualFile>(), recorder.disappeared)
    }

    @Test
    fun `Wolf reflects errors that appear and then get cleared by a fresh server response`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error">hello</error> world
      """.trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val uri = serverSession.fileUri(virtualFile)

      // Phase 1: server reports an error.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == uri }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null)
        )))
      }

      checkHighlightingByPolling()

      waitUntilAssertSucceeds(message = "Wolf must register the file after the first error diagnostic") {
        assertTrue(wolf.isProblemFile(virtualFile))
      }

      // Phase 2: edit the document and have the server reply with no diagnostics.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == uri }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList()))
      }
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.insertString(0, " ")
      }
      val noErrors = createExpectedDataFromText(" hello world")
      (codeInsightFixture as CodeInsightTestFixtureImpl).checkHighlightingRetrying(noErrors, initialCheck = true)

      waitUntilAssertSucceeds(message = "Wolf must unregister the file once the LSP errors are cleared") {
        assertFalse(wolf.isProblemFile(virtualFile))
      }
    }

    /**
     * After the user clears the entire file, the cache returns pending-edit-adjusted (empty) diagnostics even though
     * the server hasn't responded with anything new. The pass's Wolf write must still go through, otherwise Wolf would
     * stay stale (errors registered) until the server eventually responded.
     */
    @Test
    fun `Wolf clears via pending-edit-adjusted snapshot without waiting for new server diagnostics`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="error">hello</error> world
      """.trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val uri = serverSession.fileUri(virtualFile)

      // Phase 1: the server answers a single pull with an error; reactive path writes Wolf and bumps the gen.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == uri }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null)
        )))
      }

      checkHighlightingByPolling()

      waitUntilAssertSucceeds(message = "Wolf must register the file after the initial pull") {
        assertTrue(wolf.isProblemFile(virtualFile))
      }

      // Phase 2: delete the document. Do NOT register a second pull response — the next pull returns null,
      // which short-circuits the cache before any refresh / gen bump. The only remaining
      // path to clearing Wolf is the pass's pending-edit-adjusted (empty) snapshot at the same observedGen.
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.setText("")
      }
      val noErrors = createExpectedDataFromText("")
      (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(noErrors)

      waitUntilAssertSucceeds(
        message = "Wolf must unregister the file from the pass's pending-edit-driven write, without a new server response"
      ) {
        assertFalse(wolf.isProblemFile(virtualFile))
      }
    }
  }

  /**
   * Tests that LSP diagnostics with ranges not aligned to PsiElement boundaries are highlighted correctly.
   * Uses [SpaceTokenizingLanguage][com.intellij.platform.lsp.common.SpaceTokenizingLanguage] where each word is a separate PsiElement.
   */
  @Nested
  inner class MismatchedPsiElementRanges {
    @Suppress("unused")
    private val spaceTokenizingLang = spaceTokenizingLanguageFixture()

    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        diagnosticProvider = DiagnosticRegistrationOptions()
      },
    )

    @Test
    fun `diagnostic range partially covers a single word element`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val psiFile = codeInsightFixture.configureByText(SpaceTokenizingFileType, """
      he<error descr="mid-word error">ll</error>o <error descr="boundary error">wor</error>ld foo
      """.trimIndent())
      assertCustomPsiTree(psiFile)
      val virtualFile = psiFile.virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 2), Position(0, 4)), "mid-word error", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(0, 6), Position(0, 9)), "boundary error", DiagnosticSeverity.Error, null),
        )))
      }

      checkHighlightingByPolling()
    }

    @Test
    fun `diagnostic range spans across word elements and whitespace`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val psiFile = codeInsightFixture.configureByText(SpaceTokenizingFileType, """
      hel<error descr="cross-boundary error">lo wo</error>rld<error descr="trailing error"> foo</error>
      """.trimIndent())
      assertCustomPsiTree(psiFile)
      val virtualFile = psiFile.virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 3), Position(0, 8)), "cross-boundary error", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(0, 11), Position(0, 15)), "trailing error", DiagnosticSeverity.Error, null),
        )))
      }

      checkHighlightingByPolling()
    }
  }

  @Nested
  inner class DiagnosticsClearedOnServerStop {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture()

    @Test
    fun `diagnostics are cleared when the server stops`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", """
      <error descr="test error">hello</error> world
      """.trimIndent()).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val uri = serverSession.fileUri(virtualFile)

      val publishJob = launch(start = CoroutineStart.LAZY) {
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams(uri, listOf(
            Diagnostic(Range(Position(0, 0), Position(0, 5)), "test error", DiagnosticSeverity.Error, "fake")
          ))
        }
      }

      val verifyDiagnosticsShown = async(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying()
      }
      publishJob.start()
      verifyDiagnosticsShown.await()

      LspServerManager.getInstance(project).stopServers(FakeLspServerSupportProvider::class.java)

      val expectedClean = createExpectedDataFromText("hello world")
      (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(expectedClean)
    }
  }

  private fun createExpectedDataFromText(afterText: String): ExpectedHighlightingData {
    val document = DocumentImpl(StreamUtil.convertSeparators(afterText))
    val data = ExpectedHighlightingData(document, true, true, false)
    data.init()
    return data
  }

  private suspend fun checkHighlightingByPolling() {
    val fixture = codeInsightFixture as CodeInsightTestFixtureImpl
    val document = fixture.editor.document
    val data = ExpectedHighlightingData(document, true, true, false)
    data.init()

    waitUntilAssertSucceeds {
      fixture.collectAndCheckHighlighting(data)
    }
  }
}

private class ProblemEventRecorder : ProblemListener {
  val appeared: MutableSet<VirtualFile> = Collections.synchronizedSet(mutableSetOf())
  val changed: MutableSet<VirtualFile> = Collections.synchronizedSet(mutableSetOf())
  val disappeared: MutableSet<VirtualFile> = Collections.synchronizedSet(mutableSetOf())

  fun subscribe(project: Project, parent: Disposable): ProblemEventRecorder {
    project.messageBus.connect(parent).subscribe(ProblemListener.TOPIC, this)
    return this
  }

  override fun problemsAppeared(file: VirtualFile) { appeared.add(file) }
  override fun problemsChanged(file: VirtualFile) { changed.add(file) }
  override fun problemsDisappeared(file: VirtualFile) { disappeared.add(file) }
}
