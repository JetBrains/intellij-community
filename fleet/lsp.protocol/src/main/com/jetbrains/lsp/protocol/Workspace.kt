package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@Serializable
data class DidChangeWorkspaceFoldersParams(
    /**
     * The actual workspace folder change event.
     */
    val event: WorkspaceFoldersChangeEvent
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
    val removed: List<WorkspaceFolder>
)

@Serializable
data class DidChangeConfigurationParams(
    /**
     * The actual changed settings
     */
    val settings: JsonElement
)

object Workspace {
    val WorkspaceFolders: RequestType<Unit, List<WorkspaceFolder>, Unit> =
        RequestType("workspace/folders", Unit.serializer(), ListSerializer(WorkspaceFolder.serializer()), Unit.serializer())
    val DidChangeWorkspaceFolders: NotificationType<DidChangeWorkspaceFoldersParams> =
        NotificationType("workspace/didChangeWorkspaceFolders", DidChangeWorkspaceFoldersParams.serializer())
    val DidChangeConfiguration: NotificationType<DidChangeConfigurationParams> =
        NotificationType("workspace/didChangeConfiguration", DidChangeConfigurationParams.serializer())

    //  val DidChangeWatchedFiles: NotificationType<DidChangeWatchedFilesParams> =
    //    NotificationType("workspace/didChangeWatchedFiles", DidChangeWatchedFilesParams.serializer())
    //  val ExecuteCommand: RequestType<ExecuteCommandParams, ExecuteCommandResponse?, ExecuteCommandError> =
    //    RequestType("workspace/executeCommand", ExecuteCommandParams.serializer(), ExecuteCommandResponse.serializer().nullable, ExecuteCommandError.serializer())
    //  val Symbol: RequestType<SymbolParams, List<SymbolInformation>, Unit> =
    //    RequestType("workspace/symbol", SymbolParams.serializer(), ListSerializer(SymbolInformation.serializer()), Unit.serializer())
}