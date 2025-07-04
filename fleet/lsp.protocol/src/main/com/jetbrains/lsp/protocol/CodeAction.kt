package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CodeActionOptions(
    /**
     * CodeActionKinds that this server may return.
     *
     * The list of kinds may be generic, such as `CodeActionKind.Refactor`,
     * or the server may list out every specific kind they provide.
     */
    val codeActionKinds: List<CodeActionKind>,

    /**
     * The server provides support to resolve additional
     * information for a code action.
     *
     * @since 3.16.0
     */
    val resolveProvider: Boolean?,
    override val workDoneProgress: Boolean?,
) : WorkDoneProgressOptions

/**
 * The kind of code action.
 *
 * Kinds are a hierarchical list of identifiers separated by `.`,
 * e.g. `"refactor.extract.function"`.
 *
 * The set of kinds is open and client needs to announce the kinds it supports
 * to the server during initialization.
 */
@Serializable
@JvmInline
value class CodeActionKind(val value: String) {
    companion object {
        /**
         * Empty kind.
         */
        val Empty: CodeActionKind = CodeActionKind("")

        /**
         * Base kind for quickfix actions: "quickfix".
         */
        val QuickFix: CodeActionKind = CodeActionKind("quickfix")

        /**
         * Base kind for refactoring actions: "refactor".
         */
        val Refactor: CodeActionKind = CodeActionKind("refactor")

        /**
         * Base kind for refactoring extraction actions: "refactor.extract".
         *
         * Example extract actions:
         *
         * - Extract method
         * - Extract function
         * - Extract variable
         * - Extract interface from class
         * - ...
         */
        val RefactorExtract: CodeActionKind = CodeActionKind("refactor.extract")

        /**
         * Base kind for refactoring inline actions: "refactor.inline".
         *
         * Example inline actions:
         *
         * - Inline function
         * - Inline variable
         * - Inline constant
         * - ...
         */
        val RefactorInline: CodeActionKind = CodeActionKind("refactor.inline")

        /**
         * Base kind for refactoring rewrite actions: "refactor.rewrite".
         *
         * Example rewrite actions:
         *
         * - Convert JavaScript function to class
         * - Add or remove parameter
         * - Encapsulate field
         * - Make method static
         * - Move method to base class
         * - ...
         */
        val RefactorRewrite: CodeActionKind = CodeActionKind("refactor.rewrite")

        /**
         * Base kind for source actions: `source`.
         *
         * Source code actions apply to the entire file.
         */
        val Source: CodeActionKind = CodeActionKind("source")

        /**
         * Base kind for an organize imports source action:
         * `source.organizeImports`.
         */
        val SourceOrganizeImports: CodeActionKind = CodeActionKind("source.organizeImports")

        /**
         * Base kind for a "fix all" source action: `source.fixAll`.
         *
         * "Fix all" actions automatically fix errors that have a clear fix that
         * do not require user input. They should not suppress errors or perform
         * unsafe fixes such as generating new types or classes.
         *
         * @since 3.17.0
         */
        val SourceFixAll: CodeActionKind = CodeActionKind("source.fixAll")
    }
}

/**
 * Parameters for a code action request.
 */
@Serializable
data class CodeActionParams(
    /**
     * Params for the CodeActionRequest
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The range for which the command was invoked.
     */
    val range: Range,

    /**
     * Context carrying additional information.
     */
    val context: CodeActionContext,
    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams {
    fun shouldProvideKind(kind: CodeActionKind): Boolean =
        context.only == null || kind in context.only
}

/**
 * Contains additional diagnostic information about the context in which
 * a code action is run.
 */
@Serializable
data class CodeActionContext(
    /**
     * An array of diagnostics known on the client side overlapping the range
     * provided to the `textDocument/codeAction` request. They are provided so
     * that the server knows which errors are currently presented to the user
     * for the given range. There is no guarantee that these accurately reflect
     * the error state of the resource. The primary parameter
     * to compute code actions is the provided range.
     */
    val diagnostics: List<Diagnostic>,

    /**
     * Requested kind of actions to return.
     *
     * Actions not of this kind are filtered out by the client before being
     * shown. So servers can omit computing them.
     */
    val only: List<CodeActionKind>? = null,

    /**
     * The reason why code actions were requested.
     *
     * @since 3.17.0
     */
    val triggerKind: CodeActionTriggerKind? = null
)

@Serializable(with = CodeActionTriggerKind.Serializer::class)
enum class CodeActionTriggerKind(val value: Int) {
    Invoked(1),
    Automatic(2),

    ;

    class Serializer : EnumAsIntSerializer<CodeActionTriggerKind>(
        serialName = CodeActionTriggerKind::class.simpleName!!,
        serialize = CodeActionTriggerKind::value,
        deserialize = { entries[it - 1] },
    )
}

/**
 * A code action represents a change that can be performed in code, e.g., to fix
 * a problem or to refactor code.
 *
 * A CodeAction must set either `edit` and/or a `command`. If both are supplied,
 * the `edit` is applied first, then the `command` is executed.
 */
@Serializable
data class CodeAction(
    /**
     * A short, human-readable, title for this code action.
     */
    val title: String,

    /**
     * The kind of the code action.
     *
     * Used to filter code actions.
     */
    val kind: CodeActionKind? = null,

    /**
     * The diagnostics that this code action resolves.
     */
    val diagnostics: List<Diagnostic>? = null,

    /**
     * Marks this as a preferred action. Preferred actions are used by the
     * `auto fix` command and can be targeted by keybindings.
     *
     * A quick fix should be marked preferred if it properly addresses the
     * underlying error. A refactoring should be marked preferred if it is the
     * most reasonable choice of actions to take.
     *
     * @since 3.15.0
     */
    val isPreferred: Boolean? = null,

    /**
     * Marks that the code action cannot currently be applied.
     *
     * Clients should follow the following guidelines regarding disabled code
     * actions:
     *
     * - Disabled code actions are not shown in automatic lightbulbs code
     *   action menus.
     *
     * - Disabled actions are shown as faded out in the code action menu when
     *   the user requests a more specific type of code action, such as
     *   refactorings.
     *
     * - If the user has a keybinding that auto-applies a code action and only
     *   a disabled code action is returned, the client should show the user
     *   an error message with `reason` in the editor.
     *
     * @since 3.16.0
     */
    val disabled: Disabled? = null,

    /**
     * The workspace edit this code action performs.
     */
    val edit: WorkspaceEdit? = null,

    /**
     * A command this code action executes. If a code action
     * provides an edit and a command, first the edit is
     * executed and then the command.
     */
    val command: Command? = null,

    /**
     * A data entry field that is preserved on a code action between
     * a `textDocument/codeAction` and a `codeAction/resolve` request.
     *
     * @since 3.16.0
     */
    val data: JsonElement? = null
) {
    /**
     * Represents a disabled state for a CodeAction with a reason.
     */
    @Serializable
    data class Disabled(
        /**
         * Human-readable description of why the code action is currently disabled.
         *
         * This is displayed in the code actions UI.
         */
        val reason: String
    )
}

@Serializable(with = CommandOrCodeAction.Serializer::class)
sealed interface CommandOrCodeAction {
    @Serializable
    @JvmInline
    value class Command(val command: com.jetbrains.lsp.protocol.Command) : CommandOrCodeAction

    @Serializable
    @JvmInline
    value class CodeAction(val codeAction: com.jetbrains.lsp.protocol.CodeAction) : CommandOrCodeAction

    class Serializer : JsonContentPolymorphicSerializer<CommandOrCodeAction>(CommandOrCodeAction::class) {
        private fun isCommand(json: JsonObject) = json["command"]?.let { command -> command is JsonPrimitive && command.isString }
            ?: false

        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<CommandOrCodeAction> {
            return when (element) {
                is JsonObject -> if (isCommand(element)) Command.serializer() else CodeAction.serializer()
                else -> throw SerializationException("Expected either Command or CodeAction, got $element")
            }
        }
    }
}

object CodeActions {
    val CodeActionRequest: RequestType<CodeActionParams, List<CommandOrCodeAction>?, Unit> =
        RequestType("textDocument/codeAction", CodeActionParams.serializer(), ListSerializer(CommandOrCodeAction.serializer()).nullable, Unit.serializer())

    val ResolveCodeAction: RequestType<CodeAction, CodeAction, Unit> =
        RequestType("codeAction/resolve", CodeAction.serializer(), CodeAction.serializer(), Unit.serializer())
}
