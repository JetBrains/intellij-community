package com.jetbrains.lsp.test

import com.jetbrains.lsp.implementation.LspClient
import com.jetbrains.lsp.implementation.streamResults
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.ProgressParams
import com.jetbrains.lsp.protocol.ProgressToken
import com.jetbrains.lsp.protocol.RequestType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [streamResults] sends the results in `$/progress` batches. A failed flow still sends the results it found before the
 * failure, then rethrows it; a cancelled one sends nothing more.
 */
class StreamResultsTest {
  private val token = ProgressToken.string("t")

  @Test
  fun `completed flow sends all results`() = runTest {
    val client = RecordingClient()
    flow { emit(1); emit(2); delay(70); emit(3) }.stream(client)
    assertEquals(listOf(listOf(1, 2), listOf(3)), client.batches)
  }

  @Test
  fun `failed flow sends results found before the failure and rethrows`() = runTest {
    val client = RecordingClient()
    val failure = IllegalStateException("searcher failed")
    val thrown = assertFailsWith<IllegalStateException> {
      flow { emit(1); emit(2); throw failure }.stream(client)
    }
    assertTrue(thrown === failure, "the original failure is rethrown: $thrown")
    assertEquals(listOf(listOf(1, 2)), client.batches)
  }

  @Test
  fun `failed flow sends results of earlier batches and of the last one`() = runTest {
    val client = RecordingClient()
    assertFailsWith<IllegalStateException> {
      flow { emit(1); delay(70); emit(2); throw IllegalStateException("searcher failed") }.stream(client)
    }
    assertEquals(listOf(listOf(1), listOf(2)), client.batches)
  }

  @Test
  fun `cancellation exception from flow is rethrown without the last batch`() = runTest {
    val client = RecordingClient()
    assertFailsWith<CancellationException> {
      flow { emit(1); delay(70); emit(2); throw CancellationException("search cancelled") }.stream(client)
    }
    assertEquals(listOf(listOf(1)), client.batches)
  }

  @Test
  fun `cancelled streaming sends nothing more`() = runTest {
    val client = RecordingClient()
    val job = launch { flow { emit(1); awaitCancellation() }.stream(client) }
    runCurrent()
    job.cancel()
    job.join()
    assertTrue(job.isCancelled)
    assertEquals(emptyList(), client.batches)
  }

  private suspend fun Flow<Int>.stream(client: RecordingClient) = streamResults(client, token, Int.serializer())

  private inner class RecordingClient : LspClient {
    val batches = mutableListOf<List<Int>>()

    override suspend fun <Params, Result, Error> request(requestType: RequestType<Params, Result, Error>, params: Params): Result =
      error("unexpected request ${requestType.method}")

    override fun <Params> notifyAsync(notificationType: NotificationType<Params>, params: Params) {
      error("unexpected async notification ${notificationType.method}")
    }

    override suspend fun <Params> notify(notificationType: NotificationType<Params>, params: Params) {
      assertEquals(LSP.ProgressNotificationType.method, notificationType.method)
      val progress = params as ProgressParams
      assertEquals(token, progress.token)
      batches += LSP.json.decodeFromJsonElement(ListSerializer(Int.serializer()), progress.value)
    }
  }
}
