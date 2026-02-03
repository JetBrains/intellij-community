package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@Serializable
data class CodeLensParams(
    /**
     * The document to request code lens for.
     */
    val textDocument: TextDocumentIdentifier,

    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

@Serializable
data class CodeLens(
    /**
     * The range in which this code lens is valid. Should only span a single
     * line.
     */
    val range: Range,

    /**
     * The command this code lens represents.
     */
    val command: Command?,

    /**
     * A data entry field that is preserved on a code lens item between
     * a code lens and a code lens resolve request.
     */
    val data: JsonElement?,
)

object CodeLenses {
    val CodeLensRequestType: RequestType<CodeLensParams, List<CodeLens>?, Unit> =
        RequestType(
          "textDocument/codeLens",
          CodeLensParams.serializer(), ListSerializer(CodeLens.serializer()).nullable,
          Unit.serializer())

    val ResolveCodeLens: RequestType<CodeLens, CodeLens, Unit> =
        RequestType(
          "codeLens/resolve",
          CodeLens.serializer(), CodeLens.serializer(),
          Unit.serializer())
}

