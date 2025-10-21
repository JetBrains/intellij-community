package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ImplementationParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams, PartialResultParams

object Implementation {
    val ImplementationRequest: RequestType<ImplementationParams, Locations?, Unit> =
        RequestType("textDocument/implementation", ImplementationParams.serializer(), Locations.serializer().nullable, Unit.serializer())
}