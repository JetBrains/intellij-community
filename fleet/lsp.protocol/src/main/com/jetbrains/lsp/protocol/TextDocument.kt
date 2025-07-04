package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    val kind: DocumentHighlightKind?,
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
        deserialize = { DocumentHighlightKind.entries[it - 1] },
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

    class Serializer : JsonContentPolymorphicSerializer<DocumentSymbolResult>(DocumentSymbolResult::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<DocumentSymbolResult> {
            return when (element) {
                is JsonArray -> {
                    if (element.isNotEmpty() && element[0].let { it is JsonObject && it.containsKey("location")}) {
                        SymbolInformations.serializer()
                    }
                    else {
                        DocumentSymbols.serializer()
                    }
                }
                else -> throw SerializationException("Expected an array of DocumentSymbol or SymbolInformation.")
            }
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