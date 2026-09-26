package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClientWindowCapabilities(
    /**
     * It indicates whether the client supports server-initiated progress using
     * the `window/workDoneProgress/create` request.
     *
     * The capability also controls whether the client supports handling
     * of progress notifications. If set servers are allowed to report a
     * `workDoneProgress` property in the request-specific server capabilities.
     *
     * @since 3.15.0
     */
    val workDoneProgress: Boolean? = null,

    /**
     * Capabilities specific to the showMessage request.
     *
     * @since 3.16.0
     */
    val showMessage: ShowMessageRequestClientCapabilities? = null,

    /**
     * Client capabilities for the show document request.
     *
     * @since 3.16.0
     */
    val showDocument: ShowDocumentClientCapabilities? = null
)

@Serializable
data class ShowMessageRequestClientCapabilities(
    /**
     * Capabilities specific to the `MessageActionItem` type.
     */
    val messageActionItem: MessageActionItem? = null,
) {
    @Serializable
    data class MessageActionItem(
        /**
         * Whether the client supports additional attributes which
         * are preserved and sent back to the server in the
         * request's response.
         */
        val additionalPropertiesSupport: Boolean? = null,
    )
}

@Serializable
data class ShowDocumentClientCapabilities(
    /**
     * The client has support for the show document
     * request.
     */
    val support: Boolean,
)

typealias Unknown = JsonElement