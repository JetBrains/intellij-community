package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class FoldingRangeParams(
  val textDocument: TextDocumentIdentifier,
  override val workDoneToken: ProgressToken? = null,
  override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

@Serializable
data class FoldingRange(
  /**
   * The zero-based line number from where the folded range starts.
   */
  val startLine: Int,

  /**
   * The zero-based line number where the folded range ends.
   */
  val endLine: Int,

  /**
   * The zero-based character offset from where the folded range starts. If not defined, defaults
   * to the length of the start line.
   */
  val startCharacter: Int? = null,

  /**
   * The zero-based character offset before the folded range ends. If not defined, defaults to the
   * length of the end line.
   */
  val endCharacter: Int? = null,

  /**
   * Describes the kind of the folding range such as [FoldingRangeKind.Comment] or [FoldingRangeKind.Region].
   * The kind is used to categorize folding ranges and used by commands like 'Fold all comments'.
   * @see FoldingRangeKind
   */
  val kind: FoldingRangeKind? = null,

  /**
   * The text that the client should show when the specified range is
   * collapsed. If not defined or not supported by the client, a default
   * will be chosen by the client.
   *
   * @since 3.17.0
   */
  val collapsedText: String? = null
)

val FoldingRangeRequestType: RequestType<FoldingRangeParams, List<FoldingRange>, Unit> =
  RequestType(
    "textDocument/foldingRange",
    FoldingRangeParams.serializer(),
    ListSerializer(FoldingRange.serializer()),
    Unit.serializer(),
  )
