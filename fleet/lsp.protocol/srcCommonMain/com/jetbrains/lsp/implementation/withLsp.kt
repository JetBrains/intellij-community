package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.*
import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class ProtocolViolation(message: String) : Exception(message)

private fun isRequest(jsonMessage: JsonObject): Boolean =
    jsonMessage.containsKey("id") && jsonMessage.containsKey("method")

private fun isResponse(jsonMessage: JsonObject): Boolean =
    jsonMessage.containsKey("id") && !jsonMessage.containsKey("method")

private fun isNotification(jsonMessage: JsonObject): Boolean =
    jsonMessage.containsKey("method") && !jsonMessage.containsKey("id")

private class OutgoingRequest(
    val deferred: CompletableDeferred<Any?>,
    val requestType: RequestType<*, *, *>,
)

suspend fun withLsp(
    incoming: ReceiveChannel<JsonElement>,
    outgoing: SendChannel<JsonElement>,
    handlers: LspHandlers,
    middleware: LspHandlersMiddleware = LspHandlersMiddleware.IDENTITY,
    createCoroutineContext: (LspClient) -> CoroutineContext = { EmptyCoroutineContext },
    body: suspend CoroutineScope.(LspClient) -> Unit,
) {
    coroutineScope {
        val outgoingRequests = ConcurrentHashMap<StringOrInt, OutgoingRequest>()
        val idGen = AtomicInt(0)
        val lspClient = object : LspClient {
            override suspend fun <Params, Result, Error> request(
                requestType: RequestType<Params, Result, Error>,
                params: Params,
            ): Result {
                val id = StringOrInt(JsonPrimitive(idGen.incrementAndFetch()))
                val deferred = CompletableDeferred<Any?>()
                outgoingRequests[id] = OutgoingRequest(deferred, requestType)
                val request = RequestMessage(
                    jsonrpc = "2.0",
                    id = id,
                    method = requestType.method,
                    params = LSP.json.encodeToJsonElement(requestType.paramsSerializer, params)
                )
                outgoing.send(LSP.json.encodeToJsonElement(RequestMessage.serializer(), request))
                @Suppress("UNCHECKED_CAST")
                return try {
                    deferred.await() as Result
                } catch (c: CancellationException) {
                    notify(LSP.CancelNotificationType, CancelParams(id))
                    outgoingRequests.remove(id)
                    throw c
                }
            }

            override fun <Params> notify(
                notificationType: NotificationType<Params>,
                params: Params,
            ) {
                val notification = NotificationMessage(
                    jsonrpc = "2.0",
                    method = notificationType.method,
                    params = LSP.json.encodeToJsonElement(notificationType.paramsSerializer, params)
                )
                outgoing.trySend(
                    LSP.json.encodeToJsonElement(
                        NotificationMessage.serializer(),
                        notification
                    )
                ).getOrThrow()
            }
        }

        val lspHandlerContext = LspHandlerContext(lspClient)

        launch(createCoroutineContext(lspClient)) {
            withSupervisor { supervisor ->
                val incomingRequestsJobs = ConcurrentHashMap<StringOrInt, Job>()
                incoming.consumeEach { jsonMessage ->
                    when {
                        jsonMessage !is JsonObject || jsonMessage["jsonrpc"] != JsonPrimitive("2.0") -> {
                            throw ProtocolViolation("not json rpc message: $jsonMessage")
                        }

                        isRequest(jsonMessage) -> {
                            val request = LSP.json.decodeFromJsonElement(RequestMessage.serializer(), jsonMessage)
                            supervisor.launch(start = CoroutineStart.ATOMIC) {
                                val maybeHandler = handlers.requestHandler(request.method)
                                    ?.let { handler -> middleware.requestHandler(handler) }
                                runCatching {
                                    val handler = requireNotNull(maybeHandler) {
                                        "no handler for request: ${request.method}"
                                    }
                                    val deserializedParams = request.params?.let { params ->
                                        LSP.json.decodeFromJsonElement(handler.requestType.paramsSerializer, params)
                                    }
                                    val result = (handler as LspRequestHandler<Any?, Any?, Any?>).handler(
                                        lspHandlerContext,
                                        this,
                                        deserializedParams
                                    )
                                    LSP.json.encodeToJsonElement(
                                        serializer = handler.requestType.resultSerializer as KSerializer<Any?>,
                                        value = result
                                    )
                                }.fold(
                                    onSuccess = { result ->
                                        ResponseMessage(
                                            jsonrpc = "2.0",
                                            id = request.id,
                                            result = result,
                                            error = null
                                        )
                                    },
                                    onFailure = { x ->
                                        val responseError = when (x) {
                                            is CancellationException -> {
                                                ResponseError(
                                                    code = ErrorCodes.RequestCancelled,
                                                    message = "cancelled",
                                                )
                                            }

                                            is LspException -> {
                                                ResponseError(
                                                    code = x.errorCode,
                                                    message = x.message ?: x::class.simpleName ?: "unknown error",
                                                    data = runCatching {
                                                        val errorSerializer = requireNotNull(maybeHandler) {
                                                            "we could not have caught LspException if we didn't find the handler"
                                                        }.requestType.errorSerializer as KSerializer<Any?>
                                                        LSP.json.encodeToJsonElement(
                                                            serializer = errorSerializer,
                                                            value = x.payload
                                                        )
                                                    }.getOrNull()
                                                )
                                            }

                                            else -> {
                                                LOG.error(x)

                                                ResponseError(
                                                    code = ErrorCodes.RequestFailed,
                                                    message = x.message ?: x::class.simpleName ?: "unknown error",
                                                )
                                            }
                                        }
                                        ResponseMessage(
                                            jsonrpc = "2.0",
                                            id = request.id,
                                            result = null,
                                            error = responseError
                                        )
                                    }
                                ).let { responseMessage ->
                                    outgoing.send(LSP.json.encodeToJsonElement(ResponseMessage.serializer(), responseMessage))
                                }
                            }.also { requestJob ->
                                incomingRequestsJobs.put(request.id, requestJob)
                                requestJob.invokeOnCompletion {
                                    incomingRequestsJobs.remove(request.id)
                                }
                            }
                        }

                        isResponse(jsonMessage) -> {
                            val response = LSP.json.decodeFromJsonElement(ResponseMessage.serializer(), jsonMessage)
                            when (val client = outgoingRequests.remove(response.id)) {
                                null -> {
                                    // request was cancelled
                                }

                                else -> {
                                    client.deferred.let { deferred ->
                                        when (val error = response.error) {
                                            null -> {
                                                val result = response.result?.let { result ->
                                                    runCatching {
                                                        LSP.json.decodeFromJsonElement(
                                                            client.requestType.resultSerializer as KSerializer<Any?>,
                                                            result
                                                        )
                                                    }.onFailure { error ->
                                                        if (error is CancellationException) throw error
                                                        LOG.error(error)
                                                    }.getOrNull()
                                                }
                                                deferred.complete(result)
                                            }

                                            else -> {
                                                deferred.completeExceptionally(
                                                    LspException(
                                                        message = error.message,
                                                        errorCode = error.code,
                                                        cause = null,
                                                        payload = error.data?.let { data ->
                                                            runCatching {
                                                                LSP.json.decodeFromJsonElement(
                                                                    client.requestType.errorSerializer as KSerializer<Any?>,
                                                                    data
                                                                )
                                                            }.onFailure { decodingError ->
                                                                if (decodingError is CancellationException) throw decodingError
                                                                LOG.error(decodingError)
                                                            }.getOrNull()
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        isNotification(jsonMessage) -> {
                            //todo: separate queue for notifications
                            val notification = LSP.json.decodeFromJsonElement(NotificationMessage.serializer(), jsonMessage)
                            when {
                                notification.method == LSP.CancelNotificationType.method -> {
                                    val params = LSP.json.decodeFromJsonElement(CancelParams.serializer(), notification.params!!)
                                    incomingRequestsJobs.remove(params.id)?.cancel()
                                }

                                else ->
                                    runCatching {
                                        when (val originalHandler = handlers.notificationHandler(notification.method)) {
                                            null ->
                                                LOG.debug("no handler for notification: ${notification.method}")

                                            else -> {
                                                val handler = middleware.notificationHandler(originalHandler)
                                                val deserializedParams = notification.params?.let { params ->
                                                    LSP.json.decodeFromJsonElement(handler.notificationType.paramsSerializer, params)
                                                }
                                                (handler as LspNotificationHandler<Any?>).handler(lspHandlerContext, this, deserializedParams)
                                            }
                                        }
                                    }.onFailure { error ->
                                        if (error is CancellationException) throw error
                                        LOG.error(error)
                                    }
                            }
                        }

                        else -> {
                            throw ProtocolViolation("not json rpc message: $jsonMessage")
                        }
                    }
                }
            }
        }.use {
            body(lspHandlerContext.lspClient)
        }
    }
}

private val LOG = logger<LspClient>()