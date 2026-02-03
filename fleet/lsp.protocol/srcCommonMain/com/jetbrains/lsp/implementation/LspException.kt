package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.RequestType

class LspException internal constructor(
    message: String,
    val errorCode: Int,
    val payload: Any?,
    cause: Throwable?,
) : Exception(message, cause)

fun <E> throwLspError(
    requestType: RequestType<*, *, E>,
    message: String,
    data: E,
    code: Int,
    cause: Throwable? = null,
): Nothing =
    throw LspException(
        message = message,
        errorCode = code,
        payload = data,
        cause = cause
    )
