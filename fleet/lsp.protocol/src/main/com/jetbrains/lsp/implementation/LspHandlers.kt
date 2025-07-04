package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.LSP.ProgressNotificationType
import com.jetbrains.lsp.protocol.RequestType
import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.ProgressParams
import com.jetbrains.lsp.protocol.WorkDoneProgress
import com.jetbrains.lsp.protocol.WorkDoneProgressParams
import kotlinx.coroutines.CoroutineScope

interface LspHandlers {
    fun requestHandler(
        requestTypeName: String,
    ): LspRequestHandler<*, *, *, *>?

    fun notificationHandler(
        notificationTypeName: String,
    ): LspNotificationHandler<*, *>?

    object EMPTY : LspHandlers {
        override fun requestHandler(requestTypeName: String): LspRequestHandler<*, *, *, *>? = null
        override fun notificationHandler(notificationTypeName: String): LspNotificationHandler<*, *>? = null
    }
}

interface LspHandlersBuilder<T> {
    fun <Request, Response, Error> request(
        requestType: RequestType<Request, Response, Error>,
        handler: suspend context(T) CoroutineScope.(Request) -> Response,
    )

    fun <Notification> notification(
        notifcationType: NotificationType<Notification>,
        handler: suspend context(T) CoroutineScope.(Notification) -> Unit,
    )
}


class LspHandlerContext(
    val lspClient: LspClient,
)

context(context: LspHandlerContext)
val lspClient: LspClient get() = context.lspClient

fun <P : WorkDoneProgress> LspClient.reportProgress(
    params: WorkDoneProgressParams,
    progress: P,
) {
    val token = params.workDoneToken ?: return
    notify(ProgressNotificationType, ProgressParams(token, LSP.json.encodeToJsonElement(WorkDoneProgress.serializer(), progress)))
}

fun LspClient.reportProgressMessage(
    params: WorkDoneProgressParams,
    message: String,
) {
    val report = WorkDoneProgress.Report(message = message)
    reportProgress(params, report)
}


class LspRequestHandler<Params, Result, Error, Context>(
    val requestType: RequestType<Params, Result, Error>,
    val handler: suspend context(Context) CoroutineScope.(Params) -> Result,
)

class LspNotificationHandler<Params, Context>(
    val notificationType: NotificationType<Params>,
    val handler: suspend context(Context) CoroutineScope.(Params) -> Unit,
)

fun <T> lspHandlers(builder: LspHandlersBuilder<T>.() -> Unit): LspHandlers {
    val requests = mutableMapOf<String, LspRequestHandler<*, *, *, T>>()
    val notifications = mutableMapOf<String, LspNotificationHandler<*, T>>()

    object : LspHandlersBuilder<T> {
        override fun <Request, Response, Error> request(
            requestType: RequestType<Request, Response, Error>,
            handler: suspend context(T) CoroutineScope.(Request) -> Response,
        ) {
            requests[requestType.method] = LspRequestHandler(requestType, handler)
        }

        override fun <Notification> notification(
            notifcationType: NotificationType<Notification>,
            handler: suspend context(T) CoroutineScope.(Notification) -> Unit,
        ) {
            notifications[notifcationType.method] = LspNotificationHandler(notifcationType, handler)
        }
    }.builder()

    return object : LspHandlers {
        @Suppress("UNCHECKED_CAST")
        override fun requestHandler(
            requestType: String,
        ): LspRequestHandler<*, *, *, T>? =
            requests[requestType]

        @Suppress("UNCHECKED_CAST")
        override fun notificationHandler(
            notificationType: String,
        ): LspNotificationHandler<*, T>? =
            notifications[notificationType]
    }
}