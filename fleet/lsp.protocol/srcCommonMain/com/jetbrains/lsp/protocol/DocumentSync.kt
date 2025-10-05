package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class TextDocumentSyncClientCapabilities(
  /**
   * Whether text document synchronization supports dynamic registration.
   */
  val dynamicRegistration: Boolean? = null,

  /**
   * The client supports sending will save notifications.
   */
  val willSave: Boolean? = null,

  /**
   * The client supports sending a will save request and
   * waits for a response providing text edits which will
   * be applied to the document before it is saved.
   */
  val willSaveWaitUntil: Boolean? = null,

  /**
   * The client supports did save notifications.
   */
  val didSave: Boolean? = null,
)

@Serializable(with = TextDocumentSync.Serializer::class)
sealed interface TextDocumentSync {

  class Serializer : JsonContentPolymorphicSerializer<TextDocumentSync>(TextDocumentSync::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TextDocumentSync> {
      return when (element) {
        is JsonObject -> TextDocumentSyncOptions.serializer()
        else -> TextDocumentSyncKind.serializer()
      }
    }
  }
}

@Serializable
data class TextDocumentSyncOptions(
    /**
     * Open and close notifications are sent to the server. If omitted open
     * close notifications should not be sent.
     */
    val openClose: Boolean?,

    /**
     * Change notifications are sent to the server. See
     * TextDocumentSyncKind.None, TextDocumentSyncKind.Full and
     * TextDocumentSyncKind.Incremental. If omitted it defaults to
     * TextDocumentSyncKind.None.
     */
    val change: TextDocumentSyncKind?,

    /**
     * If present will save notifications are sent to the server. If omitted
     * the notification should not be sent.
     */
    val willSave: Boolean?,

    /**
     * If present will save wait until requests are sent to the server. If
     * omitted the request should not be sent.
     */
    val willSaveWaitUntil: Boolean?,

    /**
     * If present save notifications are sent to the server. If omitted the
     * notification should not be sent.
     */
    val save: OrBoolean<SaveOptions>?,
) : TextDocumentSync

class TextDocumentSyncKindSerializer : EnumAsIntSerializer<TextDocumentSyncKind>(
    serialName = "TextDocumentSyncKind",
    serialize = TextDocumentSyncKind::value,
    deserialize = { TextDocumentSyncKind.entries[it] },
)

@Serializable(TextDocumentSyncKindSerializer::class)
enum class TextDocumentSyncKind(val value: Int) : TextDocumentSync {
    None(0),
    Full(1),
    Incremental(2);
}

@Serializable
data class DidOpenTextDocumentParams(
    /**
     * The document that was opened.
     */
    val textDocument: TextDocumentItem,
)

@Serializable
data class DidChangeTextDocumentParams(
    /**
     * The document that did change. The version number points
     * to the version after all provided content changes have
     * been applied.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The actual content changes. The content changes describe single state
     * changes to the document. So if there are two content changes c1 (at
     * array index 0) and c2 (at array index 1) for a document in state S then
     * c1 moves the document from S to S' and c2 from S' to S''. So c1 is
     * computed on the state S and c2 is computed on the state S'.
     *
     * To mirror the content of a document using change events use the following
     * approach:
     * - start with the same initial content
     * - apply the 'textDocument/didChange' notifications in the order you
     *   receive them.
     * - apply the `TextDocumentContentChangeEvent`s in a single notification
     *   in the order you receive them.
     */
    val contentChanges: List<TextDocumentContentChangeEvent>,
)

/**
 * An event describing a change to a text document. If only a text is provided
 * it is considered to be the full content of the document.
 */
@Serializable
data class TextDocumentContentChangeEvent(
    /**
     * The range of the document that changed.
     */
    val range: Range?,
    /**
     * The optional length of the range that got replaced.
     *
     * @deprecated use range instead.
     */
    val rangeLength: Int?,
    /**
     * The new text for the provided range.
     * if range is absent, it's the new text of the document.
     */
    val text: String,
)

/**
 * The parameters sent in a will save text document notification.
 */
@Serializable
data class WillSaveTextDocumentParams(
    /**
     * The document that will be saved.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The 'TextDocumentSaveReason'.
     */
    val reason: TextDocumentSaveReason,
)

class TextDocumentSaveReasonSerializer : EnumAsIntSerializer<TextDocumentSaveReason>(
    serialName = "TextDocumentSaveReason",
    serialize = TextDocumentSaveReason::code,
    deserialize = { TextDocumentSaveReason.entries[it - 1] },
)

/**
 * Represents reasons why a text document is saved.
 */
@Serializable(TextDocumentSaveReasonSerializer::class)
enum class TextDocumentSaveReason(val code: Int) {

    /**
     * Manually triggered, e.g. by the user pressing save, by starting
     * debugging, or by an API call.
     */
    Manual(1),

    /**
     * Automatic after a delay.
     */
    AfterDelay(2),

    /**
     * When the editor lost focus.
     */
    FocusOut(3)
}

@Serializable
data class SaveOptions(
    val includeText: Boolean? = null,
)

@Serializable
data class DidSaveTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
    val text: String? = null,
)

@Serializable
data class DidCloseTextDocumentParams(
    val textDocument: TextDocumentIdentifier,
)

object DocumentSync {
    val DidOpen: NotificationType<DidOpenTextDocumentParams> =
        NotificationType("textDocument/didOpen", DidOpenTextDocumentParams.serializer())

    val DidChange: NotificationType<DidChangeTextDocumentParams> =
        NotificationType("textDocument/didChange", DidChangeTextDocumentParams.serializer())

    val WillSaveWaitUntil: RequestType<WillSaveTextDocumentParams, List<TextEdit>, Unit> =
        RequestType(
            "textDocument/willSaveWaitUntil",
            WillSaveTextDocumentParams.serializer(),
            ListSerializer(TextEdit.serializer()),
            Unit.serializer()
        )

    val DidSave: NotificationType<DidSaveTextDocumentParams> =
        NotificationType("textDocument/didSave", DidSaveTextDocumentParams.serializer())

    val DidClose: NotificationType<DidCloseTextDocumentParams> =
        NotificationType("textDocument/didClose", DidCloseTextDocumentParams.serializer())
}