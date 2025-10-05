package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@Serializable
data class ExecuteCommandOptions(
    val commands: List<String>,
)

/**
 * Represents parameters for executing a command.
 * Extends WorkDoneProgressParams.
 */
@Serializable
data class ExecuteCommandParams(
    /**
     * The identifier of the actual command handler.
     */
    val command: String,

    /**
     * Arguments that the command should be invoked with.
     */
    val arguments: List<JsonElement>? = null,

    override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams

object Commands {
    val ExecuteCommand: RequestType<ExecuteCommandParams, JsonElement, Unit> = RequestType(
        "workspace/executeCommand",
        ExecuteCommandParams.serializer(),
        JsonElement.serializer(), Unit.serializer()
    )
}