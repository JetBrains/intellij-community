package com.jetbrains.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
@JvmInline
value class OrBoolean<T>(val value: JsonElement) {
    constructor(boolean: Boolean) : this(JsonPrimitive(boolean))

    companion object {
        inline fun <reified T> of(value: T): OrBoolean<T> {
            val serializer = kotlinx.serialization.serializer<T>()
            val encoded = LSP.json.encodeToJsonElement(serializer, value)
            return OrBoolean(encoded)
        }
    }
}

@Serializable
data class ServerCapabilities(
    /**
     * The position encoding the server picked from the encodings offered
     * by the client via the client capability `general.positionEncodings`.
     *
     * If the client didn't provide any position encodings the only valid
     * value that a server can return is 'utf-16'.
     *
     * If omitted it defaults to 'utf-16'.
     *
     * @since 3.17.0
     */
    val positionEncoding: PositionEncodingKind? = null,

    /**
     * Defines how text documents are synced. Is either a detailed structure
     * defining each notification or for backwards compatibility the
     * TextDocumentSyncKind number. If omitted it defaults to
     * `TextDocumentSyncKind.None`.
     */
    val textDocumentSync: TextDocumentSync? = null, // TextDocumentSyncOptions | TextDocumentSyncKind

    /**
     * Defines how notebook documents are synced.
     *
     * @since 3.17.0
     */
    val notebookDocumentSync: NotebookDocumentSyncOptions? = null, // NotebookDocumentSyncOptions | NotebookDocumentSyncRegistrationOptions

    /**
     * The server provides completion support.
     */
    val completionProvider: CompletionOptions? = null,

    /**
     * The server provides hover support.
     */
    val hoverProvider: OrBoolean<HoverOptions>? = null,

    /**
     * The server provides signature help support.
     */
    val signatureHelpProvider: SignatureHelpOptions? = null,

    /**
     * The server provides go to declaration support.
     *
     * @since 3.14.0
     */
    val declarationProvider: OrBoolean<DeclarationOptions>? = null,

    /**
     * The server provides goto definition support.
     */
    val definitionProvider: OrBoolean<DefinitionOptions>? = null,

    /**
     * The server provides goto type definition support.
     *
     * @since 3.6.0
     */
    val typeDefinitionProvider: OrBoolean<TypeDefinitionOptions>? = null,

    /**
     * The server provides goto implementation support.
     *
     * @since 3.6.0
     */
    val implementationProvider: OrBoolean<ImplementationOptions>? = null,

    /**
     * The server provides find references support.
     */
    val referencesProvider: OrBoolean<ReferenceOptions>? = null,

    /**
     * The server provides document highlight support.
     */
    val documentHighlightProvider: OrBoolean<DocumentHighlightOptions>? = null,

    /**
     * The server provides document symbol support.
     */
    val documentSymbolProvider: OrBoolean<DocumentSymbolOptions>? = null,

    /**
     * The server provides code actions. The `CodeActionOptions` return type is
     * only valid if the client signals code action literal support via the
     * property `textDocument.codeAction.codeActionLiteralSupport`.
     */
    val codeActionProvider: OrBoolean<CodeActionOptions>? = null,

    /**
     * The server provides code lens.
     */
    val codeLensProvider: CodeLensOptions? = null,

    /**
     * The server provides document link support.
     */
    val documentLinkProvider: DocumentLinkOptions? = null,

    /**
     * The server provides color provider support.
     *
     * @since 3.6.0
     */
    val colorProvider: OrBoolean<DocumentColorOptions>? = null,

    /**
     * The server provides document formatting.
     */
    val documentFormattingProvider: OrBoolean<DocumentFormattingOptions>? = null,

    /**
     * The server provides document range formatting.
     */
    val documentRangeFormattingProvider: OrBoolean<DocumentRangeFormattingOptions>? = null,

    /**
     * The server provides document formatting on typing.
     */
    val documentOnTypeFormattingProvider: DocumentOnTypeFormattingOptions? = null,

    /**
     * The server provides rename support. RenameOptions may only be
     * specified if the client states that it supports
     * `prepareSupport` in its initial `initialize` request.
     */
    val renameProvider: OrBoolean<RenameOptions>? = null,

    /**
     * The server provides folding provider support.
     *
     * @since 3.10.0
     */
    val foldingRangeProvider: OrBoolean<FoldingRangeOptions>? = null,

    /**
     * The server provides execute command support.
     */
    val executeCommandProvider: ExecuteCommandOptions? = null,

    /**
     * The server provides selection range support.
     *
     * @since 3.15.0
     */
    val selectionRangeProvider: OrBoolean<SelectionRangeOptions>? = null,

    /**
     * The server provides linked editing range support.
     *
     * @since 3.16.0
     */
    val linkedEditingRangeProvider: OrBoolean<LinkedEditingRangeOptions>? = null,

    /**
     * The server provides call hierarchy support.
     *
     * @since 3.16.0
     */
    val callHierarchyProvider: OrBoolean<CallHierarchyOptions>? = null,

    /**
     * The server provides semantic tokens support.
     *
     * @since 3.16.0
     */
    val semanticTokensProvider: SemanticTokensOptions? = null, // SemanticTokensOptions | SemanticTokensRegistrationOptions

    /**
     * Whether server provides moniker support.
     *
     * @since 3.16.0
     */
    val monikerProvider: OrBoolean<MonikerOptions>? = null,

    /**
     * The server provides type hierarchy support.
     *
     * @since 3.17.0
     */
    val typeHierarchyProvider: OrBoolean<TypeHierarchyOptions>? = null,

    /**
     * The server provides inline values.
     *
     * @since 3.17.0
     */
    val inlineValueProvider: OrBoolean<InlineValueOptions>? = null,

    /**
     * The server provides inlay hints.
     *
     * @since 3.17.0
     */
    val inlayHintProvider: OrBoolean<InlayHintOptions>? = null,

    /**
     * The server has support for pull model diagnostics.
     *
     * @since 3.17.0
     */
    val diagnosticProvider: DiagnosticOptions? = null,

    /**
     * The server provides workspace symbol support.
     */
    val workspaceSymbolProvider: OrBoolean<WorkspaceSymbolOptions>? = null,

    /**
     * Workspace specific server capabilities
     */
    val workspace: ServerWorkspaceCapabilities? = null,

    /**
     * Experimental server capabilities.
     */
    val experimental: JsonElement? = null,
)


typealias HoverOptions = Unknown
typealias TypeDefinitionOptions = Unknown
typealias ImplementationOptions = Unknown
typealias DocumentHighlightOptions = Unknown
typealias DocumentSymbolOptions = Unknown
typealias CodeLensOptions = Unknown
typealias DocumentLinkOptions = Unknown
typealias DocumentColorOptions = Unknown
typealias DocumentFormattingOptions = Unknown
typealias DocumentRangeFormattingOptions = Unknown
typealias DocumentOnTypeFormattingOptions = Unknown
typealias RenameOptions = Unknown
typealias FoldingRangeOptions = Unknown
typealias SelectionRangeOptions = Unknown
typealias LinkedEditingRangeOptions = Unknown
typealias CallHierarchyOptions = Unknown
typealias SemanticTokensRegistrationOptions = Unknown
typealias MonikerOptions = Unknown
typealias TypeHierarchyOptions = Unknown
typealias InlineValueOptions = Unknown
typealias InlayHintOptions = Unknown

@Serializable
data class SemanticTokensOptions(
    /**
     * The legend used by the server
     */
    val legend: SemanticTokensLegend,

    /**
     * Server supports providing semantic tokens for a specific range
     * of a document.
     */
    val range: Boolean?,

    /**
     * Server supports providing semantic tokens for a full document.
     */
    val full: Boolean?,
)

@Serializable
data class ServerWorkspaceCapabilities(
    /**
     * The server supports workspace folder.
     *
     * @since 3.6.0
     */
    val workspaceFolders: WorkspaceFoldersServerCapabilities? = null,

    /**
     * The server is interested in file notifications/requests.
     *
     * @since 3.16.0
     */
    val fileOperations: FileOperations? = null,
)

@Serializable
data class WorkspaceFoldersServerCapabilities(
    /**
     * The server has support for workspace folders
     */
    val supported: Boolean? = null,

    /**
     * Whether the server wants to receive workspace folder
     * change notifications.
     *
     * If a string is provided, the string is treated as an ID
     * under which the notification is registered on the client
     * side. The ID can be used to unregister for these events
     * using the `client/unregisterCapability` request.
     */
    val changeNotifications: JsonElement? = null // String or Boolean
)

/**
 * A pattern to describe in which file operation requests or notifications
 * the server is interested in.
 *
 * @since 3.16.0
 */
@Serializable
data class FileOperationPattern(
    /**
     * The glob pattern to match. Glob patterns can have the following syntax:
     * - `*` to match one or more characters in a path segment
     * - `?` to match on one character in a path segment
     * - `**` to match any number of path segments, including none
     * - `{}` to group sub patterns into an OR expression.
     *   matches all TypeScript and JavaScript files)
     * - `[]` to declare a range of characters to match in a path segment
     *   (e.g., `example.[0-9]` to match on `example.0`, `example.1`, …)
     * - `[!...]` to negate a range of characters to match in a path segment
     *   (e.g., `example.[!0-9]` to match on `example.a`, `example.b`, but
     *   not `example.0`)
     */
    val glob: String,

    /**
     * Whether to match files or folders with this pattern.
     *
     * Matches both if undefined.
     */
    val matches: FileOperationPatternKind? = null,

    /**
     * Additional options used during matching.
     */
    val options: FileOperationPatternOptions? = null
)

/**
 * Matching options for the file operation pattern.
 *
 * @since 3.16.0
 */
@Serializable
data class FileOperationPatternOptions(
    /**
     * The pattern should be matched ignoring casing.
     */
    val ignoreCase: Boolean? = null
)

@Serializable
enum class FileOperationPatternKind {
    /**
     * The pattern matches a file only.
     *
     * @since 3.16.0
     */
    @SerialName("file")
    FILE,

    /**
     * The pattern matches a folder only.
     *
     * @since 3.16.0
     */
    @SerialName("folder")
    FOLDER
}

/**
 * A filter to describe in which file operation requests or notifications
 * the server is interested in.
 *
 * @since 3.16.0
 */
@Serializable
data class FileOperationFilter(
    /**
     * A Uri like `file` or `untitled`.
     */
    val scheme: String? = null,

    /**
     * The actual file operation pattern.
     */
    val pattern: FileOperationPattern
)

/**
 * The options to register for file operations.
 *
 * @since 3.16.0
 */
@Serializable
data class FileOperationRegistrationOptions(
    /**
     * The actual filters.
     */
    val filters: List<FileOperationFilter>
)

@Serializable
data class FileOperations(
    /**
     * The server is interested in receiving didCreateFiles
     * notifications.
     */
    val didCreate: FileOperationRegistrationOptions? = null,

    /**
     * The server is interested in receiving willCreateFiles requests.
     */
    val willCreate: FileOperationRegistrationOptions? = null,

    /**
     * The server is interested in receiving didRenameFiles
     * notifications.
     */
    val didRename: FileOperationRegistrationOptions? = null,

    /**
     * The server is interested in receiving willRenameFiles requests.
     */
    val willRename: FileOperationRegistrationOptions? = null,

    /**
     * The server is interested in receiving didDeleteFiles file
     * notifications.
     */
    val didDelete: FileOperationRegistrationOptions? = null,

    /**
     * The server is interested in receiving willDeleteFiles file
     * requests.
     */
    val willDelete: FileOperationRegistrationOptions? = null,
)