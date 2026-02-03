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

// TODO: In reality, this method, similar to TypeDefinition, returns the type `Location | Location[] | LocationLink[] | null`.
//       Since the testing machinery of the language-server currently doesn't support performing the partial result tests
//       with requests that return something that isn't a list, the definition here is kept as is.
//       The Air (Fleet) LSP client has its own RequestType of this method as a temporary measure, but the aim is to merge them together.
val DefinitionRequestType: RequestType<DefinitionParams, List<Location>/*TODO  LocationLink should be here as more flexible*/, Unit> =
    RequestType("textDocument/definition", DefinitionParams.serializer(), ListSerializer(Location.serializer()), Unit.serializer())

val TypeDefinitionRequestType: RequestType<TypeDefinitionParams, Locations?, Unit> =
    RequestType("textDocument/typeDefinition", TypeDefinitionParams.serializer(), Locations.serializer().nullable, Unit.serializer())
