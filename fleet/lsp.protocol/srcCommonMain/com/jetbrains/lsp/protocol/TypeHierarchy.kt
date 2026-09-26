// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * The parameter of a `textDocument/prepareTypeHierarchy` request.
 *
 * @since 3.17.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchyPrepareParams">typeHierarchyPrepareParams (LSP spec)</a>
 */
@Serializable
data class TypeHierarchyPrepareParams(
  override val textDocument: TextDocumentIdentifier,
  override val position: Position,
  override val workDoneToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams

/**
 * The parameter of a `typeHierarchy/supertypes` request.
 *
 * @since 3.17.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchySupertypesParams">typeHierarchySupertypesParams (LSP spec)</a>
 */
@Serializable
data class TypeHierarchySupertypesParams(
  val item: TypeHierarchyItem,
  override val partialResultToken: ProgressToken? = null,
  override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

/**
 * The parameter of a `typeHierarchy/subtypes` request.
 *
 * @since 3.17.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchySubtypesParams">typeHierarchySubtypesParams (LSP spec)</a>
 */
@Serializable
data class TypeHierarchySubtypesParams(
  val item: TypeHierarchyItem,
  override val partialResultToken: ProgressToken? = null,
  override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

/**
 * Represents an item in the type hierarchy.
 *
 * @since 3.17.0
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchyItem">typeHierarchyItem (LSP spec)</a>
 */
@Serializable
data class TypeHierarchyItem(
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
   * {@link TypeHierarchyItem.range `range`}.
   */
  val selectionRange: Range,

  /**
   * A data entry field that is preserved between a type hierarchy prepare and
   * supertypes or subtypes requests. It could also be used to identify the
   * type hierarchy in the server, helping improve the performance on
   * resolving supertypes and subtypes.
   */
  val data: JsonElement? = null,
)

object TypeHierarchyRequests {
  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareTypeHierarchy">textDocument/prepareTypeHierarchy</a>
   */
  val PrepareTypeHierarchyRequestType: RequestType<TypeHierarchyPrepareParams, List<TypeHierarchyItem>?, Unit> = RequestType(
    method = "textDocument/prepareTypeHierarchy",
    paramsSerializer = TypeHierarchyPrepareParams.serializer(),
    resultSerializer = ListSerializer(elementSerializer = TypeHierarchyItem.serializer()).nullable,
    errorSerializer = Unit.serializer(),
  )

  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_supertypes">typeHierarchy/supertypes</a>
   */
  val SupertypesRequestType: RequestType<TypeHierarchySupertypesParams, List<TypeHierarchyItem>?, Unit> = RequestType(
    method = "typeHierarchy/supertypes",
    paramsSerializer = TypeHierarchySupertypesParams.serializer(),
    resultSerializer = ListSerializer(elementSerializer = TypeHierarchyItem.serializer()).nullable,
    errorSerializer = Unit.serializer(),
  )

  /**
   * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_subtypes">typeHierarchy/subtypes</a>
   */
  val SubtypesRequestType: RequestType<TypeHierarchySubtypesParams, List<TypeHierarchyItem>?, Unit> = RequestType(
    method = "typeHierarchy/subtypes",
    paramsSerializer = TypeHierarchySubtypesParams.serializer(),
    resultSerializer = ListSerializer(elementSerializer = TypeHierarchyItem.serializer()).nullable,
    errorSerializer = Unit.serializer(),
  )
}
