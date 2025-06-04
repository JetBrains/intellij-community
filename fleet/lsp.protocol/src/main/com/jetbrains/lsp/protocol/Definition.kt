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
    val linkSupport: Boolean? = null
)

interface DefinitionOptions : WorkDoneProgressOptions

@Serializable
data class DefinitionRegistrationOptions(
    override val documentSelector: DocumentSelector? = null,
    override val workDoneProgress: Boolean? = null
) : TextDocumentRegistrationOptions, DefinitionOptions


@Serializable
data class DefinitionParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken?,
    override val partialResultToken: ProgressToken?
) : WorkDoneProgressParams, PartialResultParams, TextDocumentPositionParams

val DefinitionRequestType: RequestType<DefinitionParams, List<Location>/*TODO  LocationLink should be here as more flexible*/, Unit> =
    RequestType("textDocument/definition", DefinitionParams.serializer(), ListSerializer(Location.serializer()), Unit.serializer())