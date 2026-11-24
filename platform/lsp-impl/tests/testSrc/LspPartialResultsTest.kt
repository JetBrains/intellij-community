// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.UnansweredRequest
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * The `$/progress` partial results of [LspClient.sendRequestWithPartialResults].
 *
 * The tests send `textDocument/references`, a standard request whose params carry a `partialResultToken`. Each chunk holds one
 * [Location] whose line number names the chunk, so an assert can tell the chunks of one request from the chunks of another.
 */
@TestApplication
internal class LspPartialResultsTest {
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
  fun `the chunks of a request arrive before its final result`() = timeoutRunBlocking(30.seconds) {
    val virtualFile = codeInsightFixture.configureByText("partial-results.txt", "hello").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)
    val lspClient = fakeLspIntegration.client

    serverSession.expectRequest(serverSession.REFERENCES) { params ->
      val token = checkNotNull(params.partialResultToken) { "the client sent no partialResultToken" }
      for (line in 0..2) {
        serverSession.sendNotification(serverSession.PROGRESS) { chunk(token, fileUri, line) }
      }
      listOf(location(fileUri, 99))
    }

    val chunks = ChunkRecorder()
    val result = lspClient.sendRequestWithPartialResults(chunks) { server, token ->
      server.textDocumentService.references(referenceParams(lspClient, virtualFile, line = 0, token))
    }

    // The server sends the chunks before the response, and the client handles the messages in that order
    assertEquals(listOf(0, 1, 2), chunks.lines())
    assertEquals(listOf(99), result?.map { it.range.start.line })
    assertTrue(chunks.texts().first().contains(fileUri), "The payload of a chunk reaches the consumer as the server sent it")
  }

  @Test
  fun `a progress notification of an unknown token goes to the work-done progress`() = timeoutRunBlocking(60.seconds) {
    val virtualFile = codeInsightFixture.configureByText("unknown-token.txt", "hello").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)
    val lspClient = fakeLspIntegration.client

    val unanswered = UnansweredRequest()
    val requestArrived = serverSession.expectRequest(serverSession.REFERENCES) { unanswered.hold() }

    val chunks = ChunkRecorder()
    val caller = launch {
      lspClient.sendRequestWithPartialResults(chunks) { server, token ->
        server.textDocumentService.references(referenceParams(lspClient, virtualFile, line = 0, token))
      }
    }
    requestArrived.await()

    val managerJob = LspClientManagerImpl.getInstanceImpl(project).cs.coroutineContext.job
    val jobsBefore = managerJob.children.toSet()
    serverSession.sendNotification(serverSession.PROGRESS) { workDoneBegin("a-token-of-the-server") }

    // Begin of an unknown token starts a progress job in the manager scope, as it does without any partial result request
    val progressJob = awaitNewProgressJob(managerJob, jobsBefore)
    serverSession.sendNotification(serverSession.PROGRESS) { workDoneEnd("a-token-of-the-server") }
    progressJob.join()

    assertEquals(emptyList<Int>(), chunks.lines(), "a work-done notification must not reach the partial result consumer")

    caller.cancel()
    caller.join()
  }

  @Test
  fun `a cancelled request stops its partial results and reaches the server`() = timeoutRunBlocking(30.seconds) {
    val virtualFile = codeInsightFixture.configureByText("cancelled-request.txt", "hello").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)
    val lspClient = fakeLspIntegration.client

    val unanswered = UnansweredRequest()
    val tokenOfTheRequest = CompletableDeferred<Either<String, Int>>()
    serverSession.expectRequest(serverSession.REFERENCES) { params ->
      tokenOfTheRequest.complete(checkNotNull(params.partialResultToken) { "the client sent no partialResultToken" })
      unanswered.hold()
    }

    val chunks = ChunkRecorder()
    val caller = launch {
      lspClient.sendRequestWithPartialResults(chunks) { server, token ->
        server.textDocumentService.references(referenceParams(lspClient, virtualFile, line = 0, token))
      }
    }
    val token = tokenOfTheRequest.await()

    caller.cancel()
    caller.join()
    waitUntilAssertSucceeds(message = "the client must cancel the abandoned request via $/cancelRequest") {
      assertTrue(unanswered.isCancelled)
    }

    serverSession.sendNotification(serverSession.PROGRESS) { chunk(token, fileUri, line = 7) }
    serverSession.awaitNotificationsHandled()

    assertEquals(emptyList<Int>(), chunks.lines(), "the cancelled request must no longer hold its partial result token")
  }

  @Test
  fun `two requests in flight keep their chunks apart`() = timeoutRunBlocking(30.seconds) {
    val virtualFile = codeInsightFixture.configureByText("two-requests.txt", "hello").virtualFile
    val serverSession = fakeLspIntegration.serverSession(virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)
    val lspClient = fakeLspIntegration.client

    // Each request asks at its own position, so the server can tell them apart; the first one waits for the second one
    val secondRequestArrived = CompletableDeferred<Unit>()
    val tokens = Collections.synchronizedList(mutableListOf<Either<String, Int>>())

    serverSession.expectRequest(serverSession.REFERENCES, { it.position.line == 10 }) { params ->
      val token = checkNotNull(params.partialResultToken) { "the client sent no partialResultToken" }
      tokens.add(token)
      secondRequestArrived.await()
      serverSession.sendNotification(serverSession.PROGRESS) { chunk(token, fileUri, line = 11) }
      serverSession.sendNotification(serverSession.PROGRESS) { chunk(token, fileUri, line = 12) }
      listOf(location(fileUri, 13))
    }
    serverSession.expectRequest(serverSession.REFERENCES, { it.position.line == 20 }) { params ->
      val token = checkNotNull(params.partialResultToken) { "the client sent no partialResultToken" }
      tokens.add(token)
      secondRequestArrived.complete(Unit)
      serverSession.sendNotification(serverSession.PROGRESS) { chunk(token, fileUri, line = 21) }
      listOf(location(fileUri, 22))
    }

    val firstChunks = ChunkRecorder()
    val secondChunks = ChunkRecorder()
    val first = async {
      lspClient.sendRequestWithPartialResults(firstChunks) { server, token ->
        server.textDocumentService.references(referenceParams(lspClient, virtualFile, line = 10, token))
      }
    }
    val second = async {
      lspClient.sendRequestWithPartialResults(secondChunks) { server, token ->
        server.textDocumentService.references(referenceParams(lspClient, virtualFile, line = 20, token))
      }
    }

    assertEquals(listOf(13), first.await()?.map { it.range.start.line })
    assertEquals(listOf(22), second.await()?.map { it.range.start.line })
    assertEquals(listOf(11, 12), firstChunks.lines())
    assertEquals(listOf(21), secondChunks.lines())
    assertNotEquals(tokens[0], tokens[1], "each request must get its own partial result token")
  }

  /**
   * Collects the payload of each chunk. The consumer runs on the thread that reads the messages of the server, so the list
   * is synchronized.
   *
   * `lsp4j` reads the payload as a `com.google.gson.JsonElement`, which this module cannot name, so the recorder keeps its
   * JSON text. [lines] reads back the line number that names the chunk.
   */
  private class ChunkRecorder : (ProgressParams) -> Unit {
    private val payloads = Collections.synchronizedList(mutableListOf<String>())

    override fun invoke(params: ProgressParams) {
      payloads.add(params.value.right.toString())
    }

    fun texts(): List<String> = synchronized(payloads) { payloads.toList() }

    fun lines(): List<Int> = texts().map { payload ->
      val line = LINE_IN_PAYLOAD.find(payload) ?: error("No line number in the partial result payload: $payload")
      line.groupValues[1].toInt()
    }

    private companion object {
      val LINE_IN_PAYLOAD: Regex = Regex("\"line\":(\\d+)")
    }
  }

  private fun referenceParams(lspClient: LspClient, file: VirtualFile, line: Int, token: Either<String, Int>): ReferenceParams =
    ReferenceParams(lspClient.getDocumentIdentifier(file), Position(line, 0), ReferenceContext(true)).apply {
      partialResultToken = token
    }

  private fun location(fileUri: String, line: Int): Location = Location(fileUri, Range(Position(line, 0), Position(line, 1)))

  /** One streamed chunk of `textDocument/references`: the partial result payload is in the right part of the value. */
  private fun chunk(token: Either<String, Int>, fileUri: String, line: Int): ProgressParams =
    ProgressParams(token, Either.forRight(listOf(location(fileUri, line))))

  private fun workDoneBegin(token: String): ProgressParams =
    ProgressParams(Either.forLeft(token), Either.forLeft<WorkDoneProgressNotification, Any>(WorkDoneProgressBegin().apply { title = "Indexing" }))

  private fun workDoneEnd(token: String): ProgressParams =
    ProgressParams(Either.forLeft(token), Either.forLeft<WorkDoneProgressNotification, Any>(WorkDoneProgressEnd()))

  /**
   * Suspends until the client has handled every notification sent before this call. The client answers a request on the same
   * thread that reads the notifications, and the messages arrive in order, so its answer proves that the notifications ran.
   */
  private suspend fun FakeLspServerSession.awaitNotificationsHandled() {
    sendRequest(CONFIGURATION) { ConfigurationParams(listOf(ConfigurationItem())) }
  }

  private suspend fun awaitNewProgressJob(managerJob: Job, jobsBefore: Set<Job>): Job =
    withTimeout(10.seconds) {
      var job: Job? = null
      while (job == null) {
        job = (managerJob.children.toSet() - jobsBefore).firstOrNull { it.isActive }
        if (job == null) delay(50)
      }
      job
    }
}
