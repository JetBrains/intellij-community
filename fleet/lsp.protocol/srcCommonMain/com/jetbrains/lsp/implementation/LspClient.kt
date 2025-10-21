package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.RequestType

interface LspClient {
    suspend fun <Params, Result, Error> request(
        requestType: RequestType<Params, Result, Error>,
        params: Params,
    ): Result

    fun <Params> notify(
        notificationType: NotificationType<Params>,
        params: Params,
    )
}