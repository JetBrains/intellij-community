package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline

/**
 * Client capabilities specific to inline completions.
 *
 * @since 3.18.0
 */
@Serializable
data class InlineCompletionClientCapabilities(
    /**
     * Whether implementation supports dynamic registration for inline completion providers.
     */
    val dynamicRegistration: Boolean? = null,
)

/**
 * Inline completion options used during static registration.
 *
 * @since 3.18.0
 */
interface InlineCompletionOptions : WorkDoneProgressOptions

/**
 * Inline completion options used during static or dynamic registration.
 *
 * @since 3.18.0
 */
@Serializable
data class InlineCompletionRegistrationOptions(
    override val workDoneProgress: Boolean? = null,
    override val documentSelector: DocumentSelector? = null,
    override val id: String? = null,
) : InlineCompletionOptions, TextDocumentRegistrationOptions, StaticRegistrationOptions

/**
 * A parameter literal used in inline completion requests.
 *
 * @since 3.18.0
 */
@Serializable
data class InlineCompletionParams(
    override val textDocument: TextDocumentIdentifier,
    override val position: Position,

    /**
     * Additional information about the context in which inline completions were requested.
     */
    val context: InlineCompletionContext,

    override val workDoneToken: ProgressToken? = null,
) : TextDocumentPositionParams, WorkDoneProgressParams

/**
 * Provides information about the context in which an inline completion was requested.
 *
 * @since 3.18.0
 */
@Serializable
data class InlineCompletionContext(
    /**
     * Describes how the inline completion was triggered.
     */
    val triggerKind: InlineCompletionTriggerKind,

    /**
     * Provides information about the currently selected item in the autocomplete widget if it is visible.
     *
     * If set, provided inline completions must extend the text of the selected item
     * and use the same range, otherwise they are not shown as preview.
     */
    val selectedCompletionInfo: SelectedCompletionInfo? = null,
)

class InlineCompletionTriggerKindSerializer : EnumAsIntSerializer<InlineCompletionTriggerKind>(
    serialName = "InlineCompletionTriggerKind",
    serialize = InlineCompletionTriggerKind::value,
    deserialize = { InlineCompletionTriggerKind.entries.getOrNull(it - 1) },
    fallback = InlineCompletionTriggerKind.Invoked,
)

/**
 * Describes how an inline completion provider was triggered.
 *
 * @since 3.18.0
 */
@Serializable(InlineCompletionTriggerKindSerializer::class)
enum class InlineCompletionTriggerKind(val value: Int) {
    /**
     * Completion was triggered explicitly by a user gesture.
     * Return multiple completion items to enable cycling through them.
     */
    Invoked(1),

    /**
     * Completion was triggered automatically while editing.
     * It is sufficient to return a single completion item in this case.
     */
    Automatic(2),
}

/**
 * Describes the currently selected completion item.
 *
 * @since 3.18.0
 */
@Serializable
data class SelectedCompletionInfo(
    /**
     * The range that will be replaced if this completion item is accepted.
     */
    val range: Range,

    /**
     * The text the range will be replaced with if this completion is accepted.
     */
    val text: String,
)

/**
 * A string value used as a snippet is a template which allows to insert text
 * and to control the editor cursor when insertion happens.
 *
 * @since 3.18.0
 */
@Serializable
data class StringValue(
    /**
     * The snippet string.
     */
    val value: String,

    /**
     * The kind of string value; only `snippet` is defined.
     */
    val kind: String = SNIPPET,
) {
    companion object {
        const val SNIPPET: String = "snippet"
    }
}

/**
 * An inline completion item represents a text snippet that is proposed inline to complete text that is being typed.
 *
 * @since 3.18.0
 */
@Serializable
data class InlineCompletionItem(
    /**
     * The text to replace the range with. Must be set.
     * Is used both for the preview and the accept operation.
     */
    val insertText: OrString<StringValue>,

    /**
     * A text that is used to decide if this inline completion should be shown.
     * When `falsy` the [insertText] is used.
     */
    val filterText: String? = null,

    /**
     * The range to replace. Must begin and end on the same line.
     */
    val range: Range? = null,

    /**
     * An optional [Command] that is executed *after* inserting this completion.
     */
    val command: Command? = null,
)

/**
 * Represents a collection of [InlineCompletionItem] to be presented in the editor.
 *
 * @since 3.18.0
 */
@Serializable
data class InlineCompletionList(
    /**
     * The inline completion items.
     */
    val items: List<InlineCompletionItem>,
)

/**
 * The result of `textDocument/inlineCompletion`: `InlineCompletionItem[] | InlineCompletionList`.
 *
 * @since 3.18.0
 */
@Serializable(with = InlineCompletionResult.Serializer::class)
sealed interface InlineCompletionResult {
    val items: List<InlineCompletionItem>

    @Serializable
    @JvmInline
    value class Items(override val items: List<InlineCompletionItem>) : InlineCompletionResult

    @Serializable
    @JvmInline
    value class ItemList(val list: InlineCompletionList) : InlineCompletionResult {
        override val items: List<InlineCompletionItem> get() = list.items
    }

    class Serializer : JsonContentPolymorphicSerializer<InlineCompletionResult>(InlineCompletionResult::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<InlineCompletionResult> {
            return when (element) {
                is JsonArray -> Items.serializer()
                else -> ItemList.serializer()
            }
        }
    }
}

/**
 * @since 3.18.0
 */
val InlineCompletionRequestType: RequestType<InlineCompletionParams, InlineCompletionResult?, Unit> =
    RequestType(
        "textDocument/inlineCompletion",
        InlineCompletionParams.serializer(),
        InlineCompletionResult.serializer().nullable,
        Unit.serializer(),
    )
