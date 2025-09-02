package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class DeclarationClientCapabilities(
    /**
     * Whether declaration supports dynamic registration. If this is set to
     * `true` the client supports the new `DeclarationRegistrationOptions`
     * return value for the corresponding server capability as well.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * The client supports additional metadata in the form of declaration links.
     */
    val linkSupport: Boolean? = null,
)

interface DeclarationOptions : WorkDoneProgressOptions

@Serializable
data class DeclarationRegistrationOptions(
    override val workDoneProgress: Boolean?,
    override val documentSelector: DocumentSelector?,
    override val id: String?,
) : DeclarationOptions,
    TextDocumentRegistrationOptions,
    StaticRegistrationOptions

@Serializable
data class DeclarationParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken?,
    override val partialResultToken: ProgressToken?,
) : TextDocumentPositionParams,
    WorkDoneProgressParams,
    PartialResultParams


val DeclarationRequestType: RequestType<DeclarationParams, List<Location>, Unit> =
    RequestType("textDocument/declaration", DeclarationParams.serializer(), ListSerializer(Location.serializer()), Unit.serializer())