package com.jetbrains.lsp.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

@Serializable
data class NotebookDocumentClientCapabilities(
    /**
     * Capabilities specific to notebook document synchronization
     *
     * @since 3.17.0
     */
    val synchronization: NotebookDocumentSyncClientCapabilities
)

@Serializable
data class NotebookDocumentSyncClientCapabilities(
    /**
     * Whether implementation supports dynamic registration. If this is
     * set to `true` the client supports the new
     * `(NotebookDocumentSyncRegistrationOptions & NotebookDocumentSyncOptions)`
     * return value for the corresponding server capability as well.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * The client supports sending execution summary data per cell.
     */
    val executionSummarySupport: Boolean? = null
)

/**
 * A notebook document.
 *
 * @since 3.17.0
 */
@Serializable
data class NotebookDocument(
    /**
     * The notebook document's URI.
     */
    val uri: URI,

    /**
     * The type of the notebook.
     */
    val notebookType: String,

    /**
     * The version number of this document (it will increase after each
     * change, including undo/redo).
     */
    val version: Int,

    /**
     * Additional metadata stored with the notebook
     * document.
     */
    val metadata: JsonObject? = null,

    /**
     * The cells of a notebook.
     */
    val cells: List<NotebookCell>,
)

/**
 * A notebook cell.
 *
 * A cell's document URI must be unique across ALL notebook
 * cells and can therefore be used to uniquely identify a
 * notebook cell or the cell's text document.
 *
 * @since 3.17.0
 */
@Serializable
data class NotebookCell(
    /**
     * The cell's kind
     */
    val kind: NotebookCellKind,

    /**
     * The URI of the cell's text document
     * content.
     */
    val document: DocumentUri,

    /**
     * Additional metadata stored with the cell.
     */
    val metadata: JsonObject? = null,

    /**
     * Additional execution summary information
     * if supported by the client.
     */
    val executionSummary: ExecutionSummary? = null,
)

class NotebookCellKindSerializer : EnumAsIntSerializer<NotebookCellKind>(
    serialName = "NotebookCellKind",
    serialize = NotebookCellKind::value,
    deserialize = { NotebookCellKind.entries[it - 1] },
)

/**
 * A notebook cell kind.
 *
 * @since 3.17.0
 */
@Serializable
enum class NotebookCellKind(val value: Int) {
    /**
     * A markup-cell is formatted source that is used for display.
     */
    Markup(1),

    /**
     * A code-cell is source code.
     */
    Code(2)
}

/**
 * Execution summary information.
 */
@Serializable
data class ExecutionSummary(
    /**
     * A strict monotonically increasing value
     * indicating the execution order of a cell
     * inside a notebook.
     */
    val executionOrder: UInt,

    /**
     * Whether the execution was successful or
     * not if known by the client.
     */
    val success: Boolean? = null,
)

/**
 * A notebook cell text document filter denotes a cell text
 * document by different properties.
 *
 * @since 3.17.0
 */
@Serializable
data class NotebookCellTextDocumentFilter(
    /**
     * A filter that matches against the notebook
     * containing the notebook cell. If a string
     * value is provided it matches against the
     * notebook type. '*' matches every notebook.
     */
    val notebook: StringOrNotebookDocumentFilter,

    /**
     * A language id like `python`.
     *
     * Will be matched against the language id of the
     * notebook cell document. '*' matches every language.
     */
    val language: String? = null,
)

/**
 * A notebook document filter denotes a notebook document by
 * different properties.
 *
 * @since 3.17.0
 */
@Serializable
data class NotebookDocumentFilter(
    val notebookType: String,
    val scheme: String? = null,
    val pattern: String? = null
)

/**
 * Represents a union type of String or NotebookDocumentFilter.
 */
@Serializable // todo: custom serializer
@JvmInline
value class StringOrNotebookDocumentFilter(val value: JsonElement) // String | NotebookDocumentFilter

/**
 * Options specific to a notebook plus its cells
 * to be synced to the server.
 *
 * If a selector provides a notebook document
 * filter but no cell selector all cells of a
 * matching notebook document will be synced.
 *
 * If a selector provides no notebook document
 * filter but only a cell selector all notebook
 * documents that contain at least one matching
 * cell will be synced.
 *
 * @since 3.17.0
 */
@Serializable
data class NotebookDocumentSyncOptions(
    /**
     * The notebooks to be synced.
     */
    val notebookSelector: List<NotebookSelector>,

    /**
     * Whether save notification should be forwarded to
     * the server. Will only be honored if mode === `notebook`.
     */
    val save: Boolean? = null,
    override val id: String?,
) : StaticRegistrationOptions

@Serializable
data class NotebookSelector(
    /**
     * The notebook to be synced. If a string value is provided, it matches
     * against the notebook type. '*' matches every notebook.
     */
    val notebook: StringOrNotebookDocumentFilter? = null,

    /**
     * The cells of the matching notebook to be synced.
     */
    val cells: List<Cell>?
)

@Serializable
data class Cell(
    /**
     * The language of the cell.
     */
    val language: String,
)

@Serializable
data class DidOpenNotebookDocumentParams(
    /**
     * The notebook document that got opened.
     */
    val notebookDocument: NotebookDocument,

    /**
     * The text documents that represent the content
     * of a notebook cell.
     */
    val cellTextDocuments: List<TextDocumentItem>,
)

data class DidChangeNotebookDocumentParams(
    /**
     * The notebook document that did change. The version number points
     * to the version after all provided changes have been applied.
     */
    val notebookDocument: VersionedNotebookDocumentIdentifier,

    /**
     * The actual changes to the notebook document.
     *
     * The change describes a single state change to the notebook document.
     * So it moves a notebook document, its cells and its cell text document
     * contents from state S to S'.
     *
     * To mirror the content of a notebook using change events use the
     * following approach:
     * - start with the same initial content
     * - apply the 'notebookDocument/didChange' notifications in the order
     *   you receive them.
     */
    val change: NotebookDocumentChangeEvent,
)

data class DidSaveNotebookDocumentParams(
    /**
     * The notebook document that got saved.
     */
    val notebookDocument: NotebookDocumentIdentifier,
)

data class DidCloseNotebookDocumentParams(
    /**
     * The notebook document that got closed.
     */
    val notebookDocument: NotebookDocumentIdentifier,

    /**
     * The text documents that represent the content
     * of a notebook cell that got closed.
     */
    val cellTextDocuments: List<TextDocumentIdentifier>,
)

data class VersionedNotebookDocumentIdentifier(
    /**
     * The version number of this notebook document.
     */
    val version: Int,

    /**
     * The notebook document's URI.
     */
    val uri: URI,
)

data class NotebookDocumentChangeEvent(
    /**
     * The changed metadata if any.
     */
    val metadata: JsonObject? = null,

    /**
     * Changes to cells.
     */
    val cells: NotebookCellChanges? = null,
)

data class NotebookCellChanges(
    /**
     * Changes to the cell structure to add or
     * remove cells.
     */
    val structure: NotebookCellStructureChange? = null,

    /**
     * Changes to notebook cells properties like its
     * kind, execution summary or metadata.
     */
    val data: List<NotebookCell>? = null,

    /**
     * Changes to the text content of notebook cells.
     */
    val textContent: List<CellTextContentChange>? = null,
)

data class NotebookCellStructureChange(
    /**
     * The change to the cell array.
     */
    val array: NotebookCellArrayChange,

    /**
     * Additional opened cell text documents.
     */
    val didOpen: List<TextDocumentItem>? = null,

    /**
     * Additional closed cell text documents.
     */
    val didClose: List<TextDocumentIdentifier>? = null,
)

data class NotebookCellArrayChange(
    /**
     * The start offset of the cell that changed.
     */
    val start: UInt,

    /**
     * The deleted cells.
     */
    val deleteCount: UInt,

    /**
     * The new cells, if any.
     */
    val cells: List<NotebookCell>? = null,
)

data class CellTextContentChange(
    /**
     * The document that changed.
     */
    val document: TextDocumentIdentifier,

    /**
     * The change events for the document's text.
     */
    val changes: List<TextDocumentContentChangeEvent>,
)

data class NotebookDocumentIdentifier(
    /**
     * The notebook document's URI.
     */
    val uri: URI,
)