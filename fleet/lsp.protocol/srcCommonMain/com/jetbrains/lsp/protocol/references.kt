package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer


interface ReferenceOptions : WorkDoneProgressOptions

@Serializable
data class ReferenceParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    val context: ReferenceContext,
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams, PartialResultParams

@Serializable
data class ReferenceContext(
    /**
     * Include the declaration of the current symbol.
     */
    val includeDeclaration: Boolean,
)

val ReferenceRequestType: RequestType<ReferenceParams, List<Location>?, Unit> =
    RequestType("textDocument/references", ReferenceParams.serializer(), ListSerializer(Location.serializer()).nullable, Unit.serializer())
