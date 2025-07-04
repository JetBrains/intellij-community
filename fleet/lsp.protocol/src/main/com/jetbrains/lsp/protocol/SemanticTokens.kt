package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
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

@Serializable
data class SemanticTokensDeltaParams(
  /**
   * The text document.
   */
  val textDocument: TextDocumentIdentifier,

  /**
   * The result id of a previous response. The result Id can either point to
   * a full response or a delta response depending on what was received last.
   */
  val previousResultId: String,

  override val workDoneToken: ProgressToken? = null,
  override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

@Serializable
data class SemanticTokensDelta(
  val resultId: String?,

  /**
   * The semantic token edits to transform a previous result into a new
   * result.
   */
  val edits: List<SemanticTokensEdit>,
)

@Serializable
data class SemanticTokensEdit(
  /**
   * The start offset of the edit. Unsigned.
   */
  val start: Int,

  /**
   * The count of elements to remove. Unsigned.
   */
  val deleteCount: Int,

  /**
   * The elements to insert. Unsigned.
   */
  val data: List<Int>?,
)

@Serializable(with = SemanticTokensDeltaResult.Serializer::class)
sealed interface SemanticTokensDeltaResult {
  @Serializable
  @JvmInline
  value class Full(val value: SemanticTokens) : SemanticTokensDeltaResult

  @Serializable
  @JvmInline
  value class Delta(val value: SemanticTokensDelta) : SemanticTokensDeltaResult

  class Serializer : JsonContentPolymorphicSerializer<SemanticTokensDeltaResult>(SemanticTokensDeltaResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SemanticTokensDeltaResult> {
      return if (element is JsonObject && element.containsKey("edits")) Delta.serializer() else Full.serializer()
    }
  }

}

object SemanticTokensRequests {
    val SemanticTokensFullRequest: RequestType<SemanticTokensParams, SemanticTokens?, Nothing?> =
        RequestType(
            "textDocument/semanticTokens/full",
            SemanticTokensParams.serializer(), SemanticTokens.serializer().nullable,
            NothingSerializer().nullable)

    val SemanticTokensRangeRequest: RequestType<SemanticTokensRangeParams, SemanticTokens, Nothing?> =
        RequestType(
            "textDocument/semanticTokens/range", SemanticTokensRangeParams.serializer(), SemanticTokens.serializer(),
            NothingSerializer().nullable)

    val SemanticTokensFullDeltaRequest: RequestType<SemanticTokensDeltaParams, SemanticTokensDeltaResult?, Unit> =
        RequestType(
            "textDocument/semanticTokens/full/delta",
            SemanticTokensDeltaParams.serializer(), SemanticTokensDeltaResult.serializer().nullable,
            Unit.serializer())
}