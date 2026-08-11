package com.jetbrains.lsp.test

import com.jetbrains.lsp.implementation.lspHandlers
import com.jetbrains.lsp.implementation.withLsp
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.RequestMessage
import com.jetbrains.lsp.protocol.RequestType
import com.jetbrains.lsp.protocol.StringOrInt
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertTrue

class WithLspTest {
    private val pingType = RequestType("test/ping", Unit.serializer(), Unit.serializer(), Unit.serializer())

    /**
     * A request handler suspended in the response send must not leak an uncaught
     * [kotlinx.coroutines.channels.ClosedSendChannelException] when the connection is torn down
     * before the peer receives the response.
     */
    @Test
    fun `response send racing connection teardown does not leak uncaught exceptions`() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val exceptionHandler = CoroutineExceptionHandler { _, e -> uncaught.add(e) }
        val clientToServer = Channel<JsonElement>()
        val serverToClient = Channel<JsonElement>()
        val handlers = lspHandlers {
            request(pingType) { }
        }
        val server = launch(exceptionHandler) {
            withLsp(clientToServer, serverToClient, handlers) {
                awaitCancellation()
            }
        }

        val request = RequestMessage(id = StringOrInt.int(1), method = pingType.method, params = null)
        clientToServer.send(LSP.json.encodeToJsonElement(RequestMessage.serializer(), request))
        // let the handler respond and suspend in the response send: nobody receives on serverToClient
        testScheduler.advanceUntilIdle()
        // mirrors the teardown order of a real shutdown: the server closes its outgoing channel,
        // then the peer's consumeEach teardown cancels it; the null close cause makes the
        // suspended sender resume with ClosedSendChannelException rather than CancellationException
        serverToClient.close()
        serverToClient.cancel()
        testScheduler.advanceUntilIdle()
        server.cancelAndJoin()

        assertTrue(uncaught.isEmpty(), "uncaught exceptions leaked from LSP handlers: $uncaught")
    }
}
