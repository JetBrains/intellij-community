package com.jetbrains.lsp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths


/**
 * URI following the URI specification, so special spaces (like spaces) are encoded.
 *
 * It may not correspond to URIs which are used inside IntelliJ
 */
@Serializable
@JvmInline
value class URI(val uri: String) {
    init {
        require(isValidUriString(uri)) { "Invalid URI: $uri" }
    }

    /**
     * Returns the URI's schema without schema delimiter (`://`)
     */
    val scheme: String get() = asJavaUri().scheme

    /**
     * Returns the file name
     */
    val fileName: String get() = asJavaUri().path.substringAfterLast('/')

    /**
     * Returns the file extension (without dot) if present
     */
    val fileExtension: String?
        get() {
            val name = fileName
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex > 0) name.substring(dotIndex + 1) else null
        }


    fun asJavaUri(): java.net.URI = java.net.URI(uri)

    object Schemas {
        const val FILE: String = "file"
        const val JRT: String = "jrt"
        const val JAR: String = "jar"
    }

    companion object {
        /**
         * We need to have consistent URIs as they are used as keys in the analyzer
         */
        fun isValidUriString(uriString: String): Boolean {
            val javaUri = runCatching { java.net.URI(uriString) }.getOrNull() ?: return false
            return javaUri.scheme != null
        }
    }
}


@Serializable
data class RegularExpressionsClientCapabilities(
    val engine: String,
    val version: String? = null,
)

@Serializable
data class Position(
    /**
     * Line position in a document (zero-based).
     */
    val line: Int,

    /**
     * Character offset on a line in a document (zero-based). The meaning of this
     * offset is determined by the negotiated `PositionEncodingKind`.
     *
     * If the character value is greater than the line length it defaults back
     * to the line length.
     */
    val character: Int,
) : Comparable<Position> {

    override fun toString(): String {
        return "$line:$character"
    }

    override fun compareTo(other: Position): Int {
        return compareValuesBy(this, other, { it.line }, { it.character })
    }

    companion object {
        val ZERO: Position = Position(0, 0)

        /**
         * To avoid computing the line size, we use a very large number that will hopefully be reached by the line size.
         *
         * We do not use `Int.MAX_VALUE` directly to avoid possible overflows in operations on the client side.
         */
        const val EOL_INDEX: Int = Int.MAX_VALUE / 4

        fun lineStart(line: Int): Position = Position(line, 0)

        fun lineEnd(line: Int): Position = Position(line, EOL_INDEX)
    }
}

fun Position.offsetCharacter(offset: Int): Position = Position(line, character + offset)

operator fun Position.plus(other: Position): Position = Position(line + other.line, character + other.character)

operator fun Position.minus(other: Position): Position = Position(line - other.line, character - other.character)

@Serializable
enum class PositionEncodingKind {
    /**
     * Character offsets count UTF-8 code units (e.g., bytes).
     */
    @SerialName("utf-8")
    UTF8,

    /**
     * Character offsets count UTF-16 code units.
     *
     * This is the default and must always be supported
     * by servers.
     */
    @SerialName("utf-16")
    UTF16,

    /**
     * Character offsets count UTF-32 code units.
     *
     * Implementation note: these are the same as Unicode code points,
     * so this `PositionEncodingKind` may also be used for an
     * encoding-agnostic representation of character offsets.
     */
    @SerialName("utf-32")
    UTF32
}

@Serializable
data class Range(
    /**
     * The range's start position.
     */
    val start: Position,

    /**
     * The range's end position.
     */
    val end: Position,
) {
    override fun toString(): String {
        return "[$start, $end]"
    }

    /**
     * Extends the range so it includes also all remaining characters in the last line including the line break
     */
    fun toTheLineEndWithLineBreak(): Range = Range(start, Position(end.line + 1, 0))

    companion object {
        val BEGINNING: Range = Range(Position.ZERO, Position.ZERO)

        fun empty(position: Position): Range = Range(position, position)

        fun fromPositionTillLineEnd(from: Position): Range = Range(from, Position.lineEnd(from.line))

        fun fromLineStartTillPosition(till: Position): Range = Range(Position.lineStart(till.line), till)

        fun fullLine(line: Int): Range = Range(Position.lineStart(line), Position.lineEnd(line))
    }
}

fun Range.intersects(other: Range): Boolean =
    start <= other.end && end >= other.start

fun Range.isSingleLine(): Boolean =
  start.line == end.line

@Serializable
@JvmInline
value class DocumentUri(val uri: URI)

@Serializable
data class TextDocumentItem(
    /**
     * The text document's URI.
     */
    val uri: DocumentUri,

    /**
     * The text document's language identifier.
     */
    val languageId: String,

    /**
     * The version number of this document (it will increase after each
     * change, including undo/redo).
     */
    val version: Int,

    /**
     * The content of the opened text document.
     */
    val text: String,
)

@Serializable
data class TextDocumentIdentifier(
    /**
     * The text document's URI.
     */
    val uri: DocumentUri,
    /**
     * The version number of this document.
     *
     * The version number of a document will increase after each change,
     * including undo/redo. The number doesn't need to be consecutive.
     */
    val version: Int? = null,
)

interface TextDocumentPositionParams {
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier

    /**
     * The position inside the text document.
     */
    val position: Position
}

/**
 * A document filter denotes a document through properties like language, scheme or pattern.
 * An example is a filter that applies to TypeScript files on disk.
 * Another example is a filter that applies to JSON files with name package.json:
 *
 * ```json
 * { language: 'typescript', scheme: 'file' }
 * { language: 'json', pattern: '** /package.json' }
 * ```
 *
 * Please note that for a document filter to be valid at least one of the properties for language, scheme, or pattern must be set.
 * To keep the type definition simple all properties are marked as optional.
 */
@Serializable
data class DocumentFilter(
    /**
     * A language id, like `typescript`.
     */
    val language: String? = null,

    /**
     * A Uri scheme, like `file` or `untitled`.
     */
    val scheme: String? = null,

    /**
     * A glob pattern, like `*.{ts,js}`.
     *
     * Glob patterns can have the following syntax:
     * - `*` to match one or more characters in a path segment
     * - `?` to match on one character in a path segment
     * - `**` to match any number of path segments, including none
     * - `{}` to group sub patterns into an OR expression
     *   matches all TypeScript and JavaScript files)
     * - `[]` to declare a range of characters to match in a path segment
     *   (e.g., `example.[0-9]` to match on `example.0`, `example.1`, …)
     * - `[!...]` to negate a range of characters to match in a path segment
     *   (e.g., `example.[!0-9]` to match on `example.a`, `example.b`, but
     *   not `example.0`)
     */
    val pattern: String? = null,
) {
    init {
        require(language != null || scheme != null || pattern != null) {
            "DocumentFilter must have at least one property set (language, scheme or pattern)"
        }
    }
}

@Serializable
@JvmInline
value class DocumentSelector(val filters: List<DocumentFilter>) {
    constructor(vararg filters: DocumentFilter) : this(filters.toList())
}

@Serializable
data class TextEdit(
    /**
     * The range of the text document to be manipulated. To insert
     * text into a document create a range where start == end.
     */
    val range: Range,

    /**
     * The string to be inserted. For delete operations use an
     * empty string.
     */
    val newText: String,

    /**
     * The actual annotation identifier.
     */
    val annotationId: ChangeAnnotationIdentifier? = null,
)

@Serializable
@JvmInline
value class ChangeAnnotationIdentifier(val id: String)

/**
 * Additional information that describes document changes.
 *
 * @since 3.16.0
 */
@Serializable
data class ChangeAnnotation(
    /**
     * A human-readable string describing the actual change. The string
     * is rendered prominently in the user interface.
     */
    val label: String,

    /**
     * A flag which indicates that user confirmation is needed
     * before applying the change.
     */
    val needsConfirmation: Boolean? = null,

    /**
     * A human-readable string which is rendered less prominently in
     * the user interface.
     */
    val description: String? = null,
)

//todo: custom serializer is required
@Serializable
@JvmInline
value class DocumentChange(val change: JsonElement) // TextDocumentEdit | FileChange

@Serializable
sealed interface FileChange

@Serializable
data class TextDocumentEdit(
    /**
     * The text document to change.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The edits to be applied.
     *
     * @since 3.16.0 - support for AnnotatedTextEdit. This is guarded by the
     * client capability `workspace.workspaceEdit.changeAnnotationSupport`
     */
    val edits: List<TextEdit>,
)

@Serializable
data class Location(
    val uri: DocumentUri,
    val range: Range,
)

@Serializable
data class LocationLink(
    /**
     * Span of the origin of this link.
     *
     * Used as the underlined span for mouse interaction. Defaults to the word
     * range at the mouse position.
     */
    val originSelectionRange: Range? = null,

    /**
     * The target resource identifier of this link.
     */
    val targetUri: DocumentUri,

    /**
     * The full target range of this link. If the target for example is a symbol
     * then target range is the range enclosing this symbol not including
     * leading/trailing whitespace but everything else like comments. This
     * information is typically used to highlight the range in the editor.
     */
    val targetRange: Range,

    /**
     * The range that should be selected and revealed when this link is being
     * followed, e.g the name of a function. Must be contained by the
     * `targetRange`. See also `DocumentSymbol#range`
     */
    val targetSelectionRange: Range,
)

@Serializable
data class Command(
    /**
     * Title of the command, like `save`.
     */
    val title: String,

    /**
     * The identifier of the actual command handler.
     */
    val command: String,

    /**
     * Arguments that the command handler should be
     * invoked with.
     */
    val arguments: List<JsonElement>? = null,
)

@Serializable
enum class MarkupKind {
    /**
     * Plain text is supported as a content format
     */
    @SerialName("plaintext")
    PlainText,

    /**
     * Markdown is supported as a content format
     */
    @SerialName("markdown")
    Markdown
}

/**
 * Describes the content type that a client supports in various
 * result literals like `Hover`, `ParameterInfo` or `CompletionItem`.
 *
 * Please note that `MarkupKinds` must not start with a `$`. This kinds
 * are reserved for internal usage.
 */
@Serializable
enum class MarkupKindType {
    /**
     * Plain text is supported as a content format
     */
    @SerialName("plaintext")

    PlaintText,

    /**
     * Markdown is supported as a content format
     */
    @SerialName("markdown")
    Markdown
}

/**
 * A `MarkupContent` literal represents a string value which content is
 * interpreted base on its kind flag. Currently the protocol supports
 * `plaintext` and `markdown` as markup kinds.
 *
 * If the kind is `markdown` then the value can contain fenced code blocks like
 * in GitHub issues.
 *
 * Here is an example how such a string can be constructed using
 * JavaScript / TypeScript:
 * ```typescript
 * let markdown: MarkdownContent = {
 * 	kind: MarkupKind.Markdown,
 * 	value: [
 * 		'# Header',
 * 		'Some text',
 * 		'```typescript',
 * 		'someCode();',
 * 		'```'
 * 	].join('\n')
 * };
 * ```
 *
 * *Please Note* that clients might sanitize the return markdown. A client could
 * decide to remove HTML from the markdown to avoid script execution.
 */
@Serializable
data class MarkupContent(
    /**
     * The type of the Markup
     */
    val kind: MarkupKindType,

    /**
     * The content itself
     */
    val value: String,
)

@Serializable
data class MarkdownClientCapabilities(
    /**
     * The name of the parser.
     */
    val parser: String,

    /**
     * The version of the parser.
     */
    val version: String? = null,

    /**
     * A list of HTML tags that the client allows / supports in Markdown.
     *
     * @since 3.17.0
     */
    val allowedTags: List<String>? = null,
)

@Serializable
data class CreateFileOptions(
    /**
     * Overwrite existing file. Overwrite wins over `ignoreIfExists`
     */
    val overwrite: Boolean? = null,

    /**
     * Ignore if exists.
     */
    val ignoreIfExists: Boolean? = null,
)

@SerialName("create")
@Serializable
data class CreateFile(
    /**
     * The resource to create.
     */
    val uri: DocumentUri,

    /**
     * Additional options
     */
    val options: CreateFileOptions? = null,

    /**
     * An optional annotation identifier describing the operation.
     *
     * @since 3.16.0
     */
    val annotationId: ChangeAnnotationIdentifier? = null,
) : FileChange

@Serializable
data class RenameFileOptions(
    /**
     * Overwrite target if existing. Overwrite wins over `ignoreIfExists`
     */
    val overwrite: Boolean? = null,

    /**
     * Ignores if target exists.
     */
    val ignoreIfExists: Boolean? = null,
)

@Serializable
@SerialName("rename")
data class RenameFile(
    /**
     * The old (existing) location.
     */
    val oldUri: DocumentUri,

    /**
     * The new location.
     */
    val newUri: DocumentUri,

    /**
     * Rename options.
     */
    val options: RenameFileOptions? = null,

    /**
     * An optional annotation identifier describing the operation.
     *
     * @since 3.16.0
     */
    val annotationId: ChangeAnnotationIdentifier? = null,
) : FileChange

@Serializable
data class DeleteFileOptions(
    /**
     * Delete the content recursively if a folder is denoted.
     */
    val recursive: Boolean? = null,

    /**
     * Ignore the operation if the file doesn't exist.
     */
    val ignoreIfNotExists: Boolean? = null,
)

@Serializable
@SerialName("delete")
data class DeleteFile(
    /**
     * The file to delete.
     */
    val uri: DocumentUri,

    /**
     * Delete options.
     */
    val options: DeleteFileOptions? = null,

    /**
     * An optional annotation identifier describing the operation.
     *
     * @since 3.16.0
     */
    val annotationId: ChangeAnnotationIdentifier? = null,
) : FileChange

@Serializable
data class WorkspaceEdit(
    /**
     * Holds changes to existing resources.
     */
    val changes: Map<DocumentUri, List<TextEdit>>? = null,

    /**
     * Depending on the client capability `workspace.workspaceEdit.resourceOperations`,
     * document changes are either an array of `TextDocumentEdit`s to express changes
     * to different text documents where each text document edit addresses a specific
     * version of a text document, or it can contain `TextDocumentEdit`s mixed with
     * create, rename and delete file / folder operations.
     *
     * Whether a client supports versioned document edits is expressed via
     * `workspace.workspaceEdit.documentChanges` client capability.
     *
     * If a client neither supports `documentChanges` nor
     * `workspace.workspaceEdit.resourceOperations`, only plain `TextEdit`s
     * using the `changes` property are supported.
     */
    val documentChanges: List<DocumentChange>? = null, // Use proper types or sealed classes for TextDocumentEdit, CreateFile, RenameFile, DeleteFile if defined elsewhere

    /**
     * A map of change annotations that can be referenced in `AnnotatedTextEdit`s
     * or create, rename and delete file / folder operations.
     *
     * Whether clients honor this property depends on the client capability
     * `workspace.changeAnnotationSupport`.
     *
     * @since 3.16.0
     */
    val changeAnnotations: Map<ChangeAnnotationIdentifier, ChangeAnnotation>? = null,
)

@Serializable
data class WorkspaceEditClientCapabilities(
    /**
     * The client supports versioned document changes in `WorkspaceEdit`s
     */
    val documentChanges: Boolean? = null,

    /**
     * The resource operations the client supports. Clients should at least
     * support 'create', 'rename' and 'delete' files and folders.
     *
     * @since 3.13.0
     */
    val resourceOperations: List<ResourceOperationKind>? = null,

    /**
     * The failure handling strategy of a client if applying the workspace edit
     * fails.
     *
     * @since 3.13.0
     */
    val failureHandling: FailureHandlingKind? = null,

    /**
     * Whether the client normalizes line endings to the client specific
     * setting. If set to `true`, the client will normalize line ending characters
     * in a workspace edit to the client-specific newline character(s).
     *
     * @since 3.16.0
     */
    val normalizesLineEndings: Boolean? = null,

    /**
     * Whether the client in general supports change annotations on text edits,
     * create file, rename file, and delete file changes.
     *
     * @since 3.16.0
     */
    val changeAnnotationSupport: ChangeAnnotationSupport? = null,
)

@Serializable
data class ChangeAnnotationSupport(
    /**
     * Whether the client groups edits with equal labels into tree nodes,
     * for instance, all edits labeled with "Changes in Strings" would
     * be a tree node.
     */
    val groupsOnLabel: Boolean? = null,
)

@Serializable
enum class ResourceOperationKind {
    @SerialName("create")
    Create,

    @SerialName("rename")
    Rename,

    @SerialName("delete")
    Delete
}

@Serializable
enum class FailureHandlingKind {
    /**
     * Applying the workspace change is simply aborted if one of the changes
     * provided fails. All operations executed before the failing operation
     * stay executed.
     */
    @SerialName("abort")
    Abort,

    /**
     * All operations are executed transactional. That means they either all
     * succeed or no changes at all are applied to the workspace.
     */
    @SerialName("transactional")
    Transactional,

    /**
     * If the workspace edit contains only textual file changes they are
     * executed transactional. If resource changes (create, rename or delete
     * file) are part of the change the failure handling strategy is abort.
     */
    @SerialName("textOnlyTransactional")
    TextOnlyTransactional,

    /**
     * The client tries to undo the operations already executed. But there is no
     * guarantee that this is succeeding.
     */
    @SerialName("undo")
    Undo
}

// todo: use 'kind' as descriminant in serialization
@Serializable
sealed interface WorkDoneProgress {
    @SerialName("begin")
    @Serializable
    data class Begin(
        val title: String,
        val cancellable: Boolean? = null,
        val message: String? = null,
        val percentage: Int? = null,
    ) : WorkDoneProgress {
        @Deprecated("Use `WorkDoneProgress.serializer()` instead to have a proper `kind` field set", level = DeprecationLevel.ERROR)
        companion object
    }

    @SerialName("report")
    @Serializable
    data class Report(
        val cancellable: Boolean? = null,
        val message: String? = null,
        val percentage: Int? = null,
    ) : WorkDoneProgress {
        @Deprecated("Use `WorkDoneProgress.serializer()` instead to have a proper `kind` field set", level = DeprecationLevel.ERROR)
        private companion object
    }

    @SerialName("end")
    @Serializable
    data class End(
        val message: String? = null,
    ) : WorkDoneProgress {
        @Deprecated("Use `WorkDoneProgress.serializer()` instead to have a proper `kind` field set", level = DeprecationLevel.ERROR)
        private companion object
    }
}

interface WorkDoneProgressParams {
    /**
     * An optional token that a server can use to report work done progress.
     */
    val workDoneToken: ProgressToken?
}

interface WorkDoneProgressOptions {
    val workDoneProgress: Boolean?
}

interface PartialResultParams {
    /**
     * An optional token that a server can use to report partial results (e.g., streaming) to the client.
     */
    val partialResultToken: ProgressToken?
}

@Serializable
enum class TraceValue {
    @SerialName("off")
    Off,

    @SerialName("messages")
    Messages,

    @SerialName("verbose")
    Verbose
}

@Serializable
data class ClientInfo(
    /**
     * The name of the client as defined by the client.
     */
    val name: String,

    /**
     * The client's version as defined by the client.
     */
    val version: String? = null,
)

@Serializable
data class StaleRequestSupport(
    /**
     * The client will actively cancel the request.
     */
    val cancel: Boolean,

    /**
     * The list of requests for which the client will retry the request if
     * it receives a response with error code `ContentModified`.
     */
    val retryOnContentModified: List<String>,
)

@Serializable
data class WorkspaceFolder(
    /**
     * The associated URI for this workspace folder.
     */
    val uri: URI,

    /**
     * The name of the workspace folder. Used to refer to this
     * workspace folder in the user interface.
     */
    val name: String,
)

@Serializable
data class Registration(
    val id: String,
    val method: String,
    val registerOptions: JsonElement? = null,
)

@Serializable
data class RegistrationParams(
    val registrations: List<Registration>,
)

/**
 * Static registration options to be returned in the initialize request.
 */

interface StaticRegistrationOptions {
    /**
     * The id used to register the request. The id can be used to deregister
     * the request again. See also Registration#id.
     */
    val id: String?
}

/**
 * General text document registration options.
 */
interface TextDocumentRegistrationOptions {
    /**
     * A document selector to identify the scope of the registration. If set to
     * null the document selector provided on the client side will be used.
     */
    val documentSelector: DocumentSelector?
}

@Serializable
data class Unregistration(
    /**
     * The id used to unregister the request or notification. Usually an id
     * provided during the register request.
     */
    val id: String,

    /**
     * The method / capability to unregister for.
     */
    val method: String,
)

@Serializable
data class UnregistrationParams(
    /**
     * This should correctly be named `unregistrations`. However changing this
     * is a breaking change and needs to wait until we deliver a 4.x version
     * of the specification.
     */
    @SerialName("unregisterations")
    val unregistrations: List<Unregistration>,
)

object LSP {
    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        classDiscriminator = "kind"
    }

    val ProgressNotificationType: NotificationType<ProgressParams> =
        NotificationType("$/progress", ProgressParams.serializer())

    val CancelNotificationType: NotificationType<CancelParams> =
        NotificationType("$/cancelRequest", CancelParams.serializer())

    val RegisterCapabilityNotificationType: NotificationType<RegistrationParams> =
        NotificationType("client/registerCapability", RegistrationParams.serializer())

    val UnregisterCapabilityNotificationType: NotificationType<UnregistrationParams> =
        NotificationType("client/unregisterCapability", UnregistrationParams.serializer())
}