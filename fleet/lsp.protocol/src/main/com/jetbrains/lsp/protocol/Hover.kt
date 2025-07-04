package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class HoverParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,
    override val workDoneToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams


/**
 * The result of a hover request.
 */
@Serializable
data class Hover(
    /**
     * The hover's content
     */
    val contents: Content,

    /**
     * An optional range is a range inside a text document
     * that is used to visualize a hover, e.g. by changing the background color.
     */
    val range: Range?,
) {
  @Serializable(with = Content.Serializer::class)
  sealed interface Content {
    @Serializable
    @JvmInline
    value class Markup(val markupContent: MarkupContent) : Content

    @Serializable
    @JvmInline
    value class Marked(val markedContent: MaybeMarkedContent) : Content

    @Serializable
    @JvmInline
    value class MarkedList(val markedContent: List<MaybeMarkedContent>) : Content

    class Serializer : JsonContentPolymorphicSerializer<Content>(Content::class) {
      override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Content> {
        return when {
          element is JsonArray -> MarkedList.serializer()
          element is JsonObject && element.containsKey("kind") -> Markup.serializer()
          else -> Marked.serializer()
        }
      }
    }
  }

  @Serializable(with = MaybeMarkedContent.Serializer::class)
  sealed interface MaybeMarkedContent {
    @Serializable
    @JvmInline
    value class Bare(val value: String) : MaybeMarkedContent

    @Serializable
    data class Marked(val language: String, val value: String) : MaybeMarkedContent

    class Serializer : JsonContentPolymorphicSerializer<MaybeMarkedContent>(MaybeMarkedContent::class) {
      override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MaybeMarkedContent> {
        return if (element is JsonPrimitive && element.isString) Bare.serializer() else Marked.serializer()
      }
    }
  }
}

/**
 * The hover request is sent from the client to the server to request hover information at a given text document position.
 */
val HoverRequestType: RequestType<HoverParams, Hover?, Unit> =
    RequestType("textDocument/hover", HoverParams.serializer(), Hover.serializer().nullable, Unit.serializer())