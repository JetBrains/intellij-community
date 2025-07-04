package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class Header(
    val contentLenght: Int,
    val contentType: String = "application/vscode-jsonrpc; charset=utf-8",
)

const val Separator: String = "\r\n"

sealed interface Message {
    val jsonrpc: String
}

@Serializable
@JvmInline
value class StringOrInt(val value: JsonPrimitive) {
    companion object {
        fun int(value: Int): StringOrInt = StringOrInt(JsonPrimitive(value))
        fun string(value: String): StringOrInt = StringOrInt(JsonPrimitive(value))
    }
}

@Serializable
data class RequestMessage(
    override val jsonrpc: String = "2.0",
    val id: StringOrInt,
    val method: String,
    val params: JsonElement? = null,
) : Message

@Serializable
data class ResponseMessage(
    override val jsonrpc: String,
    val id: StringOrInt,
    val result: JsonElement? = null,
    val error: ResponseError? = null,
) : Message

@Serializable
data class ResponseError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

object ErrorCodes {
    // Defined by JSON-RPC
    const val ParseError: Int = -32700
    const val InvalidRequest: Int = -32600
    const val MethodNotFound: Int = -32601
    const val InvalidParams: Int = -32602
    const val InternalError: Int = -32603

    /**
     * This is the start range of JSON-RPC reserved error codes.
     * It doesn't denote a real error code. No LSP error codes should
     * be defined between the start and end range. For backwards
     * compatibility the `ServerNotInitialized` and the `UnknownErrorCode`
     * are left in the range.
     *
     * @since 3.16.0
     */
    const val jsonrpcReservedErrorRangeStart: Int = -32099

    /** @deprecated use jsonrpcReservedErrorRangeStart */
    const val serverErrorStart: Int = jsonrpcReservedErrorRangeStart

    /**
     * Error code indicating that a server received a notification or
     * request before the server has received the `initialize` request.
     */
    const val ServerNotInitialized: Int = -32002
    const val UnknownErrorCode: Int = -32001

    /**
     * This is the end range of JSON-RPC reserved error codes.
     * It doesn't denote a real error code.
     *
     * @since 3.16.0
     */
    const val jsonrpcReservedErrorRangeEnd: Int = -32000

    /** @deprecated use jsonrpcReservedErrorRangeEnd */
    const val serverErrorEnd: Int = jsonrpcReservedErrorRangeEnd

    /**
     * This is the start range of LSP reserved error codes.
     * It doesn't denote a real error code.
     *
     * @since 3.16.0
     */
    const val lspReservedErrorRangeStart: Int = -32899

    /**
     * A request failed but it was syntactically correct, e.g the
     * method name was known and the parameters were valid. The error
     * message should contain human readable information about why
     * the request failed.
     *
     * @since 3.17.0
     */
    const val RequestFailed: Int = -32803

    /**
     * The server cancelled the request. This error code should
     * only be used for requests that explicitly support being
     * server cancellable.
     *
     * @since 3.17.0
     */
    const val ServerCancelled: Int = -32802

    /**
     * The server detected that the content of a document got
     * modified outside normal conditions. A server should
     * NOT send this error code if it detects a content change
     * in it unprocessed messages. The result even computed
     * on an older state might still be useful for the client.
     *
     * If a client decides that a result is not of any use anymore
     * the client should cancel the request.
     */
    const val ContentModified: Int = -32801

    /**
     * The client has canceled a request and a server has detected
     * the cancel.
     */
    const val RequestCancelled: Int = -32800

    /**
     * This is the end range of LSP reserved error codes.
     * It doesn't denote a real error code.
     *
     * @since 3.16.0
     */
    const val lspReservedErrorRangeEnd: Int = -32800
}

@Serializable
data class NotificationMessage(
    override val jsonrpc: String,
    val method: String,
    val params: JsonElement? = null,
) : Message

@Serializable
data class CancelParams(
    val id: StringOrInt,
)

@Serializable
@JvmInline
value class ProgressToken(val value: StringOrInt) {
    companion object {
        fun string(value: String): ProgressToken = ProgressToken(StringOrInt.string(value))
        fun int(value: Int): ProgressToken = ProgressToken(StringOrInt.int(value))
    }
}

@Serializable
data class ProgressParams(
    val token: ProgressToken,
    val value: JsonElement,
)

data class RequestType<Request, Response, Error>(
    val method: String,
    val paramsSerializer: KSerializer<Request>,
    val resultSerializer: KSerializer<Response>,
    val errorSerializer: KSerializer<Error>,
)

data class NotificationType<Params>(
    val method: String,
    val paramsSerializer: KSerializer<Params>
)
