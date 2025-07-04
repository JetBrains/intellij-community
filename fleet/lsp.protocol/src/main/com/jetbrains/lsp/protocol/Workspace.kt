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