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
        handler: suspend CoroutineScope.(Notification) -> Unit,
    )
}


class LspHandlerContext(
    val lspClient: LspClient,
)

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


class LspRequestHandler<Params, Result, Error>(
    val requestType: RequestType<Params, Result, Error>,
    val handler: suspend context(LspHandlerContext) CoroutineScope.(Params) -> Result,
)

class LspNotificationHandler<Params>(
    val notificationType: NotificationType<Params>,
    val handler: suspend CoroutineScope.(Params) -> Unit,
)

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
            handler: suspend CoroutineScope.(Notification) -> Unit,
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