package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class DocumentColorParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

@Serializable
data class ColorInformation(
    /**
    * The range in the document where this color appears.
    */
   val range: Range,

   /**
    * The actual color value for this color range.
    */
   val color: Color,
)

@Serializable
data class Color(
    /**
    * The red component of this color in the range [0-1].
    */
   val red: Double,

   /**
    * The green component of this color in the range [0-1].
    */
   val green: Double,

   /**
    * The blue component of this color in the range [0-1].
    */
   val blue: Double,

   /**
    * The alpha component of this color in the range [0-1].
    */
   val alpha: Double,
)

object DocumentColors {
    val DocumentColor: RequestType<DocumentColorParams, List<ColorInformation>, Unit> =
        RequestType("textDocument/documentColor", DocumentColorParams.serializer(), ListSerializer(ColorInformation.serializer()), Unit.serializer())
}