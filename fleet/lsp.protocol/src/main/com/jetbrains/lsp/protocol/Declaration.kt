package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

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

@Serializable
@JvmInline
value class Locations(val location: JsonElement) { // Location | Location[]
    constructor(loc: Location) : this(LSP.json.encodeToJsonElement(Location.serializer(), loc))
    constructor(locs: List<Location>) : this(LSP.json.encodeToJsonElement(ListSerializer(Location.serializer()), locs))

    val locations: List<Location>
        get() = when (val loc = location) {
            is JsonArray -> loc.map { LSP.json.decodeFromJsonElement(Location.serializer(), it) }
            is JsonObject -> listOf(LSP.json.decodeFromJsonElement(Location.serializer(), loc))
            else -> error("Unexpected value: $loc")
        }

    val links: List<LocationLink>
        get() = location.jsonArray.map { LSP.json.decodeFromJsonElement(LocationLink.serializer(), it) }

    val areLinks: Boolean
        get() = location is JsonArray && location.isNotEmpty() && isLink(location[0])

    private fun isLink(element: JsonElement) = element is JsonObject && element.containsKey("targetUri")

    companion object {
        fun fromLinks(links: List<LocationLink>): Locations {
            return Locations(LSP.json.encodeToJsonElement(ListSerializer(LocationLink.serializer()), links))
        }
    }
}

val DeclarationRequestType: RequestType<DeclarationParams, Locations?, Unit> =
    RequestType("textDocument/declaration", DeclarationParams.serializer(), Locations.serializer().nullable, Unit.serializer())
