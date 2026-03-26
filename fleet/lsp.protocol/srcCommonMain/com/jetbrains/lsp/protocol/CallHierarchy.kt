// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * The parameter of a `textDocument/prepareCallHierarchy` request.
 *
 * @since 3.16.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchyPrepareParams">callHierarchyPrepareParams (LSP spec)</a>
 */
@Serializable
data class CallHierarchyPrepareParams(
  override val textDocument: TextDocumentIdentifier,
  override val position: Position,
  override val workDoneToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams

/**
 * Represents programming constructs like functions or constructors
 * in the context of call hierarchy.
 *
 * @since 3.16.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchyItem">callHierarchyItem (LSP spec)</a>
 */
@Serializable
data class CallHierarchyItem(
  /**
   * The name of this item.
   */
  val name: String,

  /**
   * The kind of this item.
   */
  val kind: SymbolKind,

  /**
   * Tags for this item.
   */
  val tags: List<SymbolTag>? = null,

  /**
   * More detail for this item, e.g. the signature of a function.
   */
  val detail: String? = null,

  /**
   * The resource identifier of this item.
   */
  val uri: DocumentUri,

  /**
   * The range enclosing this symbol not including leading/trailing whitespace
   * but everything else, e.g. comments and code.
   */
  val range: Range,

  /**
   * The range that should be selected and revealed when this symbol is being
   * picked, e.g. the name of a function. Must be contained by the
   * {@link CallHierarchyItem.range `range`}.
   */
  val selectionRange: Range,

  /**
   * A data entry field that is preserved between a call hierarchy prepare and
   * incoming calls or outgoing calls requests. It could also be used to
   * identify the call hierarchy in the server, helping improve the performance
   * on resolving incoming and outgoing calls.
   */
  val data: JsonElement? = null,
)

/**
 * The parameter of a `callHierarchy/incomingCalls` request.
 *
 * @since 3.16.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchyIncomingCallsParams">callHierarchyIncomingCallsParams (LSP spec)</a>
 */
@Serializable
data class CallHierarchyIncomingCallsParams(
  val item: CallHierarchyItem,
  override val partialResultToken: ProgressToken? = null,
  override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

/**
 * Represents an incoming call, e.g. a caller of a method or constructor.
 *
 * @since 3.16.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchyIncomingCall">callHierarchyIncomingCall (LSP spec)</a>
 */
@Serializable
data class CallHierarchyIncomingCall(
  /**
   * The item that makes the call.
   */
  val from: CallHierarchyItem,

  /**
   * The ranges at which the calls appear. This is relative to the caller
   * denoted by {@link CallHierarchyIncomingCall.from `this.from`}.
   */
  val fromRanges: List<Range>,
)

/**
 * The parameter of a `callHierarchy/outgoingCalls` request.
 *
 * @since 3.16.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchyOutgoingCallsParams">callHierarchyOutgoingCallsParams (LSP spec)</a>
 */
@Serializable
data class CallHierarchyOutgoingCallsParams(
  val item: CallHierarchyItem,
  override val partialResultToken: ProgressToken? = null,
  override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

/**
 * Represents an outgoing call, e.g. calling a getter from a method or
 * a method from a constructor etc.
 *
 * @since 3.16.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchyOutgoingCall">callHierarchyOutgoingCall (LSP spec)</a>
 */
@Serializable
data class CallHierarchyOutgoingCall(
  /**
   * The item that is called.
   */
  val to: CallHierarchyItem,

  /**
   * The range at which this item is called. This is the range relative to
   * the caller, e.g the item passed to `callHierarchy/outgoingCalls` request.
   */
  val fromRanges: List<Range>,
)

object CallHierarchyRequests {
  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareCallHierarchy">textDocument/prepareCallHierarchy</a>
   */
  val PrepareCallHierarchyRequestType: RequestType<CallHierarchyPrepareParams, List<CallHierarchyItem>?, Unit> = RequestType(
    method = "textDocument/prepareCallHierarchy",
    paramsSerializer = CallHierarchyPrepareParams.serializer(),
    resultSerializer = ListSerializer(elementSerializer = CallHierarchyItem.serializer()).nullable,
    errorSerializer = Unit.serializer(),
  )

  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_incomingCalls">callHierarchy/incomingCalls</a>
   */
  val IncomingCallsRequestType: RequestType<CallHierarchyIncomingCallsParams, List<CallHierarchyIncomingCall>?, Unit> = RequestType(
    method = "callHierarchy/incomingCalls",
    paramsSerializer = CallHierarchyIncomingCallsParams.serializer(),
    resultSerializer = ListSerializer(elementSerializer = CallHierarchyIncomingCall.serializer()).nullable,
    errorSerializer = Unit.serializer(),
  )

  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_outgoingCalls">callHierarchy/outgoingCalls</a>
   */
  val OutgoingCallsRequestType: RequestType<CallHierarchyOutgoingCallsParams, List<CallHierarchyOutgoingCall>?, Unit> = RequestType(
    method = "callHierarchy/outgoingCalls",
    paramsSerializer = CallHierarchyOutgoingCallsParams.serializer(),
    resultSerializer = ListSerializer(elementSerializer = CallHierarchyOutgoingCall.serializer()).nullable,
    errorSerializer = Unit.serializer(),
  )
}
