package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.nullable

@Serializable
data class SemanticTokensLegend(
    /**
     * The token types a server uses.
     */
    val tokenTypes: List<String>,

    /**
     * The token modifiers a server uses.
     */
    val tokenModifiers: List<String>,
)

@Serializable
data class SemanticTokensParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,
    override val workDoneToken: ProgressToken?,
    override val partialResultToken: ProgressToken?,
) : WorkDoneProgressParams, PartialResultParams


@Serializable
data class SemanticTokensRangeParams(
  /**
   * The text document.
   */
  val textDocument: TextDocumentIdentifier,
  /**
   * The range the semantic tokens are requested for.
   */
  val range: Range,
  override val workDoneToken: ProgressToken?,
  override val partialResultToken: ProgressToken?,
) : WorkDoneProgressParams, PartialResultParams


@Serializable
data class SemanticTokens(
    /**
     * An optional result id. If provided and clients support delta updating
     * the client will include the result id in the next semantic token request.
     * A server can then instead of computing all semantic tokens again simply
     * send a delta.
     */
    val resultId: String?,

    /**
     * The actual tokens.
     */
    val data: List<Int>,
)


object SemanticTokensRequests {
    val SemanticTokensFullRequest: RequestType<SemanticTokensParams, SemanticTokens, Nothing?> =
        RequestType(
            "textDocument/semanticTokens/full", SemanticTokensParams.serializer(), SemanticTokens.serializer(),
            NothingSerializer().nullable
        )

    val SemanticTokensRangeRequest: RequestType<SemanticTokensRangeParams, SemanticTokens, Nothing?> =
      RequestType(
        "textDocument/semanticTokens/range", SemanticTokensRangeParams.serializer(), SemanticTokens.serializer(),
        NothingSerializer().nullable
      )
}