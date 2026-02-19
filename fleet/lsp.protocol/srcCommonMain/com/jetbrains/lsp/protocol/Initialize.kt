package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

val Initialize: RequestType<InitializeParams, InitializeResult, InitializeError> = RequestType(
    "initialize",
    InitializeParams.serializer(),
    InitializeResult.serializer(),
    InitializeError.serializer()
)

val Initialized: NotificationType<Unit> = NotificationType("initialized", Unit.serializer())

@Serializable
data class InitializeParams(
    /**
     * The process Id of the parent process that started the server. Is null if
     * the process has not been started by another process. If the parent
     * process is not alive then the server should exit (see exit notification)
     * its process.
     */
    val processId: Int?,

    /**
     * Information about the client
     *
     * @since 3.15.0
     */
    val clientInfo: ClientInfo? = null,

    /**
     * The locale the client is currently showing the user interface
     * in. This must not necessarily be the locale of the operating
     * system.
     *
     * Uses IETF language tags as the value's syntax
     * (See https://en.wikipedia.org/wiki/IETF_language_tag)
     *
     * @since 3.16.0
     */
    val locale: String? = null,

    /**
     * The rootPath of the workspace. Is null
     * if no folder is open.
     *
     * @deprecated in favour of `rootUri`.
     */
    @Deprecated("Use rootUri instead")
    val rootPath: String? = null,

    /**
     * The rootUri of the workspace. Is null if no
     * folder is open. If both `rootPath` and `rootUri` are set
     * `rootUri` wins.
     *
     * @deprecated in favour of `workspaceFolders`
     */
    val rootUri: DocumentUri?,

    /**
     * User provided initialization options.
     */
    val initializationOptions: JsonElement? = null,

    /**
     * The capabilities provided by the client (editor or tool)
     */
    val capabilities: ClientCapabilities,

    /**
     * The initial trace setting. If omitted trace is disabled ('off').
     */
    val trace: TraceValue? = null,

    /**
     * The workspace folders configured in the client when the server starts.
     * This property is only available if the client supports workspace folders.
     * It can be `null` if the client supports workspace folders but none are
     * configured.
     *
     * @since 3.6.0
     */
    val workspaceFolders: List<WorkspaceFolder>? = null,

    override val workDoneToken: ProgressToken?,

    ) : WorkDoneProgressParams

@Serializable
data class InitializeResult(
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo? = null,
) {
    @Serializable
    data class ServerInfo(
        val name: String,
        val version: String? = null,
    )
}

object InitializeErrorCodes {

    /**
     * If the protocol version provided by the client can't be handled by
     * the server.
     *
     * @deprecated This initialize error got replaced by client capabilities.
     * There is no version handshake in version 3.0x
     */
    const val UNKNOWN_PROTOCOL_VERSION: Int = 1
}

@Serializable
data class InitializeError(
    /**
     * Indicates whether the client executes the following retry logic:
     * (1) Show the message provided by the ResponseError to the user
     * (2) User selects retry or cancel
     * (3) If user selected retry, the initialize method is sent again.
     */
    val retry: Boolean,
)

val ExitNotificationType: NotificationType<Unit> =
    NotificationType("exit", Unit.serializer())