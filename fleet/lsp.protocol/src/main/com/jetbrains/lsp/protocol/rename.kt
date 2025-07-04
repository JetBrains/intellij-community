package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
data class RenameParams(
  /**
   * The text document.
   */
  override val textDocument: TextDocumentIdentifier,

  /**
   * The position inside the text document.
   */
  override val position: Position,

  /**
   * The new name of the symbol. If the given name is not valid the
   * request must return a [ResponseError](#ResponseError) with an
   * appropriate message set.
   */
  val newName: String,

  /**
   * An optional token that a server can use to report work done progress.
   */
  override val workDoneToken: ProgressToken?
): TextDocumentPositionParams, WorkDoneProgressParams

val RenameRequestType: RequestType<RenameParams, WorkspaceEdit?, Unit> =
  RequestType("textDocument/rename", RenameParams.serializer(), WorkspaceEdit.serializer().nullable, Unit.serializer())