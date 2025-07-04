package com.jetbrains.lsp.protocol

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CompletionClientCapabilities(
    /**
     * Whether completion supports dynamic registration.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * The client supports the following `CompletionItem` specific
     * capabilities.
     */
    val completionItem: CompletionItemCapabilities? = null,

    val completionItemKind: CompletionItemKindCapabilities? = null,

    /**
     * The client supports to send additional context information for a
     * `textDocument/completion` request.
     */
    val contextSupport: Boolean? = null,

    /**
     * The client's default when the completion item doesn't provide a
     * `insertTextMode` property.
     *
     * @since 3.17.0
     */
    val insertTextMode: InsertTextMode? = null,

    /**
     * The client supports the following `CompletionList` specific
     * capabilities.
     *
     * @since 3.17.0
     */
    val completionList: CompletionListCapabilities? = null,
) {
    @Serializable
    data class CompletionItemCapabilities(
        /**
         * Client supports snippets as insert text.
         *
         * A snippet can define tab stops and placeholders with `$1`, `$2`
         * and `${3:foo}`. `$0` defines the final tab stop, it defaults to
         * the end of the snippet. Placeholders with equal identifiers are
         * linked, that is typing in one will update others too.
         */
        val snippetSupport: Boolean? = null,

        /**
         * Client supports commit characters on a completion item.
         */
        val commitCharactersSupport: Boolean? = null,

        /**
         * Client supports the follow content formats for the documentation
         * property. The order describes the preferred format of the client.
         */
        val documentationFormat: List<MarkupKind>? = null,

        /**
         * Client supports the deprecated property on a completion item.
         */
        val deprecatedSupport: Boolean? = null,

        /**
         * Client supports the preselect property on a completion item.
         */
        val preselectSupport: Boolean? = null,

        /**
         * Client supports the tag property on a completion item. Clients
         * supporting tags have to handle unknown tags gracefully. Clients
         * especially need to preserve unknown tags when sending a completion
         * item back to the server in a resolve call.
         *
         * @since 3.15.0
         */
        val tagSupport: TagSupportCapabilities? = null,

        /**
         * Client supports insert replace edit to control different behavior if
         * a completion item is inserted in the text or should replace text.
         *
         * @since 3.16.0
         */
        val insertReplaceSupport: Boolean? = null,

        /**
         * Indicates which properties a client can resolve lazily on a
         * completion item. Before version 3.16.0 only the predefined properties
         * `documentation` and `detail` could be resolved lazily.
         *
         * @since 3.16.0
         */
        val resolveSupport: ResolveSupportCapabilities? = null,

        /**
         * The client supports the `insertTextMode` property on
         * a completion item to override the whitespace handling mode
         * as defined by the client (see `insertTextMode`).
         *
         * @since 3.16.0
         */
        val insertTextModeSupport: InsertTextModeSupportCapabilities? = null,

        /**
         * The client has support for completion item label
         * details (see also `CompletionItemLabelDetails`).
         *
         * @since 3.17.0
         */
        val labelDetailsSupport: Boolean? = null,
    ) {
        @Serializable
        data class TagSupportCapabilities(
            /**
             * The tags supported by the client.
             */
            val valueSet: List<CompletionItemTag>,
        )

        @Serializable
        data class ResolveSupportCapabilities(
            /**
             * The properties that a client can resolve lazily.
             */
            val properties: List<String>,
        )

        @Serializable
        data class InsertTextModeSupportCapabilities(
            val valueSet: List<InsertTextMode>,
        )
    }

    @Serializable
    data class CompletionItemKindCapabilities(
        /**
         * The completion item kind values the client supports. When this
         * property exists the client also guarantees that it will
         * handle values outside its set gracefully and falls back
         * to a default value when unknown.
         *
         * If this property is not present the client only supports
         * the completion items kinds from `Text` to `Reference` as defined in
         * the initial version of the protocol.
         */
        val valueSet: List<CompletionItemKind>? = null,
    )

    @Serializable
    data class CompletionListCapabilities(
        /**
         * The client supports the following itemDefaults on
         * a completion list.
         *
         * The value lists the supported property names of the
         * `CompletionList.itemDefaults` object. If omitted
         * no properties are supported.
         *
         * @since 3.17.0
         */
        val itemDefaults: List<String>? = null,
    )
}

@Serializable
sealed interface CompletionOptions {
    /**
     * The additional characters, beyond the defaults provided by the client (typically
     * [a-zA-Z]), that should automatically trigger a completion request. For example
     * `.` in JavaScript represents the beginning of an object property or method and is
     * thus a good candidate for triggering a completion request.
     *
     * Most tools trigger a completion request automatically without explicitly
     * requesting it using a keyboard shortcut (e.g. Ctrl+Space). Typically they
     * do so when the user starts to type an identifier. For example if the user
     * types `c` in a JavaScript file code complete will automatically pop up
     * present `console` besides others as a completion item. Characters that
     * make up identifiers don't need to be listed here.
     */
    val triggerCharacters: List<String>?

    /**
     * The list of all possible characters that commit a completion. This field
     * can be used if clients don't support individual commit characters per
     * completion item. See client capability
     * `completion.completionItem.commitCharactersSupport`.
     *
     * If a server provides both `allCommitCharacters` and commit characters on
     * an individual completion item the ones on the completion item win.
     *
     * @since 3.2.0
     */
    val allCommitCharacters: List<String>?

    /**
     * The server provides support to resolve additional
     * information for a completion item.
     */
    val resolveProvider: Boolean?

    /**
     * The server supports the following `CompletionItem` specific
     * capabilities.
     *
     * @since 3.17.0
     */
    val completionItem: CompletionItemCapabilities?

    @Serializable
    data class CompletionItemCapabilities(
        /**
         * The server has support for completion item label
         * details (see also `CompletionItemLabelDetails`) when receiving
         * a completion item in a resolve call.
         *
         * @since 3.17.0
         */
        val labelDetailsSupport: Boolean? = null,
    )
}

@Serializable
data class CompletionRegistrationOptionsImpl(
    override val triggerCharacters: List<String>?,
    override val allCommitCharacters: List<String>?,
    override val resolveProvider: Boolean?,
    override val completionItem: CompletionOptions.CompletionItemCapabilities?,
) : CompletionOptions

@Serializable
data class CompletionRegistrationOptions(
    override val documentSelector: DocumentSelector?,
    override val triggerCharacters: List<String>?,
    override val allCommitCharacters: List<String>?,
    override val resolveProvider: Boolean?,
    override val completionItem: CompletionOptions.CompletionItemCapabilities?,
) : TextDocumentRegistrationOptions, CompletionOptions

@Serializable
data class CompletionParams(
    val textDocument: TextDocumentIdentifier,
    val position: Position,
    val workDoneToken: ProgressToken? = null,
    val partialResultToken: ProgressToken? = null,
    /**
     * The completion context. This is only available if the client specifies
     * to send this using the client capability
     * `completion.contextSupport === true`
     */
    val context: CompletionContext? = null,
)

class CompletionTriggerKindSerializer : EnumAsIntSerializer<CompletionTriggerKind>(
    serialName = "CompletionTriggerKind",
    serialize = CompletionTriggerKind::value,
    deserialize = { CompletionTriggerKind.values().get(it - 1) },
)

@Serializable(CompletionTriggerKindSerializer::class)
enum class CompletionTriggerKind(val value: Int) {
    Invoked(1),
    TriggerCharacter(2),
    TriggerForIncompleteCompletions(3);
}

@Serializable
data class CompletionContext(
    /**
     * How the completion was triggered.
     */
    val triggerKind: CompletionTriggerKind,

    /**
     * The trigger character (a single character) that has triggered code
     * completion. Is null if `triggerKind` is not `TriggerCharacter`.
     */
    val triggerCharacter: String? = null,
)

@Serializable
data class CompletionList(
    /**
     * This list is not complete. Further typing should result in recomputing
     * this list.
     *
     * Recomputed lists have all their items replaced (not appended) in the
     * incomplete completion sessions.
     */
    val isIncomplete: Boolean,

    /**
     * In many cases the items of an actual completion result share the same
     * value for properties like `commitCharacters` or the range of a text
     * edit. A completion list can therefore define item defaults which will
     * be used if a completion item itself doesn't specify the value.
     *
     * If a completion list specifies a default value and a completion item
     * also specifies a corresponding value the one from the item is used.
     *
     * Servers are only allowed to return default values if the client
     * signals support for this via the `completionList.itemDefaults`
     * capability.
     *
     * @since 3.17.0
     */
    val itemDefaults: ItemDefaults? = null,

    /**
     * The completion items.
     */
    val items: List<CompletionItem>,
) {

    companion object {
        val EMPTY_COMPLETE = CompletionList(
            isIncomplete = false,
            itemDefaults = null,
            items = emptyList(),
        )
    }

    @Serializable
    data class ItemDefaults(
        /**
         * A default commit character set.
         *
         * @since 3.17.0
         */
        val commitCharacters: List<String>? = null,

        /**
         * A default edit range.
         *
         * @since 3.17.0
         */
        val editRange: EditRange? = null,

        /**
         * A default insert text format.
         *
         * @since 3.17.0
         */
        val insertTextFormat: InsertTextFormat? = null,

        /**
         * A default insert text mode.
         *
         * @since 3.17.0
         */
        val insertTextMode: InsertTextMode? = null,

        /**
         * A default data value.
         *
         * @since 3.17.0
         */
        val data: JsonElement? = null,
    )

    @Serializable
    data class EditRange(
        val insert: Range,
        val replace: Range,
    )
}

/**
 * A special text edit to provide an insert and a replace operation.
 *
 * @since 3.16.0
 */
@Serializable
data class InsertReplaceEdit(
    /**
     * The string to be inserted.
     */
    val newText: String,
    /**
     * The range if the insert is requested
     */
    val insert: Range,
    /**
     * The range if the replace is requested.
     */
    val replace: Range,
)

class InsertTextFormatSerializer : EnumAsIntSerializer<InsertTextFormat>(
    serialName = "CompletionTriggerKind",
    serialize = InsertTextFormat::value,
    deserialize = { InsertTextFormat.values().get(it - 1) },
)

@Serializable(InsertTextFormatSerializer::class)
enum class InsertTextFormat(val value: Int) {
    PlainText(1),
    Snippet(2);
}

class CompletionItemTagSerializer : EnumAsIntSerializer<CompletionItemTag>(
    serialName = "CompletionTriggerKind",
    serialize = CompletionItemTag::value,
    deserialize = { CompletionItemTag.values().get(it - 1) },
)

/**
 * Completion item tags are extra annotations that tweak the rendering of a
 * completion item.
 *
 * @since 3.15.0
 */
@Serializable(CompletionItemTagSerializer::class)
enum class CompletionItemTag(val value: Int) {
    /**
     * Render a completion as obsolete, usually using a strike-out.
     */
    Deprecated(1);
}

class InsertTextModeSerializer : EnumAsIntSerializer<InsertTextMode>(
    serialName = "CompletionTriggerKind",
    serialize = InsertTextMode::value,
    deserialize = { InsertTextMode.values().get(it - 1) },
)

/**
 * How whitespace and indentation is handled during completion
 * item insertion.
 *
 * @since 3.16.0
 */
@Serializable(InsertTextModeSerializer::class)
enum class InsertTextMode(val value: Int) {
    /**
     * The insertion or replace strings is taken as it is. If the
     * value is multi line the lines below the cursor will be
     * inserted using the indentation defined in the string value.
     * The client will not apply any kind of adjustments to the
     * string.
     */
    AsIs(1),

    /**
     * The editor adjusts leading whitespace of new lines so that
     * they match the indentation up to the cursor of the line for
     * which the item is accepted.
     *
     * Consider a line like this: <2tabs><cursor><3tabs>foo. Accepting a
     * multi line completion item is indented using 2 tabs and all
     * following lines inserted will be indented using 2 tabs as well.
     */
    AdjustIndentation(2);
}

@Serializable
data class CompletionItemLabelDetails(
    /**
     * An optional string which is rendered less prominently directly after
     * [CompletionItem.label], without any spacing. Should be
     * used for function signatures or type annotations.
     *
     * @since 3.17.0
     */
    val detail: String? = null,

    /**
     * An optional string which is rendered less prominently after
     * [CompletionItemLabelDetails.detail]. Should be used for fully qualified
     * names or file paths.
     *
     * @since 3.17.0
     */
    val description: String? = null,
)

@Serializable
data class CompletionItem(
    /**
     * The label of this completion item.
     *
     * The label property is also by default the text that
     * is inserted when selecting this completion.
     *
     * If label details are provided the label itself should
     * be an unqualified name of the completion item.
     */
    val label: String,

    /**
     * Additional details for the label
     *
     * @since 3.17.0
     */
    val labelDetails: CompletionItemLabelDetails? = null,

    /**
     * The kind of this completion item. Based on the kind
     * an icon is chosen by the editor. The standardized set
     * of available values is defined in `CompletionItemKind`.
     */
    val kind: CompletionItemKind? = null,

    /**
     * Tags for this completion item.
     *
     * @since 3.15.0
     */
    val tags: List<CompletionItemTag>? = null,

    /**
     * A human-readable string with additional information
     * about this item, like type or symbol information.
     */
    val detail: String? = null,

    /**
     * A human-readable string that represents a doc-comment.
     */
    val documentation: StringOrMarkupContent? = null,

    /**
     * Indicates if this item is deprecated.
     *
     * @deprecated Use `tags` instead if supported.
     */
    @Deprecated("Use `tags` instead if supported.") val deprecated: Boolean? = null,

    /**
     * Select this item when showing.
     *
     * *Note* that only one completion item can be selected and that the
     * tool / client decides which item that is. The rule is that the *first*
     * item of those that match best is selected.
     */
    val preselect: Boolean? = null,

    /**
     * A string that should be used when comparing this item
     * with other items. When omitted the label is used
     * as the sort text for this item.
     */
    val sortText: String? = null,

    /**
     * A string that should be used when filtering a set of
     * completion items. When omitted the label is used as the
     * filter text for this item.
     */
    val filterText: String? = null,

    /**
     * A string that should be inserted into a document when selecting
     * this completion. When omitted the label is used as the insert text
     * for this item.
     */
    val insertText: String? = null,

    /**
     * The format of the insert text.
     */
    val insertTextFormat: InsertTextFormat? = null,

    /**
     * How whitespace and indentation is handled during completion
     * item insertion.
     *
     * @since 3.16.0
     * @since 3.17.0 - support for `textDocument.completion.insertTextMode`
     */
    val insertTextMode: InsertTextMode? = null,

    /**
     * An edit which is applied to a document when selecting this completion.
     */
    val textEdit: TextEditOrInsertReplaceEdit? = null,

    /**
     * The edit text used if the completion item is part of a CompletionList.
     *
     * @since 3.17.0
     */
    val textEditText: String? = null,

    /**
     * An optional array of additional text edits that are applied when
     * selecting this completion.
     */
    val additionalTextEdits: List<TextEdit>? = null,

    /**
     * An optional set of characters that when pressed while this completion is
     * active will accept it first and then type that character.
     */
    val commitCharacters: List<String>? = null,

    /**
     * An optional command that is executed *after* inserting this completion.
     */
    val command: Command? = null,

    /**
     * A data entry field that is preserved on a completion item between
     * a completion and a completion resolve request.
     */
    val data: JsonElement? = null,
)

// todo: custom serializer needed
@Serializable
@JvmInline
value class TextEditOrInsertReplaceEdit private constructor(val edit: JsonElement) {
    constructor(textEdit: TextEdit) : this(LSP.json.encodeToJsonElement(TextEdit.serializer(), textEdit))
    constructor(insertReplaceEdit: InsertReplaceEdit) : this(
        LSP.json.encodeToJsonElement(
            InsertReplaceEdit.serializer(),
            insertReplaceEdit
        )
    )
}

//todo: custom serializer needed
@Serializable
@JvmInline
value class StringOrMarkupContent private constructor(val content: JsonElement) {
  constructor(content: String) : this(JsonPrimitive(content))
  constructor(content: MarkupContent) : this(LSP.json.encodeToJsonElement(MarkupContent.serializer(), content))

  fun contentAsString(): String = when (content) {
    is JsonPrimitive -> content.content
    is JsonObject -> LSP.json.decodeFromJsonElement(MarkupContent.serializer(), content).value
    else -> error("Unexpected content type: ${content::class.simpleName}")
  }
}

class CompletionItemKindSerializer : EnumAsIntSerializer<CompletionItemKind>(
    serialName = "CompletionTriggerKind",
    serialize = CompletionItemKind::kind,
    deserialize = { CompletionItemKind.values().get(it - 1) },
)

/**
 * The kind of a completion entry.
 */
@Serializable(CompletionItemKindSerializer::class)
enum class CompletionItemKind(val kind: Int) {
    Text(1),
    Method(2),
    Function(3),
    Constructor(4),
    Field(5),
    Variable(6),
    Class(7),
    Interface(8),
    Module(9),
    Property(10),
    Unit(11),
    Value(12),
    Enum(13),
    Keyword(14),
    Snippet(15),
    Color(16),
    File(17),
    Reference(18),
    Folder(19),
    EnumMember(20),
    Constant(21),
    Struct(22),
    Event(23),
    Operator(24),
    TypeParameter(25);
}

val CompletionRequestType: RequestType<CompletionParams, CompletionList, Unit> =
    RequestType(
        "textDocument/completion",
        CompletionParams.serializer(),
        CompletionList.serializer(),
        Unit.serializer()
    )

val CompletionResolveRequestType: RequestType<CompletionItem, CompletionItem, Unit> =
    RequestType(
        "completionItem/resolve",
        CompletionItem.serializer(),
        CompletionItem.serializer(),
        Unit.serializer()
    )