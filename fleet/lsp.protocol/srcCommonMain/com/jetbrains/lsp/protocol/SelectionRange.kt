package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class SelectionRangeParams(
    /**
    * The text document.
    */
   val textDocument: TextDocumentIdentifier,

   /**
    * The positions inside the text document.
    */
   val positions: List<Position>,

    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

@Serializable
data class SelectionRange(
    /**
    * The [range](#Range) of this selection range.
    */
   val range: Range,

   /**
    * The parent selection range containing this range. Therefore
    * `parent.range` must contain `this.range`.
    */
   val parent: SelectionRange?,
)

val SelectionRangeRequestType: RequestType<SelectionRangeParams, List<SelectionRange>?, Unit> =
    RequestType("textDocument/selectionRange", SelectionRangeParams.serializer(), ListSerializer(SelectionRange.serializer()).nullable, Unit.serializer())