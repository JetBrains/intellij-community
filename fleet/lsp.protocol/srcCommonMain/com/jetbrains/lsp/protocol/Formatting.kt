package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class DocumentRangeFormattingParams(
    /**
     * The document to format.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The range to format
     */
    val range: Range,

    /**
     * The format options
     */
    val options: FormattingOptions,

    override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams

/**
 * Value-object describing what options formatting should use.
 */
@Serializable
data class FormattingOptions(
    /**
     * Size of a tab in spaces. Unsigned.
     */
    val tabSize: Int,

    /**
     * Prefer spaces over tabs.
     */
    val insertSpaces: Boolean,

    /**
     * Trim trailing whitespace on a line.
     *
     * @since 3.15.0
     */
    val trimTrailingWhitespace: Boolean? = null,

    /**
     * Insert a newline character at the end of the file if one does not exist.
     *
     * @since 3.15.0
     */
    val insertFinalNewline: Boolean? = null,

    /**
     * Trim all newlines after the final newline at the end of the file.
     *
     * @since 3.15.0
     */
    val trimFinalNewlines: Boolean? = null,

    /**
     * Signature for further properties.
     */
    // [key: string]: boolean | integer | string;
)

@Serializable
data class DocumentFormattingParams(
    /**
    * The document to format.
    */
   val textDocument: TextDocumentIdentifier,

   /**
    * The format options.
    */
   val options: FormattingOptions,

    override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams

val FormattingRequestType: RequestType<DocumentFormattingParams, List<TextEdit>?, Unit> =
    RequestType("textDocument/formatting", DocumentFormattingParams.serializer(), ListSerializer(TextEdit.serializer()).nullable, Unit.serializer())

val RangeFormattingRequestType: RequestType<DocumentRangeFormattingParams, List<TextEdit>?, Unit> =
    RequestType("textDocument/rangeFormatting", DocumentRangeFormattingParams.serializer(), ListSerializer(TextEdit.serializer()).nullable, Unit.serializer())
