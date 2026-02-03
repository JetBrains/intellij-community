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
    val diagnostics: DiagnosticWorkspaceClientCapabilities? = null,
)

@Serializable
data class DidChangeConfigurationClientCapabilities(
  val dynamicRegistration: Boolean?,
)

@Serializable
data class DidChangeWatchedFilesClientCapabilities(
    val dynamicRegistration: Boolean?,
    val relativePatternSupport: Boolean?,
)

@Serializable
data class WorkspaceSymbolClientCapabilities(
    /**
     * Symbol request supports dynamic registration.
     */
    val dynamicRegistration: Boolean?,

    /**
     * Specific capabilities for the `SymbolKind` in the `workspace/symbol`
     * request.
     *
     * The symbol kind values the client supports. When this
     * property exists the client also guarantees that it will
     * handle values outside its set gracefully and falls back
     * to a default value when unknown.
     *
     * If this property is not present the client only supports
     * the symbol kinds from `File` to `Array` as defined in
     * the initial version of the protocol.
     */
    val symbolKind: ValueSet<SymbolKind>?,


    /**
     * The client supports tags on `SymbolInformation` and `WorkspaceSymbol`.
     * Clients supporting tags have to handle unknown tags gracefully.
     *
     * @since 3.16.0
     */
    val tagSupport: ValueSet<SymbolTag>?,

    /**
     * The client support partial workspace symbols. The client will send the
     * request `workspaceSymbol/resolve` to the server to resolve additional
     * properties.
     *
     * The properties that a client can resolve lazily. Usually
     * `location.range`
     *
     * @since 3.17.0 - proposedState
     */
    val resolveSupport: Properties<String>?,
)

@Serializable
data class ValueSet<T>(
    val valueSet: List<T>?,
)

@Serializable
data class Properties<T>(val properties: List<T>)

@Serializable
data class ExecuteCommandClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable
data class SemanticTokensWorkspaceClientCapabilities(
    /**
     * Whether the client implementation supports a refresh request sent from
     * the server to the client.
     *
     * Note that this event is global and will force the client to refresh all
     * semantic tokens currently shown. It should be used with absolute care
     * and is useful for situation where a server for example detect a project
     * wide change that requires such a calculation.
     */
    val refreshSupport: Boolean?,
)


@Serializable
data class CodeLensWorkspaceClientCapabilities(
    /**
     * Whether the client implementation supports a refresh request sent from the
     * server to the client.
     *
     * Note that this event is global and will force the client to refresh all
     * code lenses currently shown. It should be used with absolute care and is
     * useful for situation where a server for example detect a project wide
     * change that requires such a calculation.
     */
    val refreshSupport: Boolean?,
)

@Serializable
data class InlineValueWorkspaceClientCapabilities(
    /**
     * Whether the client implementation supports a refresh request sent from
     * the server to the client.
     *
     * Note that this event is global and will force the client to refresh all
     * inline values currently shown. It should be used with absolute care and
     * is useful for situation where a server for example detect a project wide
     * change that requires such a calculation.
     */
    val refreshSupport: Boolean?,
)

@Serializable
data class InlayHintWorkspaceClientCapabilities(
    /**
     * Whether the client implementation supports a refresh request sent from
     * the server to the client.
     *
     * Note that this event is global and will force the client to refresh all
     * inlay hints currently shown. It should be used with absolute care and
     * is useful for situation where a server for example detects a project wide
     * change that requires such a calculation.
     */
    val refreshSupport: Boolean?,
)