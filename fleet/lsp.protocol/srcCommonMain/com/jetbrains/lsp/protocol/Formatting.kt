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

/**
 * Parameters of the `textDocument/onTypeFormatting` request.
 */
@Serializable
data class DocumentOnTypeFormattingParams(
    /**
     * The document to format.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The position around which the on type formatting should happen.
     * This is not necessarily the exact position where the character denoted
     * by the property `ch` got typed.
     */
    val position: Position,

    /**
     * The character that has been typed that triggered the formatting
     * on type request. That is not necessarily the last character that
     * got inserted into the document since the client could auto insert
     * characters as well (e.g. like automatic brace completion).
     */
    val ch: String,

    /**
     * The formatting options.
     */
    val options: FormattingOptions,
)

/**
 * Parameters of the `textDocument/rangesFormatting` request.
 *
 * @since 3.18.0
 */
@Serializable
data class DocumentRangesFormattingParams(
    /**
     * The document to format.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The ranges to format.
     */
    val ranges: List<Range>,

    /**
     * The format options.
     */
    val options: FormattingOptions,

    override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams

val OnTypeFormattingRequestType: RequestType<DocumentOnTypeFormattingParams, List<TextEdit>?, Unit> =
    RequestType("textDocument/onTypeFormatting", DocumentOnTypeFormattingParams.serializer(), ListSerializer(TextEdit.serializer()).nullable, Unit.serializer())

/**
 * @since 3.18.0
 */
val RangesFormattingRequestType: RequestType<DocumentRangesFormattingParams, List<TextEdit>?, Unit> =
    RequestType("textDocument/rangesFormatting", DocumentRangesFormattingParams.serializer(), ListSerializer(TextEdit.serializer()).nullable, Unit.serializer())
