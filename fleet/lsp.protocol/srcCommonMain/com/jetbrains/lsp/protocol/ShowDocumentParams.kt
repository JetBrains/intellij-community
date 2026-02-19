package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Params to show a resource.
 * The show document request is sent from a server to a client to ask the client
 * to display a particular resource referenced by a URI in the user interface.
 *
 * @since 3.16.0
 */
@Serializable
data class ShowDocumentParams(
  /**
   * The uri to show.
   */
  val uri: URI,

  /**
   * Indicates to show the resource in an external program.
   * To show, for example, `https://code.visualstudio.com/`
   * in the default WEB browser set `external` to `true`.
   */
  val external: Boolean? = false,

  /**
   * An optional property to indicate whether the editor
   * showing the document should take focus or not.
   * Clients might ignore this property if an external
   * program is started.
   */
  val takeFocus: Boolean? = false,

  /**
   * An optional selection range if the document is a text
   * document. Clients might ignore the property if an
   * external program is started or the file is not a text
   * file.
   */
  val selection: Range? = null,
)

/**
 * The result of an show document request.
 *
 * @since 3.16.0
 */
@Serializable
data class ShowDocumentResult(
  /**
   * A boolean indicating if the show was successful.
   */
  val success: Boolean
)

val ShowDocument: RequestType<ShowDocumentParams, ShowDocumentResult, Unit> = RequestType(
  "window/showDocument",
  ShowDocumentParams.serializer(),
  ShowDocumentResult.serializer(),
  Unit.serializer(),
)
