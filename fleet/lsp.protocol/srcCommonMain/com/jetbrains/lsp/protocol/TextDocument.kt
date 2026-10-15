package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlin.jvm.JvmInline

@Serializable
data class DocumentHighlightParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams, PartialResultParams

/**
 * A document highlight is a range inside a text document which deserves
 * special attention. Usually a document highlight is visualized by changing
 * the background color of its range.
 *
 */
@Serializable
data class DocumentHighlight(
    /**
     * The range this highlight applies to.
     */
    val range: Range,

    /**
     * The highlight kind, default is DocumentHighlightKind.Text.
     */
    val kind: DocumentHighlightKind? = null,
)

/**
 * A document highlight kind.
 */
@Serializable(with = DocumentHighlightKind.Serializer::class)
enum class DocumentHighlightKind(val value: Int) {
    /**
     * A textual occurrence.
     */
    Text(1),

    /**
     * Read-access of a symbol, like reading a variable.
     */
    Read(2),

    /**
     * Write-access of a symbol, like writing to a variable.
     */
    Write(3),

    ;

    class Serializer : EnumAsIntSerializer<DocumentHighlightKind>(
        serialName = "DocumentHighlightKind",
        serialize = DocumentHighlightKind::value,
        deserialize = { DocumentHighlightKind.entries.getOrNull(it - 1) },
        fallback = DocumentHighlightKind.Text,
    )
}


@Serializable(with = DocumentSymbolResult.Serializer::class)
sealed interface DocumentSymbolResult {
    @Serializable
    @JvmInline
    value class DocumentSymbols(val value: List<DocumentSymbol>) : DocumentSymbolResult

    @Serializable
    @JvmInline
    value class SymbolInformations(val value: List<SymbolInformation>) : DocumentSymbolResult

    /**
     * Streams the list with no tree: each element is read with the members of both variants
     * ([DocumentSymbolOrInformation]), then the first element picks the variant for all, as the tree way did: a
     * `location` member (also `null`) makes symbol informations, anything else (also an empty list) document symbols.
     * Encode writes the variant with its own serializer.
     */
    class Serializer : KSerializer<DocumentSymbolResult> {
        override val descriptor: SerialDescriptor = unionDescriptor("DocumentSymbolResult")

        override fun serialize(encoder: Encoder, value: DocumentSymbolResult) {
            when (value) {
                is DocumentSymbols -> encoder.encodeSerializableValue(DocumentSymbols.serializer(), value)
                is SymbolInformations -> encoder.encodeSerializableValue(SymbolInformations.serializer(), value)
            }
        }

        override fun deserialize(decoder: Decoder): DocumentSymbolResult {
            val elements = decoder.decodeSerializableValue(DecodeOnlyList(DocumentSymbolOrInformation.Reader))
            return when (elements.firstOrNull()?.hasLocation) {
                true -> SymbolInformations(elements.map { it.symbolInformation() })
                else -> DocumentSymbols(elements.map { it.documentSymbol() })
            }
        }
    }
}

/** One element of a [DocumentSymbolResult]: the members of [DocumentSymbol] and [SymbolInformation]. */
private class DocumentSymbolOrInformation(
    val name: String?,
    val detail: String?,
    val kind: SymbolKind?,
    val tags: List<SymbolTag>?,
    val deprecated: Boolean?,
    val range: Range?,
    val selectionRange: Range?,
    val children: List<DocumentSymbol>?,
    val intellij: DocumentSymbolIntellij?,
    /** The object has a `location` member, also `null`. */
    val hasLocation: Boolean,
    val location: Location?,
    val containerName: String?,
) {
    fun documentSymbol(): DocumentSymbol {
        if (name == null || kind == null || range == null || selectionRange == null) {
            throw missingFields(DocumentSymbol.serializer().descriptor, "name" to name, "kind" to kind, "range" to range,
                                "selectionRange" to selectionRange)
        }
        return DocumentSymbol(name = name, detail = detail, kind = kind, tags = tags, deprecated = deprecated, range = range,
                              selectionRange = selectionRange, children = children, intellij = intellij)
    }

    fun symbolInformation(): SymbolInformation {
        if (name == null || kind == null || location == null) {
            throw missingFields(SymbolInformation.serializer().descriptor, "name" to name, "kind" to kind, "location" to location)
        }
        return SymbolInformation(name = name, kind = kind, tags = tags, deprecated = deprecated, location = location,
                                 containerName = containerName)
    }

    /** Every member optional and nullable: a member of one variant is absent from the other, and `null` is not skipped. */
    object Reader : DeserializationStrategy<DocumentSymbolOrInformation> {
        private const val NAME = 0
        private const val DETAIL = 1
        private const val KIND = 2
        private const val TAGS = 3
        private const val DEPRECATED = 4
        private const val RANGE = 5
        private const val SELECTION_RANGE = 6
        private const val CHILDREN = 7
        private const val LOCATION = 8
        private const val CONTAINER_NAME = 9
        private const val INTELLIJ = 10

        private val tagsSerializer = SymbolTagListSerializer().nullable
        private val childrenSerializer = ListSerializer(DocumentSymbol.serializer()).nullable

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.jetbrains.lsp.protocol.DocumentSymbolOrInformation") {
            annotations = ignoreUnknownKeys
            element("name", String.serializer().nullable.descriptor, isOptional = true)
            element("detail", String.serializer().nullable.descriptor, isOptional = true)
            element("kind", SymbolKind.serializer().nullable.descriptor, isOptional = true)
            element("tags", tagsSerializer.descriptor, isOptional = true)
            element("deprecated", Boolean.serializer().nullable.descriptor, isOptional = true)
            element("range", Range.serializer().nullable.descriptor, isOptional = true)
            element("selectionRange", Range.serializer().nullable.descriptor, isOptional = true)
            element("children", childrenSerializer.descriptor, isOptional = true)
            element("location", Location.serializer().nullable.descriptor, isOptional = true)
            element("containerName", String.serializer().nullable.descriptor, isOptional = true)
            element("intellij", DocumentSymbolIntellij.serializer().nullable.descriptor, isOptional = true)
        }

        override fun deserialize(decoder: Decoder): DocumentSymbolOrInformation {
            var name: String? = null
            var detail: String? = null
            var kind: SymbolKind? = null
            var tags: List<SymbolTag>? = null
            var deprecated: Boolean? = null
            var range: Range? = null
            var selectionRange: Range? = null
            var children: List<DocumentSymbol>? = null
            var hasLocation = false
            var location: Location? = null
            var containerName: String? = null
            var intellij: DocumentSymbolIntellij? = null
            decoder.decodeStructure(descriptor) {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        NAME -> name = decodeNullableSerializableElement(descriptor, index, String.serializer())
                        DETAIL -> detail = decodeNullableSerializableElement(descriptor, index, String.serializer())
                        KIND -> kind = decodeNullableSerializableElement(descriptor, index, SymbolKind.serializer())
                        TAGS -> tags = decodeSerializableElement(descriptor, index, tagsSerializer)
                        DEPRECATED -> deprecated = decodeNullableSerializableElement(descriptor, index, Boolean.serializer())
                        RANGE -> range = decodeNullableSerializableElement(descriptor, index, Range.serializer())
                        SELECTION_RANGE -> selectionRange = decodeNullableSerializableElement(descriptor, index, Range.serializer())
                        CHILDREN -> children = decodeSerializableElement(descriptor, index, childrenSerializer)
                        LOCATION -> {
                            hasLocation = true
                            location = decodeNullableSerializableElement(descriptor, index, Location.serializer())
                        }
                        CONTAINER_NAME -> containerName = decodeNullableSerializableElement(descriptor, index, String.serializer())
                        INTELLIJ -> intellij = decodeNullableSerializableElement(descriptor, index, DocumentSymbolIntellij.serializer())
                        CompositeDecoder.DECODE_DONE -> break
                        else -> throw SerializationException("Unexpected index $index")
                    }
                }
            }
            return DocumentSymbolOrInformation(name, detail, kind, tags, deprecated, range, selectionRange, children, intellij,
                                               hasLocation, location, containerName)
        }
    }
}

object TextDocuments {
    val DocumentSymbol: RequestType<DocumentSymbolParams, DocumentSymbolResult?, Unit> =
        RequestType(
          "textDocument/documentSymbol",
          DocumentSymbolParams.serializer(), DocumentSymbolResult.serializer().nullable,
          Unit.serializer())

    val DocumentHighlightRequestType: RequestType<DocumentHighlightParams, List<DocumentHighlight>?, Unit> =
        RequestType(
          "textDocument/documentHighlight",
          DocumentHighlightParams.serializer(), ListSerializer(DocumentHighlight.serializer()).nullable,
          Unit.serializer())
}