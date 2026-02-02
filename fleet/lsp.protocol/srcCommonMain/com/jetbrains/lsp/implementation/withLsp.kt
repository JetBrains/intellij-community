package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.CancelParams
import com.jetbrains.lsp.protocol.ErrorCodes
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.NotificationMessage
import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.RequestMessage
import com.jetbrains.lsp.protocol.RequestType
import com.jetbrains.lsp.protocol.ResponseError
import com.jetbrains.lsp.protocol.ResponseMessage
import com.jetbrains.lsp.protocol.StringOrInt
import fleet.multiplatform.shims.MultiplatformConcurrentHashMap
import fleet.util.logging.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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
    createCoroutineContext: (LspClient) -> CoroutineContext = { EmptyCoroutineContext },
    body: suspend CoroutineScope.(LspClient) -> Unit,
) {
    coroutineScope {
        val outgoingRequests = MultiplatformConcurrentHashMap<StringOrInt, OutgoingRequest>()
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
                    notifyAsync(LSP.CancelNotificationType, CancelParams(id))
                    outgoingRequests.remove(id)
                    throw c
                }
            }

            override fun <Params> notifyAsync(
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

          override suspend fun <Params> notify(notificationType: NotificationType<Params>, params: Params) {
            val notification = NotificationMessage(
              jsonrpc = "2.0",
              method = notificationType.method,
              params = LSP.json.encodeToJsonElement(notificationType.paramsSerializer, params)
            )
            outgoing.send(
              LSP.json.encodeToJsonElement(
                NotificationMessage.serializer(),
                notification
              )
            )
          }
        }

        val lspHandlerContext = LspHandlerContext(lspClient, this)

        launch(CoroutineName("incoming requests accepter") + createCoroutineContext(lspClient)) {
            withSupervisor { supervisor ->
                val incomingRequestsJobs = MultiplatformConcurrentHashMap<StringOrInt, Job>()
                incoming.consumeEach { jsonMessage ->
                    when {
                        jsonMessage !is JsonObject || jsonMessage["jsonrpc"] != JsonPrimitive("2.0") -> {
                            throw ProtocolViolation("not json rpc message: $jsonMessage")
                        }

                        isRequest(jsonMessage) -> {
                            val request = LSP.json.decodeFromJsonElement(RequestMessage.serializer(), jsonMessage)
                            supervisor.launch(context = CoroutineName("handler for ${request.method}"), start = CoroutineStart.ATOMIC) {
                                val maybeHandler = handlers.requestHandler(request.method)
                                runCatching {
                                    val handler = requireNotNull(maybeHandler) {
                                        "no handler for request: ${request.method}"
                                    }
                                    val deserializedParams = request.params?.let { params ->
                                        LSP.json.decodeFromJsonElement(handler.requestType.paramsSerializer, params)
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    handler as LspRequestHandler<Any?, Any?, Any?>

                                    val result = handler.handler(
                                          lspHandlerContext,
                                          this,
                                          deserializedParams
                                      )

                                    LSP.json.encodeToJsonElement(
                                        serializer = handler.requestType.resultSerializer,
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
                                                        @Suppress("UNCHECKED_CAST")
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
                                incomingRequestsJobs[request.id] = requestJob
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
                                                      @Suppress("UNCHECKED_CAST")
                                                      LSP.json.decodeFromJsonElement(
                                                          client.requestType.resultSerializer as KSerializer<Any?>,
                                                          result
                                                      )
                                                    }.onFailure { error ->
                                                        currentCoroutineContext().job.ensureActive()
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
                                                              @Suppress("UNCHECKED_CAST")
                                                              LSP.json.decodeFromJsonElement(
                                                                  client.requestType.errorSerializer as KSerializer<Any?>,
                                                                  data
                                                              )
                                                            }.onFailure { decodingError ->
                                                                currentCoroutineContext().job.ensureActive()
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
                                        when (val handler = handlers.notificationHandler(notification.method)) {
                                            null ->
                                                LOG.debug("no handler for notification: ${notification.method}")

                                            else -> {
                                                val deserializedParams = notification.params?.let { params ->
                                                    LSP.json.decodeFromJsonElement(handler.notificationType.paramsSerializer, params)
                                                }
                                                @Suppress("UNCHECKED_CAST")
                                                (handler as LspNotificationHandler<Any?>).handler(lspHandlerContext, this, deserializedParams)
                                            }
                                        }
                                    }.onFailure { error ->
                                        currentCoroutineContext().job.ensureActive()
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