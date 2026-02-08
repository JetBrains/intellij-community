package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
    val scopeUri: URI?,
    val section: String?,
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
    val kind: WatchKind?,
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
    deserialize = { FileChangeType.entries[it - 1] },
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
    val tags: List<SymbolTag>?,

    /**
     * Indicates if this symbol is deprecated.
     *
     * @deprecated Use tags instead
     */
    val deprecated: Boolean?,

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
    val containerName: String?,
)

@Serializable(with = WorkspaceSymbolResult.Serializer::class)
sealed interface WorkspaceSymbolResult {
    @Serializable
    @JvmInline
    value class SymbolInformations(val value: List<SymbolInformation>) : WorkspaceSymbolResult

    @Serializable
    @JvmInline
    value class WorkspaceSymbols(val value: List<WorkspaceSymbol>) : WorkspaceSymbolResult

    class Serializer : JsonContentPolymorphicSerializer<WorkspaceSymbolResult>(WorkspaceSymbolResult::class) {
        // NOTE: any SymbolInformation can successfully deserialise in a WorkspaceSymbol with the exception of its
        //       `deprecated' field. If such field is present we'll try to deserialise the JSON as SymbolInformation,
        //        if not, even if the sender meant to send SymbolInformatino (the `deprecated' field is optional), we
        //        still will deserialise it as a WorkspaceSymbol and it should just work. Note also, that SymbolInformation
        //        is itself deprecated and should not be used.
        private fun isSymbolInformation(element: JsonElement) = element is JsonObject && element.containsKey("deprecated")

        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<WorkspaceSymbolResult> {
            return when {
                element is JsonArray -> {
                    if (element.isNotEmpty() && isSymbolInformation(element[0])) {
                        SymbolInformations.serializer()
                    }
                    else {
                        WorkspaceSymbols.serializer()
                    }
                }
                else -> throw SerializationException("Expected an array of either WorkspaceSymbol or SymbolInformation.")
            }
        }
    }
}

object Workspace {
    val WorkspaceFolders: RequestType<Unit, List<WorkspaceFolder>, Unit> =
        RequestType("workspace/folders", Unit.serializer(), ListSerializer(WorkspaceFolder.serializer()), Unit.serializer())
    val DidChangeWorkspaceFolders: NotificationType<DidChangeWorkspaceFoldersParams> =
        NotificationType("workspace/didChangeWorkspaceFolders", DidChangeWorkspaceFoldersParams.serializer())
    val DidChangeConfiguration: NotificationType<DidChangeConfigurationParams> =
        NotificationType("workspace/didChangeConfiguration", DidChangeConfigurationParams.serializer())
    val Configuration: RequestType<ConfigurationParams, List<JsonElement?>, Unit> =
        RequestType("workspace/configuration", ConfigurationParams.serializer(), ListSerializer(JsonElement.serializer().nullable), Unit.serializer())

    val WillRenameFiles: RequestType<RenameFilesParams, WorkspaceEdit?, Unit> =
        RequestType("workspace/willRenameFiles", RenameFilesParams.serializer(), WorkspaceEdit.serializer().nullable, Unit.serializer())

    val RefreshCodeLenses: RequestType<Unit, Unit, Unit> =
        RequestType("workspace/codeLens/refresh", Unit.serializer(), Unit.serializer(), Unit.serializer())

    val RefreshInlayHints: RequestType<Unit, Unit, Unit> =
        RequestType("workspace/inlayHint/refresh", Unit.serializer(), Unit.serializer(), Unit.serializer())

    val RefreshSemanticTokens: RequestType<Unit, Unit, Unit> =
        RequestType("workspace/semanticTokens/refresh", Unit.serializer(), Unit.serializer(), Unit.serializer())

    val DidChangeWatchedFiles: NotificationType<DidChangeWatchedFilesParams> =
        NotificationType("workspace/didChangeWatchedFiles", DidChangeWatchedFilesParams.serializer())
    // val ExecuteCommand: RequestType<ExecuteCommandParams, ExecuteCommandResponse?, ExecuteCommandError> =
    //     RequestType("workspace/executeCommand", ExecuteCommandParams.serializer(), ExecuteCommandResponse.serializer().nullable, ExecuteCommandError.serializer())

    val Symbol: RequestType<WorkspaceSymbolParams, WorkspaceSymbolResult?, Unit> =
        RequestType("workspace/symbol", WorkspaceSymbolParams.serializer(), WorkspaceSymbolResult.serializer().nullable, Unit.serializer())

    val ResolveSymbol: RequestType<WorkspaceSymbol, WorkspaceSymbol, Unit> =
        RequestType("workspaceSymbol/resolve", WorkspaceSymbol.serializer(), WorkspaceSymbol.serializer(), Unit.serializer())
}