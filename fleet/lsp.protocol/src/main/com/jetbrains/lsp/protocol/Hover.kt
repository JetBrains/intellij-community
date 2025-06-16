package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class HoverParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken?,
) : TextDocumentPositionParams, WorkDoneProgressParams


/**
 * The result of a hover request.
 */
@Serializable
data class Hover(
    /**
     * The hover's content
     */
    val contents: MarkupContent, // actually, it's contents: `MarkedString | MarkedString[] | MarkupContent` but `MarkedString` is deprecated

    /**
     * An optional range is a range inside a text document
     * that is used to visualize a hover, e.g. by changing the background color.
     */
    val range: Range?,
)

/**
 * The hover request is sent from the client to the server to request hover information at a given text document position.
 */
val HoverRequestType: RequestType<HoverParams, Hover?, Unit> =
    RequestType("textDocument/hover", HoverParams.serializer(), Hover.serializer().nullable, Unit.serializer())