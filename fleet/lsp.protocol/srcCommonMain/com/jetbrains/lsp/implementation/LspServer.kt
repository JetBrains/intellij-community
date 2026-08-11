package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.ExitNotificationType
import com.jetbrains.lsp.protocol.Initialize
import com.jetbrains.lsp.protocol.InitializeParams
import com.jetbrains.lsp.protocol.InitializeResult
import com.jetbrains.lsp.protocol.Shutdown
import fleet.util.async.Resource
import fleet.util.async.resource
import fleet.util.async.withCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

interface LspServer {
    val handlers: LspHandlers
    val initializeResult: InitializeResult
}

suspend fun serveLsp(
    incoming: ReceiveChannel<JsonElement>,
    outgoing: SendChannel<JsonElement>,
    handlers: (InitializeParams, LspClient) -> Resource<LspServer>,
) {
    withLspImpl(incoming, outgoing) { lspClient ->
        resource { cc ->
            withCoroutineScope { scope ->
                val handlersDef = AtomicReference<LspHandlers?>(null)
                val exitSignal = CompletableDeferred<Unit>()
                cc(object : LspHandlers {
                    override fun requestHandler(requestTypeName: String): LspRequestHandler<*, *, *>? =
                        when (requestTypeName) {
                            Initialize.method -> {
                                LspRequestHandler(Initialize) { initializeParams ->
                                    val def = CompletableDeferred<InitializeResult>()
                                    // `def` is a hot resource which we trust this coroutine with, 
                                    // we must ensure the coroutine is successfully started, thus ATOMIC
                                    scope.launch(start = CoroutineStart.ATOMIC) {
                                        try {
                                            handlers(initializeParams, lspClient).use { server ->
                                                handlersDef.store(server.handlers)
                                                def.complete(server.initializeResult)
                                                exitSignal.await()
                                            }
                                        } catch (initServerException: Throwable) {
                                            coroutineContext.ensureActive()
                                            // Before the result is published this is an `initialize` rejection: report it to the client
                                            // instead of tearing the session down. After that it is a real server failure — propagate.
                                            if (!def.completeExceptionally(initServerException)) {
                                                throw RuntimeException("Server has failed", initServerException)
                                            }
                                        }
                                    }
                                    def.await()
                                }
                            }

                            Shutdown.method -> {
                                LspRequestHandler(Shutdown) { _ -> null }
                            }

                            else -> {
                                handlersDef.load()?.requestHandler(requestTypeName)
                            }
                        }

                    override fun notificationHandler(notificationTypeName: String): LspNotificationHandler<*>? =
                        when (notificationTypeName) {
                            ExitNotificationType.method -> {
                                LspNotificationHandler(ExitNotificationType) {
                                    exitSignal.complete(Unit)
                                }
                            }

                            else -> {
                                handlersDef.load()?.notificationHandler(notificationTypeName)
                            }
                        }
                })
            }
        }
    }
}
