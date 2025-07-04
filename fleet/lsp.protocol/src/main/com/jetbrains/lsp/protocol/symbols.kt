package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class WorkspaceSymbolOptions(
    /**
     * The server provides support to resolve additional
     * information for a workspace symbol.
     *
     * @since 3.17.0
     */
    val resolveProvider: Boolean?,
    override val workDoneProgress: Boolean?,
) : WorkDoneProgressOptions

/**
 * The parameters of a Workspace Symbol Request.
 */
@Serializable
data class WorkspaceSymbolParams(
    /**
     * A query string to filter symbols by. Clients may send an empty
     * string here to request all symbols.
     */
    val query: String,
    override val partialResultToken: ProgressToken? = null,
    override val workDoneToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams


@Serializable
data class DocumentSymbolParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,
    override val partialResultToken: ProgressToken? = null,
    override val workDoneToken: ProgressToken? = null,
): WorkDoneProgressParams, PartialResultParams

/**
 * A special workspace symbol that supports locations without a range
 *
 * @since 3.17.0
 */
@Serializable
data class WorkspaceSymbol(
    /**
     * The name of this symbol.
     */
    val name: String,

    /**
     * The kind of this symbol.
     */
    val kind: SymbolKind,

    /**
     * Tags for this completion item.
     */
    val tags: List<SymbolTag>?,

    /**
     * The name of the symbol containing this symbol. This information is for
     * user interface purposes (e.g. to render a qualifier in the user interface
     * if necessary). It can't be used to re-infer a hierarchy for the document
     * symbols.
     */
    val containerName: String?,

    /**
     * The location of this symbol. Whether a server is allowed to
     * return a location without a range depends on the client
     * capability `workspace.symbol.resolveSupport`.
     *
     * See also `SymbolInformation.location`.
     */
    val location: SymbolLocation,

    /**
     * A data entry field that is preserved on a workspace symbol between a
     * workspace symbol request and a workspace symbol resolve request.
     */
    val data: JsonElement?,
) {
    @Serializable(with = SymbolLocation.Serializer::class)
    sealed interface SymbolLocation {
        @Serializable
        @JvmInline
        value class Full(val value: Location) : SymbolLocation

        @Serializable
        data class Partial(val uri: DocumentUri) : SymbolLocation

        class Serializer : JsonContentPolymorphicSerializer<SymbolLocation>(SymbolLocation::class) {
            override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SymbolLocation> {
                return when {
                    element is JsonObject && element.containsKey("range") -> Full.serializer()
                    else -> Partial.serializer()
                }
            }
        }
    }
}

/**
 * Represents programming constructs like variables, classes, interfaces etc.
 * that appear in a document. Document symbols can be hierarchical and they
 * have two ranges: one that encloses its definition and one that points to its
 * most interesting range, e.g. the range of an identifier.
 */
@Serializable
data class  DocumentSymbol(

    /**
     * The name of this symbol. Will be displayed in the user interface and
     * therefore must not be an empty string or a string only consisting of
     * white spaces.
     */
    val name: String,

    /**
     * More detail for this symbol, e.g the signature of a function.
     */
    val detail: String?,

    /**
     * The kind of this symbol.
     */
    val kind: SymbolKind,

    /**
     * Tags for this document symbol.
     *
     * @since 3.16.0
     */
    val tags: List<SymbolTag>?,

    /**
     * Indicates if this symbol is deprecated.
     *
     * @deprecated Use tags instead
     */
    val deprecated: Boolean?,

    /**
     * The range enclosing this symbol not including leading/trailing whitespace
     * but everything else like comments. This information is typically used to
     * determine if the clients cursor is inside the symbol to reveal in the
     * symbol in the UI.
     */
    val range: Range,

    /**
     * The range that should be selected and revealed when this symbol is being
     * picked, e.g. the name of a function. Must be contained by the `range`.
     */
    val selectionRange: Range,

    /**
     * Children of this symbol, e.g. properties of a class.
     */
    val children: List<DocumentSymbol>?
)

/**
 * A symbol kind.
 */
@Serializable(SymbolKind.Serializer::class)
enum class SymbolKind(val value: Int) {
    File(1),
    Module(2),
    Namespace(3),
    Package(4),
    Class(5),
    Method(6),
    Property(7),
    Field(8),
    Constructor(9),
    Enum(10),
    Interface(11),
    Function(12),
    Variable(13),
    Constant(14),
    String(15),
    Number(16),
    Boolean(17),
    Array(18),
    Object(19),
    Key(20),
    Null(21),
    EnumMember(22),
    Struct(23),
    Event(24),
    Operator(25),
    TypeParameter(26),

    ;

    class Serializer : EnumAsIntSerializer<SymbolKind>(
        serialName = SymbolKind::class.simpleName!!,
        serialize = SymbolKind::value,
        deserialize = { SymbolKind.entries[it - 1] },
    )
}

/**
 * Symbol tags are extra annotations that tweak the rendering of a symbol.
 *
 * @since 3.16
 */
@Serializable(SymbolTag.Serializer::class)
enum class SymbolTag(val value: Int) {
    /**
     * Render a symbol as obsolete, usually using a strike-out.
     */
    Deprecated(1),

    ;

    class Serializer : EnumAsIntSerializer<SymbolTag>(
        serialName = SymbolTag::class.simpleName!!,
        serialize = SymbolTag::value,
        deserialize = { SymbolTag.entries[it - 1] },
    )
}


object WorkspaceSymbolRequests {
    val WorkspaceSymbolRequest: RequestType<WorkspaceSymbolParams, List<WorkspaceSymbol>, Unit> =
        RequestType(
            "workspace/symbol",
            WorkspaceSymbolParams.serializer(),
            ListSerializer(WorkspaceSymbol.serializer()),
            Unit.serializer(),
        )

    val WorkspaceSymbolResolveRequest: RequestType<WorkspaceSymbolParams, WorkspaceSymbol, Unit> =
        RequestType(
            "workspace/symbol/resolve",
            WorkspaceSymbolParams.serializer(),
            WorkspaceSymbol.serializer(),
            Unit.serializer(),
        )
}

val DocumentSymbolRequest: RequestType<DocumentSymbolParams, List<DocumentSymbol>, Unit> = RequestType(
    "textDocument/documentSymbol",
    DocumentSymbolParams.serializer(),
    ListSerializer(DocumentSymbol.serializer()),
    Unit.serializer(),
)