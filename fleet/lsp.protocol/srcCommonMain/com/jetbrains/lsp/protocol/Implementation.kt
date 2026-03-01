package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/**
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#implementationParams>ImplementationParams (LSP spec)</a>
 */
@Serializable
data class ImplementationParams(
  override val textDocument: TextDocumentIdentifier,
  override val position: Position,
  override val workDoneToken: ProgressToken? = null,
  override val partialResultToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams, PartialResultParams

object Implementation {

  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation">textDocument/implementation (LSP spec)</a>
   */
  val ImplementationRequest: RequestType<ImplementationParams, Locations?, Unit> = RequestType(
    method = "textDocument/implementation",
    paramsSerializer = ImplementationParams.serializer(),
    resultSerializer = Locations.serializer().nullable,
    errorSerializer = Unit.serializer(),
  )
}
