package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ClientWorkspaceCapabilities(
    /**
     * The client supports applying batch edits
     * to the workspace by supporting the request
     * 'workspace/applyEdit'
     */
    val applyEdit: Boolean? = null,

    /**
     * Capabilities specific to `WorkspaceEdit`s
     */
    val workspaceEdit: WorkspaceEditClientCapabilities? = null,

    /**
     * Capabilities specific to the `workspace/didChangeConfiguration` notification.
     */
    val didChangeConfiguration: DidChangeConfigurationClientCapabilities? = null,

    /**
     * Capabilities specific to the `workspace/didChangeWatchedFiles` notification.
     */
    val didChangeWatchedFiles: DidChangeWatchedFilesClientCapabilities? = null,

    /**
     * Capabilities specific to the `workspace/symbol` request.
     */
    val symbol: WorkspaceSymbolClientCapabilities? = null,

    /**
     * Capabilities specific to the `workspace/executeCommand` request.
     */
    val executeCommand: ExecuteCommandClientCapabilities? = null,

    /**
     * The client has support for workspace folders.
     *
     * @since 3.6.0
     */
    val workspaceFolders: Boolean? = null,

    /**
     * The client supports `workspace/configuration` requests.
     *
     * @since 3.6.0
     */
    val configuration: Boolean? = null,

    /**
     * Capabilities specific to the semantic token requests scoped to the
     * workspace.
     *
     * @since 3.16.0
     */
    val semanticTokens: SemanticTokensWorkspaceClientCapabilities? = null,

    /**
     * Capabilities specific to the code lens requests scoped to the
     * workspace.
     *
     * @since 3.16.0
     */
    val codeLens: CodeLensWorkspaceClientCapabilities? = null,

    /**
     * The client has support for file requests/notifications.
     *
     * @since 3.16.0
     */
    val fileOperations: ClientFileOperationsCapabilities? = null,

    /**
     * Client workspace capabilities specific to inline values.
     *
     * @since 3.17.0
     */
    val inlineValue: InlineValueWorkspaceClientCapabilities? = null,

    /**
     * Client workspace capabilities specific to inlay hints.
     *
     * @since 3.17.0
     */
    val inlayHint: InlayHintWorkspaceClientCapabilities? = null,

    /**
     * Client workspace capabilities specific to diagnostics.
     *
     * @since 3.17.0.
     */
    val diagnostics: DiagnosticWorkspaceClientCapabilities? = null
)

typealias DidChangeConfigurationClientCapabilities = Unknown
typealias DidChangeWatchedFilesClientCapabilities = Unknown
typealias WorkspaceSymbolClientCapabilities = Unknown
typealias ExecuteCommandClientCapabilities = Unknown
typealias SemanticTokensWorkspaceClientCapabilities = Unknown
typealias CodeLensWorkspaceClientCapabilities = Unknown
typealias InlineValueWorkspaceClientCapabilities = Unknown
typealias InlayHintWorkspaceClientCapabilities = Unknown