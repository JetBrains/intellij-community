package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.LSP.ProgressNotificationType
import com.jetbrains.lsp.protocol.RequestType
import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.ProgressParams
import com.jetbrains.lsp.protocol.WorkDoneProgress
import com.jetbrains.lsp.protocol.WorkDoneProgressParams
import fleet.util.async.throttleLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlin.time.Duration.Companion.milliseconds

interface LspHandlers {
    fun requestHandler(
        requestTypeName: String,
    ): LspRequestHandler<*, *, *>?

    fun notificationHandler(
        notificationTypeName: String,
    ): LspNotificationHandler<*>?

    object EMPTY : LspHandlers {
        override fun requestHandler(requestTypeName: String): LspRequestHandler<*, *, *>? = null
        override fun notificationHandler(notificationTypeName: String): LspNotificationHandler<*>? = null
    }
}

interface LspHandlersBuilder {
    fun <Request, Response, Error> request(
        requestType: RequestType<Request, Response, Error>,
        handler: suspend context(LspHandlerContext) CoroutineScope.(Request) -> Response,
    )

    fun <Notification> notification(
        notifcationType: NotificationType<Notification>,
        handler: suspend context(LspHandlerContext) CoroutineScope.(Notification) -> Unit,
    )
}


class LspHandlerContext(
    val lspClient: LspClient,
)

context(context: LspHandlerContext)
val lspClient: LspClient get() = context.lspClient

fun interface ProgressReporter {
  companion object {
    val NOOP: ProgressReporter = ProgressReporter {}
  }
  fun report(progress: WorkDoneProgress)
}

suspend fun<T> LspClient.withProgress(
  request: WorkDoneProgressParams,
  body: suspend CoroutineScope.(ProgressReporter) -> T
): T =
  coroutineScope {
    when (val token = request.workDoneToken) {
      null -> body(ProgressReporter.NOOP)
      else -> {
        val state = MutableStateFlow<WorkDoneProgress?>(null)
        launch {
          state.filterNotNull().collect { p ->
            notify(ProgressNotificationType, ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), p)))
            delay(100.milliseconds)
          }
        }.use {
          body(ProgressReporter { p -> state.value = p })
        }
      }
    }
  }

class LspRequestHandler<Params, Result, Error>(
    val requestType: RequestType<Params, Result, Error>,
    val handler: suspend context(LspHandlerContext) CoroutineScope.(Params) -> Result,
)

class LspNotificationHandler<Params>(
    val notificationType: NotificationType<Params>,
    val handler: suspend context(LspHandlerContext) CoroutineScope.(Params) -> Unit,
)

interface LspHandlersMiddleware {
  fun <P, R, E> requestHandler(handler: LspRequestHandler<P, R, E>): LspRequestHandler<P, R, E>

  fun <P> notificationHandler(handler: LspNotificationHandler<P>): LspNotificationHandler<P>

  companion object {
    val IDENTITY: LspHandlersMiddleware = object : LspHandlersMiddleware {
      override fun <P, R, E> requestHandler(handler: LspRequestHandler<P, R, E>): LspRequestHandler<P, R, E> {
        return handler
      }

      override fun <P> notificationHandler(handler: LspNotificationHandler<P>): LspNotificationHandler<P> {
        return handler
      }
    }
  }
}

fun lspHandlers(builder: LspHandlersBuilder.() -> Unit): LspHandlers {
    val requests = mutableMapOf<String, LspRequestHandler<*, *, *>>()
    val notifications = mutableMapOf<String, LspNotificationHandler<*>>()

    object : LspHandlersBuilder {
        override fun <Request, Response, Error> request(
            requestType: RequestType<Request, Response, Error>,
            handler: suspend context(LspHandlerContext) CoroutineScope.(Request) -> Response,
        ) {
            requests[requestType.method] = LspRequestHandler(requestType, handler)
        }

        override fun <Notification> notification(
            notifcationType: NotificationType<Notification>,
            handler: suspend context(LspHandlerContext) CoroutineScope.(Notification) -> Unit,
        ) {
            notifications[notifcationType.method] = LspNotificationHandler(notifcationType, handler)
        }
    }.builder()

    return object : LspHandlers {
        @Suppress("UNCHECKED_CAST")
        override fun requestHandler(
            requestType: String,
        ): LspRequestHandler<*, *, *>? =
            requests[requestType]

        @Suppress("UNCHECKED_CAST")
        override fun notificationHandler(
            notificationType: String,
        ): LspNotificationHandler<*>? =
            notifications[notificationType]
    }
}