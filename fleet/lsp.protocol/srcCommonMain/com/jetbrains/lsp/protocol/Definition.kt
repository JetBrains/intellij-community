package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/**
 * Client capabilities for the definition feature.
 */
@Serializable
data class DefinitionClientCapabilities(
  /**
   * Whether definition supports dynamic registration.
   */
  val dynamicRegistration: Boolean? = null,

  /**
   * The client supports additional metadata in the form of definition links.
   *
   * @since 3.14.0
   */
  val linkSupport: Boolean? = null,
)

interface DefinitionOptions : WorkDoneProgressOptions

@Serializable
data class DefinitionRegistrationOptions(
    override val documentSelector: DocumentSelector? = null,
    override val workDoneProgress: Boolean? = null,
) : TextDocumentRegistrationOptions, DefinitionOptions


@Serializable
data class DefinitionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

@Serializable
data class TypeDefinitionParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams, PartialResultParams

/**
 * The spec result is `Location | Location[] | LocationLink[] | null`. This type covers the two list forms, so partial
 * results still work: the testing machinery of the language server supports partial results only for list results.
 * A server sends [LocationLink]s only to a client with [DefinitionClientCapabilities.linkSupport], and never mixes the two
 * kinds in one answer.
 *
 * TODO: the single `Location` and `null` forms. The Air (Fleet) LSP client has its own RequestType of this method as a
 *       temporary measure, but the aim is to merge them together.
 */
val DefinitionRequestType: RequestType<DefinitionParams, List<LocationOrLink>, Unit> =
    RequestType("textDocument/definition", DefinitionParams.serializer(), ListSerializer(LocationOrLink.serializer()), Unit.serializer())

val TypeDefinitionRequestType: RequestType<TypeDefinitionParams, Locations?, Unit> =
    RequestType("textDocument/typeDefinition", TypeDefinitionParams.serializer(), Locations.serializer().nullable, Unit.serializer())
