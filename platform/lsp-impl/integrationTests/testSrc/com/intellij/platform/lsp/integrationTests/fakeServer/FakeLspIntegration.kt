// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeServer

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.findFile
import com.intellij.driver.sdk.jdk.getSystemProperty
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.platform.lsp.integrationTests.util.ClientCustomization
import com.intellij.platform.lsp.integrationTests.util.LspIntegrationTestService
import com.intellij.platform.lsp.integrationTests.util.resetLspTestIde
import com.intellij.platform.lsp.integrationTests.util.waitForValue
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ServerCapabilities
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The fake LSP server for the tests that drive a real IDE.
 *
 * The IDE starts a real server process for the `txt` and `stok` files of the project, through the test-only plugin of
 * `community/platform/lsp-impl/integrationTests/idePlugin`. That process is [FakeLspServerRelay], and the server behind it is a [FakeLspServer]
 * in the test process. So a test sets up expectations with lambdas, as a test of `intellij.platform.lsp.impl.tests` does,
 * while the client, the editor and the highlighting are those of a running IDE.
 */
internal class FakeLspIntegration(private val driver: Driver, private val host: FakeLspServerHost, private val projectHome: Path) {
  private val scenarioCount = AtomicInteger()

  private val ideService: LspIntegrationTestService = driver.service(LspIntegrationTestService::class)

  init {
    val java = Path.of(driver.getSystemProperty("java.home"), "bin", "java").toString()
    ideService.configureServer(ParametersListUtil.join(host.relayCommand(java)), "txt,stok")
  }

  /**
   * Runs [test] with a fake server that starts when the test opens its first file.
   *
   * The server answers only the messages that the test expects. A message without a matching expectation fails the test,
   * unless its method is in [allowUnmatched]. An expectation that never matches fails the test too.
   * After the test the editors close and the server stops, so the next test gets a new server.
   *
   * [clientCustomization] is the name of a customization of the client in the IDE, one of [ClientCustomization].
   * The client runs in the IDE process, so a test can't pass an `LspCustomization` object.
   */
  fun scenario(
    configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
    clientCustomization: String = ClientCustomization.DEFAULT,
    allowUnmatched: ServerSessionProtocolScope.() -> Set<LspMethod> = { DOCUMENT_SYNC },
    test: FakeLspScenario.() -> Unit,
  ) {
    val state = FakeLspScenarioState(configureServerCapabilities, FakeLspProtocol.allowUnmatched().mapTo(HashSet()) { it.method })
    host.startScenario(state)
    try {
      ideService.setClientCustomization(clientCustomization)
      driver.withContext {
        ideFrame {
          FakeLspScenario(this, state, ideService, projectHome, "fake-lsp/${scenarioCount.incrementAndGet()}").test()
        }
      }
      state.awaitJobsAndExpectations()
    }
    finally {
      // While the fake server still answers, so the client shuts it down the regular way.
      runCatching {
        driver.resetLspTestIde()
        ideService.setClientCustomization(ClientCustomization.DEFAULT)
      }
      host.endScenario(state)
    }
    state.assertNoUnexpectedMessages()
  }
}

private object FakeLspProtocol : ServerSessionProtocolScope()

private fun FakeLspScenarioState.awaitJobsAndExpectations() {
  runBlocking {
    for (job in jobs) {
      withTimeout(JOB_TIMEOUT) { (job as Deferred<*>).await() }
    }
    withTimeoutOrNull(JOB_TIMEOUT) { pendingExpectations.forEach { it.second.join() } }
  }
  handlerFailures.firstOrNull()?.let { throw AssertionError("A request handler of the test failed", it) }
  val neverMatched = pendingExpectations.filter { it.second.isActive }.map { it.first }
  if (neverMatched.isNotEmpty()) {
    throw AssertionError("The fake LSP server never got the expected messages: $neverMatched")
  }
}

private fun FakeLspScenarioState.assertNoUnexpectedMessages() {
  val unmatched = unmatchedMessages.toList()
  if (unmatched.isNotEmpty()) {
    throw AssertionError("The fake LSP server got messages that the test did not expect. " +
                         "Expect them, or pass their methods to `allowUnmatched`:\n" + unmatched.joinToString("\n"))
  }
}

private val JOB_TIMEOUT = 30.seconds

/** What a test with the fake server can do: the receiver of the block of [FakeLspIntegration.scenario]. */
internal class FakeLspScenario(
  val frame: IdeaFrameUI,
  private val state: FakeLspScenarioState,
  private val ideService: LspIntegrationTestService,
  private val projectHome: Path,
  private val directory: String,
) {
  val driver: Driver get() = frame.driver

  /**
   * Creates a file in a directory of this test, and returns its path relative to the project directory.
   * The path identifies the file in the other functions.
   */
  fun addFile(name: String, text: String): String = step("Create $name") {
    val relativePath = "$directory/$name"
    val path = projectHome.resolve(relativePath)
    path.parent.createDirectories()
    path.writeText(text)
    // The test process writes the file, so the IDE doesn't know about it yet.
    checkNotNull(driver.utility(LocalFileSystemRef::class).getInstance().refreshAndFindFileByPath(path.toString())) {
      "The IDE doesn't see $path"
    }
    relativePath
  }

  /** Stops the fake server the regular way, with `shutdown` and `exit`. It starts again when the next file opens. */
  fun stopServers() {
    ideService.stopServers()
  }

  /** Whether WolfTheProblemSolver of the IDE knows [file] as a file with problems. */
  fun isProblemFile(file: String): Boolean = ideService.isProblemFile(projectHome.resolve(file).toString())

  /** The name of the file type that the IDE gives to [file]. */
  fun fileTypeName(file: String): String = checkNotNull(driver.findFile(file)) { "The IDE doesn't see $file" }.getFileType().getName()

  /** Opens [file] in the editor. With no server running, that starts one. */
  fun openFile(file: String): JEditorUiComponent {
    // No wait for the code analysis of the daemon: a test waits for what it checks.
    driver.openFile(file, waitForCodeAnalysis = false)
    return frame.codeEditorForFile(file.substringAfterLast('/'))
  }

  /** Opens [file] in the editor and waits until the server got `didOpen` for it. */
  fun FakeLspServerSession.openFile(file: String): JEditorUiComponent {
    val opened = expectNotification(DID_OPEN) { it.textDocument.uri.endsWith("/$file") }
    val editor = this@FakeLspScenario.openFile(file)
    opened.awaitBlocking("didOpen of $file")
    return editor
  }

  /**
   * Waits until a fake server got `didOpen` for [file], and returns its session.
   *
   * The client sends some requests right after `didOpen`, e.g. the diagnostic pull, so expectations of them race with it.
   * Set those up on [unstartedServerSession] instead.
   */
  fun serverSession(file: String): FakeLspServerSession {
    waitForValue("The fake LSP server got didOpen of $file", getter = { runningServerOf(file) }, checker = { it != null })
    val server = checkNotNull(runningServerOf(file))
    return FakeLspServerSession(server.expectations, state.pendingExpectations) { server }
  }

  private fun runningServerOf(file: String): FakeLspServer? = state.servers.lastOrNull { it.hasOpened(file) }

  /**
   * Returns the session of the fake server that starts next, without waiting for it.
   *
   * Call it before the file that starts the server opens, so the expectations are in place before the client sends anything.
   * Until the server starts, the session can only set up expectations. The predicates and handlers run when a message arrives,
   * so they can use the whole session.
   */
  fun unstartedServerSession(): FakeLspServerSession {
    val unstartedServer = UnstartedServer()
    check(state.unstartedServer.compareAndSet(null, unstartedServer)) { "unstartedServerSession() is already called, and no server has started since" }
    return FakeLspServerSession(unstartedServer.expectations, state.pendingExpectations) {
      unstartedServer.server ?: error("The fake LSP server has not started yet. Await an expectation, e.g. of didOpen, first")
    }
  }

  /** Runs [block] next to the test, e.g. to send notifications after an expected message arrives. Its failure fails the test. */
  fun launch(block: suspend CoroutineScope.() -> Unit): Job = state.scope.async(block = block).also { state.jobs.add(it) }

  fun Deferred<Unit>.awaitBlocking(what: String, timeout: Duration = 30.seconds) {
    runBlocking {
      withTimeoutOrNull(timeout) { await() } ?: throw AssertionError("The fake LSP server did not get $what in $timeout")
    }
  }

  /**
   * Waits until the editor has the text of [markup] and exactly its errors and warnings.
   * The markup is the one of the highlighting tests: `<error descr="message">text</error>`.
   */
  fun JEditorUiComponent.waitForHighlighting(markup: String, timeout: Duration = 30.seconds) {
    waitForHighlights(parseHighlightingMarkup(markup), timeout) { errorsAndWarnings() }
  }

  /**
   * Waits until the editor has the text of [markup] and, of the highlights with the text attributes keys of [markup]
   * and of [absentKeys], exactly those of [markup]: `<text_attr textAttributesKey="KEY">text</text_attr>`.
   * The editor has highlights of its own, e.g. of the spellchecker, so the check is limited to the named keys.
   */
  fun JEditorUiComponent.waitForTextAttributes(markup: String, absentKeys: Set<String> = emptySet(), timeout: Duration = 30.seconds) {
    val expected = parseHighlightingMarkup(markup)
    val keys = expected.highlights.mapTo(HashSet()) { it.kind } + absentKeys
    waitForHighlights(expected, timeout) { highlightsWithKeys(keys) }
  }

  private fun JEditorUiComponent.waitForHighlights(expected: HighlightingMarkup, timeout: Duration, actualHighlights: () -> List<Highlight>) {
    try {
      waitFor("The editor has the expected text and highlighting", timeout,
              getter = { text to actualHighlights() },
              checker = { (text, highlights) -> text == expected.text && sameHighlights(expected.highlights, highlights) })
    }
    catch (e: Exception) {
      assertThat(text).describedAs("The text of the editor").isEqualTo(expected.text)
      val actual = actualHighlights()
      if (!sameHighlights(expected.highlights, actual)) {
        throw AssertionError("Expected highlights:\n${expected.highlights.joinToString("\n")}\n" +
                             "Actual highlights:\n${actual.joinToString("\n")}\n" +
                             "All highlights of the editor:\n${allHighlights().joinToString("\n")}", e)
      }
    }
  }

  /** The errors and warnings in the editor. Other highlights, e.g. the typos of the spellchecker, are not from the server. */
  fun JEditorUiComponent.errorsAndWarnings(): List<Highlight> =
    allHighlights().filter { it.severity in SERVER_SEVERITIES }
      .map { Highlight(it.severity, it.startOffset, it.endOffset, it.description) }.sorted()

  fun JEditorUiComponent.highlightsWithKeys(keys: Set<String>): List<Highlight> =
    allHighlights().filter { it.key in keys }.map { Highlight(it.key!!, it.startOffset, it.endOffset, null) }.sorted()

  private fun List<Highlight>.sorted(): List<Highlight> = sortedWith(compareBy({ it.startOffset }, { it.endOffset }, { it.description }))

  private fun JEditorUiComponent.allHighlights(): List<EditorHighlight> =
    getAllHighlights().mapNotNull { info ->
      val highlighter = info.getHighlighter() ?: return@mapNotNull null
      // The remote call returns null for a highlighter without a key, although the SDK declares the type as not null.
      val key = runCatching { highlighter.getTextAttributesKey().getExternalName() }.getOrNull()
                ?: runCatching { info.getType().getAttributesKey().getExternalName() }.getOrNull()
      EditorHighlight(info.getSeverity().getName(), key, highlighter.getStartOffset(), highlighter.getEndOffset(), info.getDescription())
    }

  private fun sameHighlights(expected: List<Highlight>, actual: List<Highlight>): Boolean {
    if (expected.size != actual.size) return false
    val notMatched = actual.toMutableList()
    return expected.all { expectedHighlight -> notMatched.remove(notMatched.firstOrNull { expectedHighlight.matches(it) }) }
  }
}

private data class EditorHighlight(val severity: String, val key: String?, val startOffset: Int, val endOffset: Int, val description: String?)

private val SERVER_SEVERITIES = setOf("ERROR", "WARNING")

@Remote("com.intellij.openapi.vfs.LocalFileSystem")
internal interface LocalFileSystemRef {
  fun getInstance(): LocalFileSystemRef
  fun refreshAndFindFileByPath(path: String): VirtualFile?
}
