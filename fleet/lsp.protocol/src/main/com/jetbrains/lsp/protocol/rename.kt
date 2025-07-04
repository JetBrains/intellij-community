package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class PrepareRenameParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams

@Serializable(with = PrepareRenameResult.Serializer::class)
sealed interface PrepareRenameResult {
    @Serializable
    @JvmInline
    value class Range(val value: com.jetbrains.lsp.protocol.Range) : PrepareRenameResult

    @Serializable
    data class Labeled(
        val range: com.jetbrains.lsp.protocol.Range,
        val placeholder: String,
    ) : PrepareRenameResult

    @Serializable
    data class Default(val defaultBehavior: Boolean) : PrepareRenameResult

    class Serializer : JsonContentPolymorphicSerializer<PrepareRenameResult>(PrepareRenameResult::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PrepareRenameResult> {
            return when (element) {
                is JsonObject -> when {
                    element.containsKey("range") -> Labeled.serializer()
                    element.containsKey("defaultBehavior") -> Default.serializer()
                    else -> Range.serializer()
                }
                else -> throw SerializationException("Expected either a Range, labeled Range or the default behavior object.")
            }
        }
    }
}

@Serializable
data class RenameParams(
    /**
     * The text document.
     */
    override val textDocument: TextDocumentIdentifier,

    /**
     * The position inside the text document.
     */
    override val position: Position,

    /**
     * The new name of the symbol. If the given name is not valid the
     * request must return a [ResponseError](#ResponseError) with an
     * appropriate message set.
     */
    val newName: String,

    /**
     * An optional token that a server can use to report work done progress.
     */
    override val workDoneToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams

val PrepareRenameRequestType: RequestType<PrepareRenameParams, PrepareRenameResult?, Unit> =
    RequestType("textDocument/prepareRename", PrepareRenameParams.serializer(), PrepareRenameResult.serializer().nullable, Unit.serializer())

val RenameRequestType: RequestType<RenameParams, WorkspaceEdit?, Unit> =
    RequestType("textDocument/rename", RenameParams.serializer(), WorkspaceEdit.serializer().nullable, Unit.serializer())