// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeScenario

import com.intellij.driver.sdk.invokeAction
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspIntegration
import com.intellij.platform.lsp.integrationTests.fakeServer.UnansweredRequest
import com.intellij.platform.lsp.integrationTests.util.SharedLspIde
import com.intellij.platform.lsp.integrationTests.util.typeAt
import com.intellij.platform.lsp.integrationTests.util.typeOver
import com.intellij.platform.lsp.integrationTests.util.waitForValue
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticRegistrationOptions
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.RelatedUnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.Unregistration
import org.eclipse.lsp4j.UnregistrationParams
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The cases of `LspDiagnosticsTest` of `intellij.platform.lsp.impl.tests`, in a running IDE instead of the unit test mode:
 * the test types in the editor and reads the highlights of the editor. The server is still the fake one.
 */
@ExtendWith(SharedLspIde::class)
internal class LspFakeScenarioDiagnosticsTest {
  @Nested
  inner class PublishDiagnostics {
    @Test
    fun `publish diagnostics twice for the same document version`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario {
      val file = addFile("test.txt", "hello world")
      val editor = openFile(file)
      val serverSession = serverSession(file)
      val uri = serverSession.fileUri(file)

      val didChange = serverSession.expectNotification(serverSession.DID_CHANGE) { it.textDocument.uri == uri }
      launch {
        didChange.await()
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

      editor.typeAt(0, "!")

      editor.waitForHighlighting("""<error descr="foo">!</error>hello world""")
    }

    @Test
    fun `zero-length diagnostics`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario {
      val file = addFile("test.txt", ZERO_LENGTH_TEXT)
      val editor = openFile(file)
      val serverSession = serverSession(file)

      serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
        PublishDiagnosticsParams(serverSession.fileUri(file), ZERO_LENGTH_DIAGNOSTICS)
      }

      editor.waitForHighlighting(ZERO_LENGTH_HIGHLIGHTING)
    }

    @Test
    fun `error range update on typing`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario {
      val file = addFile("test.txt", EIGHT_WARNINGS_TEXT)
      val editor = openFile(file)
      val serverSession = serverSession(file)

      serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
        PublishDiagnosticsParams(serverSession.fileUri(file), EIGHT_WARNINGS)
      }
      editor.waitForHighlighting(EIGHT_WARNINGS_HIGHLIGHTING)

      // The server publishes nothing after the edits, so the editor shows the warnings that survive, at their new positions.
      editor.typeAt(14, "X")
      editor.typeOver(24, 33, "Y") // removes "ext-3xl" and the line break
      editor.typeAt(42, "Q")
      editor.typeAt(51, "W")
      editor.typeAt(61, "\nfoo\nbar")

      editor.waitForHighlighting(WARNINGS_AFTER_EDITS_HIGHLIGHTING)
    }

    @Test
    fun `diagnostic in freshly typed region is shown`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario {
      val file = addFile("test.txt", "hello world")
      val editor = openFile(file)
      val serverSession = serverSession(file)

      editor.typeAt(editor.text.length, "\nBOOM")
      serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
        PublishDiagnosticsParams(serverSession.fileUri(file), listOf(
          Diagnostic(Range(Position(1, 0), Position(1, 4)), "boom error", DiagnosticSeverity.Error, "fake")
        ))
      }

      editor.waitForHighlighting("hello world\n<error descr=\"boom error\">BOOM</error>")
    }

    @Test
    fun `diagnostics are cleared when the server stops`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario {
      val file = addFile("test.txt", "hello world")
      val editor = openFile(file)
      val serverSession = serverSession(file)

      serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
        PublishDiagnosticsParams(serverSession.fileUri(file), listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "test error", DiagnosticSeverity.Error, "fake")
        ))
      }
      editor.waitForHighlighting("""<error descr="test error">hello</error> world""")

      stopServers()

      editor.waitForHighlighting("hello world")
    }
  }

  @Nested
  inner class PullDiagnostics {
    private val pullDiagnostics: ServerCapabilities.() -> Unit = { diagnosticProvider = DiagnosticRegistrationOptions() }

    @Test
    fun `basic pull diagnostics`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "error message", DiagnosticSeverity.Error, null))
      }
      val editor = serverSession.openFile(file)

      editor.waitForHighlighting("""<error descr="error message">hello</error> world""")
    }

    @Test
    fun `zero-length pull diagnostics`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", ZERO_LENGTH_TEXT)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(ZERO_LENGTH_DIAGNOSTICS))
      }
      val editor = serverSession.openFile(file)

      editor.waitForHighlighting(ZERO_LENGTH_HIGHLIGHTING)
    }

    @Test
    fun `empty pull response clears previously reported errors`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null))
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting("""<error descr="error">hello</error> world""")

      // The client must apply the empty reply, and not treat it as "no response".
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport()
      }
      editor.typeAt(0, " ")

      editor.waitForHighlighting(" hello world")
    }

    @Test
    fun `error range update on typing`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(
      pullDiagnostics,
      // Each typed character can cause a pull, and how many go out depends on the timing. The fake server answers them with `null`,
      // so the client keeps the warnings that it adjusted to the edits.
      allowUnmatched = { DOCUMENT_SYNC + DIAGNOSTIC },
    ) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", EIGHT_WARNINGS_TEXT)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(EIGHT_WARNINGS))
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting(EIGHT_WARNINGS_HIGHLIGHTING)

      editor.typeAt(14, "X")
      editor.typeOver(24, 33, "Y") // removes "ext-3xl" and the line break
      editor.typeAt(42, "Q")
      editor.typeAt(51, "W")
      editor.typeAt(61, "\nfoo\nbar")

      editor.waitForHighlighting(WARNINGS_AFTER_EDITS_HIGHLIGHTING)
    }

    @Test
    fun `superseded pull is cancelled on the server`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      val pendingResponse = UnansweredRequest()
      val firstRequestArrived = serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        pendingResponse.hold()
      }
      val editor = serverSession.openFile(file)
      firstRequestArrived.awaitBlocking("the first pull")

      // The edit makes the pending pull obsolete. The client must cancel it and send a new pull.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport(Diagnostic(Range(Position(0, 1), Position(0, 6)), "error", DiagnosticSeverity.Error, null))
      }
      editor.typeAt(0, " ")

      editor.waitForHighlighting(""" <error descr="error">hello</error> world""")
      waitForValue("The client cancels the superseded pull with $/cancelRequest",
                   getter = { pendingResponse.isCancelled }, checker = { it })
    }

    @Test
    fun `previousResultId is sent on the next pull`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null), resultId = "r1")
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting("""<error descr="error">hello</error> world""")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == "r1" }) {
        fullReport(resultId = "r2")
      }
      editor.typeAt(0, " ")

      editor.waitForHighlighting(" hello world")
    }

    @Test
    fun `unchanged report keeps the diagnostics`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null), resultId = "r1")
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting("""<error descr="error">hello</error> world""")

      // The space at the end leaves the range of the error in place.
      val unchangedRequest = serverSession.expectRequest(serverSession.DIAGNOSTIC,
                                                         { it.textDocument.uri.endsWith("/$file") && it.previousResultId == "r1" }) {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport("r1"))
      }
      editor.typeAt(editor.text.length, " ")
      unchangedRequest.awaitBlocking("the pull with the result ID r1")

      // The next pull still carries r1, so the client kept the result that the server called unchanged.
      val nextRequest = serverSession.expectRequest(serverSession.DIAGNOSTIC,
                                                    { it.textDocument.uri.endsWith("/$file") && it.previousResultId == "r1" }) {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport("r1"))
      }
      editor.typeAt(editor.text.length, " ")
      nextRequest.awaitBlocking("the second pull with the result ID r1")

      editor.waitForHighlighting("""<error descr="error">hello</error> world  """)
    }

    @Test
    fun `workspace diagnostic refresh triggers a full re-pull`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "stale", DiagnosticSeverity.Error, null), resultId = "r1")
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting("""<error descr="stale">hello</error> world""")

      // The forced re-pull must not send previousResultId, so the server answers with a full report.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "fresh", DiagnosticSeverity.Error, null))
      }
      serverSession.sendRequest(serverSession.WORKSPACE_DIAGNOSTIC_REFRESH) { }

      // Nobody edited the document. The fresh diagnostics must arrive anyway.
      editor.waitForHighlighting("""<error descr="fresh">hello</error> world""")
    }

    @Test
    fun `workspace diagnostic refresh supersedes the in-flight pull`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      val pendingResponse = UnansweredRequest()
      val firstRequestArrived = serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        pendingResponse.hold()
      }
      val editor = serverSession.openFile(file)
      firstRequestArrived.awaitBlocking("the first pull")

      // The forced re-pull must go out although a pull is in flight, and it must carry no previousResultId.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "fresh", DiagnosticSeverity.Error, null))
      }
      serverSession.sendRequest(serverSession.WORKSPACE_DIAGNOSTIC_REFRESH) { }

      waitForValue("The forced refresh cancels the in-flight pull", getter = { pendingResponse.isCancelled }, checker = { it })
      editor.waitForHighlighting("""<error descr="fresh">hello</error> world""")
    }

    @Test
    fun `re-registering the diagnostic capability triggers a full re-pull`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "stale", DiagnosticSeverity.Error, null), resultId = "r1")
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting("""<error descr="stale">hello</error> world""")

      // The re-pull forced by the new registration must not send previousResultId either.
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "fresh", DiagnosticSeverity.Error, null))
      }
      serverSession.sendRequest(serverSession.UNREGISTER_CAPABILITY) {
        UnregistrationParams(listOf(Unregistration("diagnostic-id", "textDocument/diagnostic")))
      }
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("diagnostic-id", "textDocument/diagnostic", DiagnosticRegistrationOptions())))
      }

      editor.waitForHighlighting("""<error descr="fresh">hello</error> world""")
    }

    @Test
    fun `reopened file triggers a full re-pull`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "stale", DiagnosticSeverity.Error, null), resultId = "r1")
      }
      serverSession.openFile(file).waitForHighlighting("""<error descr="stale">hello</error> world""")

      val didClose = serverSession.expectNotification(serverSession.DID_CLOSE) { it.textDocument.uri.endsWith("/$file") }
      driver.invokeAction("CloseAllEditors")
      didClose.awaitBlocking("didClose of $file")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") && it.previousResultId == null }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "fresh", DiagnosticSeverity.Error, null))
      }

      openFile(file).waitForHighlighting("""<error descr="fresh">hello</error> world""")
    }

    @Test
    fun `error diagnostics are reported to WolfTheProblemSolver, and cleared by a fresh server response`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "error", DiagnosticSeverity.Error, null))
      }
      val editor = serverSession.openFile(file)
      editor.waitForHighlighting("""<error descr="error">hello</error> world""")
      waitForValue("WolfTheProblemSolver knows the file after the first error", getter = { isProblemFile(file) }, checker = { it })

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport()
      }
      editor.typeAt(0, " ")

      editor.waitForHighlighting(" hello world")
      waitForValue("WolfTheProblemSolver forgets the file once the errors are cleared", getter = { isProblemFile(file) }, checker = { !it })
    }

    /** In a file of the test language each word is a separate PSI element, so these ranges don't match element boundaries. */
    @Test
    fun `diagnostic range partially covers a single word element`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.stok", "hello world foo")
      assertThat(fileTypeName(file)).isEqualTo("SpaceTokenizing")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport(
          Diagnostic(Range(Position(0, 2), Position(0, 4)), "mid-word error", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(0, 6), Position(0, 9)), "boundary error", DiagnosticSeverity.Error, null),
        )
      }
      val editor = serverSession.openFile(file)

      editor.waitForHighlighting("""he<error descr="mid-word error">ll</error>o <error descr="boundary error">wor</error>ld foo""")
    }

    @Test
    fun `diagnostic range spans across word elements and whitespace`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val file = addFile("test.stok", "hello world foo")
      assertThat(fileTypeName(file)).isEqualTo("SpaceTokenizing")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$file") }) {
        fullReport(
          Diagnostic(Range(Position(0, 3), Position(0, 8)), "cross-boundary error", DiagnosticSeverity.Error, null),
          Diagnostic(Range(Position(0, 11), Position(0, 15)), "trailing error", DiagnosticSeverity.Error, null),
        )
      }
      val editor = serverSession.openFile(file)

      editor.waitForHighlighting("""hel<error descr="cross-boundary error">lo wo</error>rld<error descr="trailing error"> foo</error>""")
    }

    @Test
    fun `second opened file gets its own pull`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(pullDiagnostics) {
      val serverSession = unstartedServerSession()
      val firstFile = addFile("first.txt", "hello")
      val newFile = addFile("new.txt", "hello world")

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$firstFile") }) {
        fullReport()
      }
      serverSession.openFile(firstFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri.endsWith("/$newFile") }) {
        fullReport(Diagnostic(Range(Position(0, 0), Position(0, 5)), "on-open error", DiagnosticSeverity.Error, null))
      }

      openFile(newFile).waitForHighlighting("""<error descr="on-open error">hello</error> world""")
    }
  }
}

private fun fullReport(vararg diagnostics: Diagnostic, resultId: String? = null): DocumentDiagnosticReport =
  DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(diagnostics.toList()).also { it.resultId = resultId })

// In the unit test mode the empty range at the start of the first line stays empty. The editor of a running IDE shows it one character wide.
private val ZERO_LENGTH_HIGHLIGHTING = """
  <error descr="error 0">l</error>ine 0<EOLError descr="EOL error 0"></EOLError>
  <EOLError descr="EOL error 1"></EOLError>
  line 2<EOLError descr="EOL error 2"></EOLError>
  line 3<EOLError descr="EOF error"></EOLError><EOLError descr="EOL error 3"></EOLError>""".trimIndent()

private const val ZERO_LENGTH_TEXT = "line 0\n\nline 2\nline 3"

private val ZERO_LENGTH_DIAGNOSTICS = listOf(
  Diagnostic(Range(Position(0, 0), Position(0, 0)), "error 0", DiagnosticSeverity.Error, null),
  Diagnostic(Range(Position(0, 6), Position(0, 6)), "EOL error 0", DiagnosticSeverity.Error, null),
  Diagnostic(Range(Position(1, 0), Position(1, 0)), "EOL error 1", DiagnosticSeverity.Error, null),
  Diagnostic(Range(Position(2, 100), Position(2, 200)), "EOL error 2", DiagnosticSeverity.Error, null),
  Diagnostic(Range(Position(3, 6), Position(3, 6)), "EOL error 3", DiagnosticSeverity.Error, null),
  Diagnostic(Range(Position(4, 0), Position(4, 0)), "EOF error", DiagnosticSeverity.Error, null),
  Diagnostic(Range(Position(5, 0), Position(5, 0)), "out of range -> ignored", DiagnosticSeverity.Error, null),
)

private val EIGHT_WARNINGS_HIGHLIGHTING = """
  <div class="
  <warning descr="w1">text-2xl</warning>
  <warning descr="w2">text-3xl</warning>
  <warning descr="w3">text-4xl</warning> <warning descr="w4">text-5xl</warning>
  <warning descr="w5">text-6xl</warning>
  <warning descr="w6">text-7xl</warning> <warning descr="w7">text-8xl</warning>
  <warning descr="w8">text-9xl</warning>
  "></div>""".trimIndent()

private val EIGHT_WARNINGS_TEXT = EIGHT_WARNINGS_HIGHLIGHTING.replace(Regex("</?warning[^>]*>"), "")

private val EIGHT_WARNINGS = listOf(
  Diagnostic(Range(Position(1, 0), Position(1, 8)), "w1", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(2, 0), Position(2, 8)), "w2", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(3, 0), Position(3, 8)), "w3", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(3, 9), Position(3, 17)), "w4", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(4, 0), Position(4, 8)), "w5", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(5, 0), Position(5, 8)), "w6", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(5, 9), Position(5, 17)), "w7", DiagnosticSeverity.Warning, "fake"),
  Diagnostic(Range(Position(6, 0), Position(6, 8)), "w8", DiagnosticSeverity.Warning, "fake"),
)

// Enter is typed before the space between "text-7xl" and "text-8xl". The editor takes that space for the indent of the new line
// and keeps it on the next line too. A test in the unit test mode inserts the text into the document and gets no indent.
private val WARNINGS_AFTER_EDITS_HIGHLIGHTING = """
  <div class="
  <warning descr="w1">tXext-2xl</warning>
  tYext-4xl <warning descr="w4">text-5xl</warning>
  Q<warning descr="w5">text-6xl</warning>W
  <warning descr="w6">text-7xl</warning>
   foo
   bar<warning descr="w7">text-8xl</warning>
  <warning descr="w8">text-9xl</warning>
  "></div>""".trimIndent()
