package com.jetbrains.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.jvm.JvmInline

@Serializable(with = OrBoolean.Serializer::class)
sealed interface OrBoolean<out T> {
    @Serializable
    @JvmInline
    value class Boolean(val value: kotlin.Boolean) : OrBoolean<Nothing>

    @Serializable
    @JvmInline
    value class Value<T>(val value: T) : OrBoolean<T>

    companion object {
        operator fun <T> invoke(value: kotlin.Boolean): OrBoolean<T> = Boolean(value)
        fun <T> of(value: T): OrBoolean<T> = Value(value)
    }

    class Serializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<OrBoolean<T>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OrBoolean")

        override fun serialize(encoder: Encoder, value: OrBoolean<T>) {
            when (value) {
                is Boolean -> encoder.encodeSerializableValue(Boolean.serializer(), value)
                is Value -> encoder.encodeSerializableValue(Value.serializer(dataSerializer), value)
            }
        }

        override fun deserialize(decoder: Decoder): OrBoolean<T> {
            val jsonDecoder = decoder as? JsonDecoder
                              ?: throw SerializationException("Json serializer is required")
            val element = jsonDecoder.decodeJsonElement()
            return when (element) {
                is JsonPrimitive if element.booleanOrNull != null -> jsonDecoder.json.decodeFromJsonElement(Boolean.serializer(), element)
                else -> jsonDecoder.json.decodeFromJsonElement(Value.serializer(dataSerializer), element)
            }
        }
    }
}

fun <T, R> OrBoolean<T>.map(mapBoolean: (Boolean) -> R, mapOtherValue: (T) -> R): R {
    return when (this) {
        is OrBoolean.Boolean -> mapBoolean(value)
        is OrBoolean.Value -> mapOtherValue(value)
    }
}

@Serializable(with = OrString.Serializer::class)
sealed interface OrString<out T> {
    @Serializable
    @JvmInline
    value class String(val value: kotlin.String) : OrString<Nothing>

    @Serializable
    @JvmInline
    value class Value<T>(val value: T) : OrString<T>

    companion object {
        operator fun <T> invoke(value: kotlin.String): OrString<T> = String(value)
        fun <T> of(value: T): OrString<T> = Value(value)
    }

    class Serializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<OrString<T>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OrString")

        override fun serialize(encoder: Encoder, value: OrString<T>) {
            when (value) {
                is String -> encoder.encodeSerializableValue(String.serializer(), value)
                is Value -> encoder.encodeSerializableValue(Value.serializer(dataSerializer), value)
            }
        }

        override fun deserialize(decoder: Decoder): OrString<T> {
            val jsonDecoder = decoder as? JsonDecoder
                              ?: throw SerializationException("Json serializer is required")
            val element = jsonDecoder.decodeJsonElement()
            return when (element) {
                is JsonPrimitive if element.isString -> jsonDecoder.json.decodeFromJsonElement(String.serializer(), element)
                else -> jsonDecoder.json.decodeFromJsonElement(Value.serializer(dataSerializer), element)
            }
        }
    }
}

fun <T, R> OrString<T>.map(mapString: (String) -> R, mapOtherValue: (T) -> R): R {
    return when (this) {
        is OrString.String -> mapString(value)
        is OrString.Value -> mapOtherValue(value)
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
    val completionProvider: CompletionRegistrationOptions? = null,

    /**
     * The server provides hover support.
     */
    val hoverProvider: OrBoolean<HoverRegistrationOptions>? = null,

    /**
     * The server provides signature help support.
     */
    val signatureHelpProvider: SignatureHelpRegistrationOptions? = null,

    /**
     * The server provides go to declaration support.
     *
     * @since 3.14.0
     */
    val declarationProvider: OrBoolean<DeclarationRegistrationOptions>? = null,

    /**
     * The server provides goto definition support.
     */
    val definitionProvider: OrBoolean<DefinitionRegistrationOptions>? = null,

    /**
     * The server provides goto type definition support.
     *
     * @since 3.6.0
     */
    val typeDefinitionProvider: OrBoolean<TypeDefinitionRegistrationOptions>? = null,

    /**
     * The server provides goto implementation support.
     *
     * @since 3.6.0
     */
    val implementationProvider: OrBoolean<ImplementationRegistrationOptions>? = null,

    /**
     * The server provides find references support.
     */
    val referencesProvider: OrBoolean<ReferenceRegistrationOptions>? = null,

    /**
     * The server provides document highlight support.
     */
    val documentHighlightProvider: OrBoolean<DocumentHighlightOptions>? = null,

    /**
     * The server provides document symbol support.
     */
    val documentSymbolProvider: OrBoolean<DocumentSymbolRegistrationOptions>? = null,

    /**
     * The server provides code actions. The `CodeActionOptions` return type is
     * only valid if the client signals code action literal support via the
     * property `textDocument.codeAction.codeActionLiteralSupport`.
     */
    val codeActionProvider: OrBoolean<CodeActionRegistrationOptions>? = null,

    /**
     * The server provides code lens.
     */
    val codeLensProvider: CodeLensRegistrationOptions? = null,

    /**
     * The server provides document link support.
     */
    val documentLinkProvider: DocumentLinkOptions? = null,

    /**
     * The server provides color provider support.
     *
     * @since 3.6.0
     */
    val colorProvider: OrBoolean<DocumentColorRegistrationOptions>? = null,

    /**
     * The server provides document formatting.
     */
    val documentFormattingProvider: OrBoolean<DocumentFormattingRegistrationOptions>? = null,

    /**
     * The server provides document range formatting.
     */
    val documentRangeFormattingProvider: OrBoolean<DocumentRangeFormattingRegistrationOptions>? = null,

    /**
     * The server provides document formatting on typing.
     */
    val documentOnTypeFormattingProvider: DocumentOnTypeFormattingOptions? = null,

    /**
     * The server provides rename support. RenameOptions may only be
     * specified if the client states that it supports
     * `prepareSupport` in its initial `initialize` request.
     */
    val renameProvider: OrBoolean<RenameRegistrationOptions>? = null,

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
    val semanticTokensProvider: SemanticTokensRegistrationOptions? = null,

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
    val inlayHintProvider: OrBoolean<InlayHintRegistrationOptions>? = null,

    /**
     * The server has support for pull model diagnostics.
     *
     * @since 3.17.0
     */
    val diagnosticProvider: DiagnosticOptions? = null,

    /**
     * The server provides workspace symbol support.
     */
    val workspaceSymbolProvider: OrBoolean<WorkspaceSymbolRegistrationOptions>? = null,

    /**
     * Workspace specific server capabilities
     */
    val workspace: ServerWorkspaceCapabilities? = null,

    /**
     * Experimental server capabilities.
     */
    val experimental: JsonElement? = null,
)

typealias DocumentLinkOptions = Unknown
typealias DocumentOnTypeFormattingOptions = Unknown
typealias FoldingRangeOptions = Unknown
typealias SelectionRangeOptions = Unknown
typealias LinkedEditingRangeOptions = Unknown
typealias CallHierarchyOptions = Unknown
typealias MonikerOptions = Unknown
typealias TypeHierarchyOptions = Unknown
typealias InlineValueOptions = Unknown

interface DocumentRangeFormattingOptions : WorkDoneProgressOptions

@Serializable
data class DocumentRangeFormattingRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
) : DocumentRangeFormattingOptions, TextDocumentRegistrationOptions

interface DocumentFormattingOptions : WorkDoneProgressOptions

@Serializable
data class DocumentFormattingRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
) : DocumentFormattingOptions, TextDocumentRegistrationOptions

interface DocumentSymbolOptions : WorkDoneProgressOptions {
    /**
     * A human-readable string that is shown when multiple outlines trees
     * are shown for the same document.
     *
     * @since 3.16.0
     */
    val label: String?
}

@Serializable
data class DocumentSymbolRegistrationOptions(
    override val label: String?,
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
) : DocumentSymbolOptions, TextDocumentRegistrationOptions

interface ImplementationOptions : WorkDoneProgressOptions

@Serializable
data class ImplementationRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
    override val id: String? = null,
) : ImplementationOptions, TextDocumentRegistrationOptions, StaticRegistrationOptions

interface HoverOptions: WorkDoneProgressOptions

@Serializable
data class HoverRegistrationOptions(
    override val documentSelector: DocumentSelector? = null,
    override val workDoneProgress: Boolean? = null,
) : HoverOptions, TextDocumentRegistrationOptions

interface TypeDefinitionOptions : WorkDoneProgressOptions

@Serializable
data class TypeDefinitionRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
    override val id: String? = null,
) : TypeDefinitionOptions, TextDocumentRegistrationOptions, StaticRegistrationOptions

interface DocumentColorOptions : WorkDoneProgressOptions

@Serializable
data class DocumentColorRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
    override val id: String? = null,
) : DocumentColorOptions, TextDocumentRegistrationOptions, StaticRegistrationOptions

@Serializable
data class DocumentHighlightOptions(
    override val workDoneProgress: Boolean? = null,
) : WorkDoneProgressOptions

interface RenameOptions : WorkDoneProgressOptions {
    /**
     * Renames should be checked and tested before being executed.
     */
    val prepareProvider: Boolean?
}

@Serializable
data class RenameRegistrationOptions(
    /**
     * Renames should be checked and tested before being executed.
     */
    override val prepareProvider: Boolean?,

    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
) : RenameOptions, TextDocumentRegistrationOptions

interface CodeLensOptions : WorkDoneProgressOptions {
    /**
     * Code lens has a resolve provider as well.
     */
    val resolveProvider: Boolean?
}

@Serializable
data class CodeLensRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,

    /**
     * Code lens has a resolve provider as well.
     */
    override val resolveProvider: Boolean? = null,
) : CodeLensOptions, TextDocumentRegistrationOptions

interface InlayHintOptions : WorkDoneProgressOptions {
    /**
    * The server provides support to resolve additional
    * information for an inlay hint item.
    */
    val resolveProvider: Boolean?
}

@Serializable
data class InlayHintRegistrationOptions(
    /**
     * The server provides support to resolve additional
     * information for an inlay hint item.
     */
    override val resolveProvider: Boolean?,

    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
    override val id: String? = null,
) : InlayHintOptions, TextDocumentRegistrationOptions, StaticRegistrationOptions

interface SemanticTokensOptions : WorkDoneProgressOptions {
    /**
     * The legend used by the server
     */
    val legend: SemanticTokensLegend

    /**
     * Server supports providing semantic tokens for a specific range
     * of a document.
     */
    val range: OrBoolean<Unit>?

    /**
     * Server supports providing semantic tokens for a full document.
     */
    val full: OrBoolean<Full>?

    @Serializable
    data class Full(
      /**
       * The server supports deltas for full documents.
       */
      val delta: Boolean?,
    )
}

@Serializable
data class SemanticTokensRegistrationOptions(
    /**
     * The legend used by the server
     */
    override val legend: SemanticTokensLegend,

    /**
     * Server supports providing semantic tokens for a specific range
     * of a document.
     */
    override val range: OrBoolean<Unit>?,

    /**
     * Server supports providing semantic tokens for a full document.
     */
    override val full: OrBoolean<SemanticTokensOptions.Full>?,

    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
    override val id: String? = null,
) : SemanticTokensOptions, TextDocumentRegistrationOptions, StaticRegistrationOptions

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
    val changeNotifications: JsonElement? = null, // String or Boolean
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
    val options: FileOperationPatternOptions? = null,
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
    val ignoreCase: Boolean? = null,
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
    val pattern: FileOperationPattern,
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
    val filters: List<FileOperationFilter>,
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