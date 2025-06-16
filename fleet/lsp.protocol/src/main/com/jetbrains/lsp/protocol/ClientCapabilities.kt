package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClientCapabilities(
    /**
     * Workspace specific client capabilities.
     */
    val workspace: ClientWorkspaceCapabilities? = null,

    /**
     * Text document specific client capabilities.
     */
    val textDocument: TextDocumentClientCapabilities? = null,

    /**
     * Capabilities specific to the notebook document support.
     *
     * @since 3.17.0
     */
    val notebookDocument: NotebookDocumentClientCapabilities? = null,

    /**
     * Window specific client capabilities.
     */
    val window: ClientWindowCapabilities? = null,

    /**
     * General client capabilities.
     *
     * @since 3.16.0
     */
    val general: ClientGeneralCapabilities? = null,

    /**
     * Experimental client capabilities.
     */
    val experimental: JsonElement? = null,
)