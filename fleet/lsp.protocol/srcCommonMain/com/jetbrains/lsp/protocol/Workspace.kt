package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmInline

@Serializable
data class DidChangeWorkspaceFoldersParams(
    /**
     * The actual workspace folder change event.
     */
    val event: WorkspaceFoldersChangeEvent,
)

/**
 * The workspace folder change event.
 */
@Serializable
data class WorkspaceFoldersChangeEvent(
    /**
     * The list of added workspace folders.
     */
    val added: List<WorkspaceFolder>,

    /**
     * The list of removed workspace folders.
     */
    val removed: List<WorkspaceFolder>,
)

@Serializable
data class DidChangeConfigurationParams(
    /**
     * The actual changed settings
     */
    val settings: JsonElement,
)

@Serializable
data class ConfigurationParams(
    val items: List<ConfigurationItem>,
)

@Serializable
data class ConfigurationItem(
    val scopeUri: URI? = null,
    val section: String? = null,
)

@Serializable
data class RenameFilesParams(
    val files: List<FileRename>,
)

@Serializable
data class FileRename(
    val oldUri: URI,
    val newUri: URI,
)

@Serializable
data class CreateFilesParams(
    /**
     * An array of all files/folders created in this operation.
     */
    val files: List<FileCreate>,
)

/**
 * Represents information on a file/folder create.
 */
@Serializable
data class FileCreate(
    /**
     * A file:// URI for the location of the file/folder being created.
     */
    val uri: URI,
)

@Serializable
data class DeleteFilesParams(
    /**
     * An array of all files/folders deleted in this operation.
     */
    val files: List<FileDelete>,
)

/**
 * Represents information on a file/folder delete.
 */
@Serializable
data class FileDelete(
    /**
     * A file:// URI for the location of the file/folder being deleted.
     */
    val uri: URI,
)

@Serializable
data class DidChangeWatchedFilesParams(
    val changes: List<FileEvent>,
)

@Serializable
data class DidChangeWatchedFilesRegistrationOptions(
    /**
     * The watchers to register.
     */
    val watchers: List<FileSystemWatcher>,
)

@Serializable
data class FileSystemWatcher(
    /**
     * The glob pattern to watch. See {@link GlobPattern glob pattern}
     * for more detail.
     *
     * @since 3.17.0 support for relative patterns.
     */
    val globPattern: GlobPattern,

    /**
     * The kind of events of interest. If omitted it defaults
     * to WatchKind.Create | WatchKind.Change | WatchKind.Delete
     * which is 7.
     */
    val kind: WatchKind? = null,
)

/**
 * The glob pattern. Either a string pattern or a relative pattern.
 *
 * @since 3.17.0
 * @see Pattern
 */
typealias GlobPattern = OrString<RelativePattern>

/**
 * The glob pattern to watch relative to the base path. Glob patterns can have
 * the following syntax:
 * - `*` to match zero or more characters in a path segment
 * - `?` to match on one character in a path segment
 * - `**` to match any number of path segments, including none
 * - `{}` to group conditions
 * - `[]` to declare a range of characters to match in a path segment
 *   (e.g., `example.[0-9]` to match on `example.0`, `example.1`, …)
 * - `[!...]` to negate a range of characters to match in a path segment
 *   (e.g., `example.[!0-9]` to match on `example.a`, `example.b`,
 *   but not `example.0`)
 *
 * @since 3.17.0
*/
typealias Pattern = String

/**
 * A relative pattern is a helper to construct glob patterns that are matched
 * relatively to a base URI. The common value for a `baseUri` is a workspace
 * folder root, but it can be another absolute URI as well.
 *
 * @since 3.17.0
 */
@Serializable
data class RelativePattern(
    /**
     * A workspace folder or a base URI to which this pattern will be matched
     * against relatively.
     */
    val baseUri: BaseURI,

    /**
     * The actual glob pattern;
     */
    val pattern: Pattern,
) {
    @Serializable(with = BaseURI.Serializer::class)
    sealed interface BaseURI {
        @Serializable
        @JvmInline
        value class WorkspaceFolder(val value: com.jetbrains.lsp.protocol.WorkspaceFolder) : BaseURI

        @Serializable
        @JvmInline
        value class URI(val value: com.jetbrains.lsp.protocol.URI) : BaseURI

        class Serializer: JsonContentPolymorphicSerializer<BaseURI>(BaseURI::class) {
            override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BaseURI> {
                return when (element) {
                    is JsonPrimitive if element.isString -> URI.serializer()
                    else -> WorkspaceFolder.serializer()
                }
            }
        }
    }
}

@Serializable
@JvmInline
value class WatchKind(val value: Int) {
    infix fun or(flag: WatchKind): WatchKind = WatchKind(value or flag.value)

    fun has(flag: WatchKind): Boolean = value and flag.value == flag.value

    companion object {
        val Create: WatchKind = WatchKind(0x01)
        val Change: WatchKind = WatchKind(0x02)
        val Delete: WatchKind = WatchKind(0x04)

        val All: WatchKind = Create or Change or Delete
    }
}

@Serializable
data class FileEvent(
    val uri: DocumentUri,
    val type: FileChangeType,
)

@Serializable(with = FileChangeTypeSerializer::class)
enum class FileChangeType(val value: Int) {
    Created(1),
    Changed(2),
    Deleted(3),
}

class FileChangeTypeSerializer : EnumAsIntSerializer<FileChangeType>(
    serialName = "FileChangeType",
    serialize = FileChangeType::value,
    deserialize = { FileChangeType.entries.getOrNull(it - 1) },
    fallback = FileChangeType.Changed,
)

/**
 * Represents information about programming constructs like variables, classes,
 * interfaces etc.
 *
 * @deprecated use DocumentSymbol or WorkspaceSymbol instead.
 */
@Serializable
data class SymbolInformation(
    /**
     * The name of this symbol.
     */
    val name: String,

    /**
     * The kind of this symbol.
     */
    val kind: SymbolKind,

    /**
     * Tags for this symbol.
     *
     * @since 3.16.0
     */
    @Serializable(with = SymbolTagListSerializer::class)
    val tags: List<SymbolTag>? = null,

    /**
     * Indicates if this symbol is deprecated.
     *
     * @deprecated Use tags instead
     */
    val deprecated: Boolean? = null,

    /**
     * The location of this symbol. The location's range is used by a tool
     * to reveal the location in the editor. If the symbol is selected in the
     * tool the range's start information is used to position the cursor. So
     * the range usually spans more then the actual symbol's name and does
     * normally include things like visibility modifiers.
     *
     * The range doesn't have to denote a node range in the sense of an abstract
     * syntax tree. It can therefore not be used to re-construct a hierarchy of
     * the symbols.
     */
    val location: Location,

    /**
     * The name of the symbol containing this symbol. This information is for
     * user interface purposes (e.g. to render a qualifier in the user interface
     * if necessary). It can't be used to re-infer a hierarchy for the document
     * symbols.
     */
    val containerName: String? = null,
)

@Serializable(with = WorkspaceSymbolResult.Serializer::class)
sealed interface WorkspaceSymbolResult {
    @Serializable
    @JvmInline
    value class SymbolInformations(val value: List<SymbolInformation>) : WorkspaceSymbolResult

    @Serializable
    @JvmInline
    value class WorkspaceSymbols(val value: List<WorkspaceSymbol>) : WorkspaceSymbolResult

    // NOTE: any SymbolInformation can successfully deserialise in a WorkspaceSymbol with the exception of its
    //       `deprecated' field. If such field is present we'll try to deserialise the JSON as SymbolInformation,
    //        if not, even if the sender meant to send SymbolInformatino (the `deprecated' field is optional), we
    //        still will deserialise it as a WorkspaceSymbol and it should just work. Note also, that SymbolInformation
    //        is itself deprecated and should not be used.
    /**
     * Streams the list with no tree: each element is read with the members of both variants
     * ([WorkspaceSymbolOrInformation]), then the first element picks the variant for all, as the tree way did: a
     * `deprecated` member (also `null`) makes symbol informations, anything else (also an empty list) workspace symbols.
     * Encode writes the variant with its own serializer.
     */
    class Serializer : KSerializer<WorkspaceSymbolResult> {
        override val descriptor: SerialDescriptor = unionDescriptor("WorkspaceSymbolResult")

        override fun serialize(encoder: Encoder, value: WorkspaceSymbolResult) {
            when (value) {
                is SymbolInformations -> encoder.encodeSerializableValue(SymbolInformations.serializer(), value)
                is WorkspaceSymbols -> encoder.encodeSerializableValue(WorkspaceSymbols.serializer(), value)
            }
        }

        override fun deserialize(decoder: Decoder): WorkspaceSymbolResult {
            val elements = decoder.decodeSerializableValue(DecodeOnlyList(WorkspaceSymbolOrInformation.Reader))
            return when (elements.firstOrNull()?.hasDeprecated) {
                true -> SymbolInformations(elements.map { it.symbolInformation() })
                else -> WorkspaceSymbols(elements.map { it.workspaceSymbol() })
            }
        }
    }
}

/**
 * One element of a [WorkspaceSymbolResult]: the members of [WorkspaceSymbol] and [SymbolInformation]. `location` is
 * read as the [WorkspaceSymbol.SymbolLocation] of a workspace symbol; a symbol information takes only its full form.
 */
private class WorkspaceSymbolOrInformation(
    val name: String?,
    val kind: SymbolKind?,
    val tags: List<SymbolTag>?,
    /** The object has a `deprecated` member, also `null`. */
    val hasDeprecated: Boolean,
    val deprecated: Boolean?,
    val location: WorkspaceSymbol.SymbolLocation?,
    val containerName: String?,
    val data: JsonElement?,
    val owner: WorkspaceSymbolOwner?,
) {
    fun workspaceSymbol(): WorkspaceSymbol {
        if (name == null || kind == null || location == null) {
            throw missingFields(WorkspaceSymbol.serializer().descriptor, "name" to name, "kind" to kind, "location" to location)
        }
        return WorkspaceSymbol(name = name, kind = kind, tags = tags, containerName = containerName, location = location, data = data,
                               owner = owner)
    }

    fun symbolInformation(): SymbolInformation {
        if (name == null || kind == null || location == null) {
            throw missingFields(SymbolInformation.serializer().descriptor, "name" to name, "kind" to kind, "location" to location)
        }
        val full = location as? WorkspaceSymbol.SymbolLocation.Full
                   ?: throw missingFields(Location.serializer().descriptor, "range" to null)
        return SymbolInformation(name = name, kind = kind, tags = tags, deprecated = deprecated, location = full.value,
                                 containerName = containerName)
    }

    /** Every member optional and nullable: a member of one variant is absent from the other, and `null` is not skipped. */
    object Reader : DeserializationStrategy<WorkspaceSymbolOrInformation> {
        private const val NAME = 0
        private const val KIND = 1
        private const val TAGS = 2
        private const val DEPRECATED = 3
        private const val LOCATION = 4
        private const val CONTAINER_NAME = 5
        private const val DATA = 6
        private const val OWNER = 7

        private val tagsSerializer = SymbolTagListSerializer().nullable

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.jetbrains.lsp.protocol.WorkspaceSymbolOrInformation") {
            annotations = ignoreUnknownKeys
            element("name", String.serializer().nullable.descriptor, isOptional = true)
            element("kind", SymbolKind.serializer().nullable.descriptor, isOptional = true)
            element("tags", tagsSerializer.descriptor, isOptional = true)
            element("deprecated", Boolean.serializer().nullable.descriptor, isOptional = true)
            element("location", WorkspaceSymbol.SymbolLocation.serializer().nullable.descriptor, isOptional = true)
            element("containerName", String.serializer().nullable.descriptor, isOptional = true)
            element("data", JsonElement.serializer().nullable.descriptor, isOptional = true)
            element("owner", WorkspaceSymbolOwner.serializer().nullable.descriptor, isOptional = true)
        }

        override fun deserialize(decoder: Decoder): WorkspaceSymbolOrInformation {
            var name: String? = null
            var kind: SymbolKind? = null
            var tags: List<SymbolTag>? = null
            var hasDeprecated = false
            var deprecated: Boolean? = null
            var location: WorkspaceSymbol.SymbolLocation? = null
            var containerName: String? = null
            var data: JsonElement? = null
            var owner: WorkspaceSymbolOwner? = null
            decoder.decodeStructure(descriptor) {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        NAME -> name = decodeNullableSerializableElement(descriptor, index, String.serializer())
                        KIND -> kind = decodeNullableSerializableElement(descriptor, index, SymbolKind.serializer())
                        TAGS -> tags = decodeSerializableElement(descriptor, index, tagsSerializer)
                        DEPRECATED -> {
                            hasDeprecated = true
                            deprecated = decodeNullableSerializableElement(descriptor, index, Boolean.serializer())
                        }
                        LOCATION -> location = decodeNullableSerializableElement(descriptor, index, WorkspaceSymbol.SymbolLocation.serializer())
                        CONTAINER_NAME -> containerName = decodeNullableSerializableElement(descriptor, index, String.serializer())
                        DATA -> data = decodeNullableSerializableElement(descriptor, index, JsonElement.serializer())
                        OWNER -> owner = decodeNullableSerializableElement(descriptor, index, WorkspaceSymbolOwner.serializer())
                        CompositeDecoder.DECODE_DONE -> break
                        else -> throw SerializationException("Unexpected index $index")
                    }
                }
            }
            return WorkspaceSymbolOrInformation(name, kind, tags, hasDeprecated, deprecated, location, containerName, data, owner)
        }
    }
}

/**
 * Parameters for the `workspace/textDocumentContent` request.
 *
 * @since 3.18.0
 */
@Serializable
data class TextDocumentContentParams(
    /**
     * The uri of the text document.
     */
    val uri: DocumentUri,
)

/**
 * Result of the `workspace/textDocumentContent` request.
 *
 * @since 3.18.0
 */
@Serializable
data class TextDocumentContentResult(
    /**
     * The text content of the text document. Please note, that the content of
     * any subsequent open notifications for the text document might differ
     * from the returned content due to whitespace and line ending
     * normalizations done on the client.
     */
    val text: String,
)

/**
 * Parameters for the `workspace/textDocumentContent/refresh` request.
 *
 * @since 3.18.0
 */
@Serializable
data class TextDocumentContentRefreshParams(
    /**
     * The uri of the text document to refresh.
     */
    val uri: DocumentUri,
)

/**
 * Text document content provider options.
 *
 * @since 3.18.0
 */
@Serializable
data class TextDocumentContentOptions(
    /**
     * The schemes for which the server provides content.
     */
    val schemes: List<String>,
)

object Workspace {
    val WorkspaceFolders: RequestType<Nothing?, List<WorkspaceFolder>, Unit> =
        RequestType("workspace/workspaceFolders", NoValueSerializer, ListSerializer(WorkspaceFolder.serializer()), Unit.serializer())
    val DidChangeWorkspaceFolders: NotificationType<DidChangeWorkspaceFoldersParams> =
        NotificationType("workspace/didChangeWorkspaceFolders", DidChangeWorkspaceFoldersParams.serializer())
    val DidChangeConfiguration: NotificationType<DidChangeConfigurationParams> =
        NotificationType("workspace/didChangeConfiguration", DidChangeConfigurationParams.serializer())
    val Configuration: RequestType<ConfigurationParams, List<JsonElement?>, Unit> =
        RequestType("workspace/configuration", ConfigurationParams.serializer(), ListSerializer(JsonElement.serializer().nullable), Unit.serializer())

    val WillRenameFiles: RequestType<RenameFilesParams, WorkspaceEdit?, Unit> =
        RequestType("workspace/willRenameFiles", RenameFilesParams.serializer(), WorkspaceEdit.serializer().nullable, Unit.serializer())

    val DidRenameFiles: NotificationType<RenameFilesParams> =
        NotificationType("workspace/didRenameFiles", RenameFilesParams.serializer())

    val WillCreateFiles: RequestType<CreateFilesParams, WorkspaceEdit?, Unit> =
        RequestType("workspace/willCreateFiles", CreateFilesParams.serializer(), WorkspaceEdit.serializer().nullable, Unit.serializer())

    val DidCreateFiles: NotificationType<CreateFilesParams> =
        NotificationType("workspace/didCreateFiles", CreateFilesParams.serializer())

    val WillDeleteFiles: RequestType<DeleteFilesParams, WorkspaceEdit?, Unit> =
        RequestType("workspace/willDeleteFiles", DeleteFilesParams.serializer(), WorkspaceEdit.serializer().nullable, Unit.serializer())

    val DidDeleteFiles: NotificationType<DeleteFilesParams> =
        NotificationType("workspace/didDeleteFiles", DeleteFilesParams.serializer())

    val RefreshCodeLenses: RequestType<Nothing?, Nothing?, Nothing?> =
        RequestType("workspace/codeLens/refresh", NoValueSerializer, NoValueSerializer, NoValueSerializer)

    val RefreshInlayHints: RequestType<Nothing?, Nothing?, Nothing?> =
        RequestType("workspace/inlayHint/refresh", NoValueSerializer, NoValueSerializer, NoValueSerializer)

    val RefreshSemanticTokens: RequestType<Nothing?, Nothing?, Nothing?> =
        RequestType("workspace/semanticTokens/refresh", NoValueSerializer, NoValueSerializer, NoValueSerializer)

    val RefreshFoldingRanges: RequestType<Nothing?, Nothing?, Nothing?> =
        RequestType("workspace/foldingRange/refresh", NoValueSerializer, NoValueSerializer, NoValueSerializer)

    val DidChangeWatchedFiles: NotificationType<DidChangeWatchedFilesParams> =
        NotificationType("workspace/didChangeWatchedFiles", DidChangeWatchedFilesParams.serializer())
    // val ExecuteCommand: RequestType<ExecuteCommandParams, ExecuteCommandResponse?, ExecuteCommandError> =
    //     RequestType("workspace/executeCommand", ExecuteCommandParams.serializer(), ExecuteCommandResponse.serializer().nullable, ExecuteCommandError.serializer())

    /**
     * The `workspace/textDocumentContent` request is sent from the client to the server
     * to request the content of a text document the server provides, for a URI scheme
     * declared in [TextDocumentContentOptions.schemes].
     *
     * @since 3.18.0
     */
    val TextDocumentContent: RequestType<TextDocumentContentParams, TextDocumentContentResult, Unit> =
        RequestType("workspace/textDocumentContent", TextDocumentContentParams.serializer(), TextDocumentContentResult.serializer(), Unit.serializer())

    /**
     * The `workspace/textDocumentContent/refresh` request is sent from the server to the client
     * to refresh the content of a specific text document.
     *
     * @since 3.18.0
     */
    val RefreshTextDocumentContent: RequestType<TextDocumentContentRefreshParams, Nothing?, Nothing?> =
        RequestType("workspace/textDocumentContent/refresh", TextDocumentContentRefreshParams.serializer(), NoValueSerializer, NoValueSerializer)

    val Symbol: RequestType<WorkspaceSymbolParams, WorkspaceSymbolResult?, Unit> =
        RequestType("workspace/symbol", WorkspaceSymbolParams.serializer(), WorkspaceSymbolResult.serializer().nullable, Unit.serializer())

    val ResolveSymbol: RequestType<WorkspaceSymbol, WorkspaceSymbol, Unit> =
        RequestType("workspaceSymbol/resolve", WorkspaceSymbol.serializer(), WorkspaceSymbol.serializer(), Unit.serializer())
}