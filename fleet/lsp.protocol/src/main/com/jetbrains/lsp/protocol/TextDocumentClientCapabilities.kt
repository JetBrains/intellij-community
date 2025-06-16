package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable


@Serializable
data class TextDocumentClientCapabilities(
    /**
     * Capabilities specific to the `textDocument/synchronization` request.
     */
    val synchronization: TextDocumentSyncClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/completion` request.
     */
    val completion: CompletionClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/hover` request.
     */
    val hover: HoverClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/signatureHelp` request.
     */
    val signatureHelp: SignatureHelpClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/declaration` request.
     *
     * @since 3.14.0
     */
    val declaration: DeclarationClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/definition` request.
     */
    val definition: DefinitionClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/typeDefinition` request.
     *
     * @since 3.6.0
     */
    val typeDefinition: TypeDefinitionClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/implementation` request.
     *
     * @since 3.6.0
     */
    val implementation: ImplementationClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/references` request.
     */
    val references: ReferenceClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/documentHighlight` request.
     */
    val documentHighlight: DocumentHighlightClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/documentSymbol` request.
     */
    val documentSymbol: DocumentSymbolClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/codeAction` request.
     */
    val codeAction: CodeActionClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/codeLens` request.
     */
    val codeLens: CodeLensClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/documentLink` request.
     */
    val documentLink: DocumentLinkClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/documentColor` and the
     * `textDocument/colorPresentation` request.
     *
     * @since 3.6.0
     */
    val colorProvider: DocumentColorClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/formatting` request.
     */
    val formatting: DocumentFormattingClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/rangeFormatting` request.
     */
    val rangeFormatting: DocumentRangeFormattingClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/onTypeFormatting` request.
     */
    val onTypeFormatting: DocumentOnTypeFormattingClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/rename` request.
     */
    val rename: RenameClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/publishDiagnostics` notification.
     */
    val publishDiagnostics: PublishDiagnosticsClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/foldingRange` request.
     *
     * @since 3.10.0
     */
    val foldingRange: FoldingRangeClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/selectionRange` request.
     *
     * @since 3.15.0
     */
    val selectionRange: SelectionRangeClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/linkedEditingRange` request.
     *
     * @since 3.16.0
     */
    val linkedEditingRange: LinkedEditingRangeClientCapabilities? = null,

    /**
     * Capabilities specific to the various call hierarchy requests.
     *
     * @since 3.16.0
     */
    val callHierarchy: CallHierarchyClientCapabilities? = null,

    /**
     * Capabilities specific to the various semantic token requests.
     *
     * @since 3.16.0
     */
    val semanticTokens: SemanticTokensClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/moniker` request.
     *
     * @since 3.16.0
     */
    val moniker: MonikerClientCapabilities? = null,

    /**
     * Capabilities specific to the various type hierarchy requests.
     *
     * @since 3.17.0
     */
    val typeHierarchy: TypeHierarchyClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/inlineValue` request.
     *
     * @since 3.17.0
     */
    val inlineValue: InlineValueClientCapabilities? = null,

    /**
     * Capabilities specific to the `textDocument/inlayHint` request.
     *
     * @since 3.17.0
     */
    val inlayHint: InlayHintClientCapabilities? = null,

    /**
     * Capabilities specific to the diagnostic pull model.
     *
     * @since 3.17.0
     */
    val diagnostic: DiagnosticClientCapabilities? = null
)

typealias HoverClientCapabilities = Unknown
typealias SignatureHelpClientCapabilities = Unknown
typealias TypeDefinitionClientCapabilities = Unknown
typealias ImplementationClientCapabilities = Unknown
typealias ReferenceClientCapabilities = Unknown
typealias DocumentHighlightClientCapabilities = Unknown
typealias DocumentSymbolClientCapabilities = Unknown
typealias CodeActionClientCapabilities = Unknown
typealias CodeLensClientCapabilities = Unknown
typealias DocumentLinkClientCapabilities = Unknown
typealias DocumentColorClientCapabilities = Unknown
typealias DocumentFormattingClientCapabilities = Unknown
typealias DocumentRangeFormattingClientCapabilities = Unknown
typealias DocumentOnTypeFormattingClientCapabilities = Unknown
typealias RenameClientCapabilities = Unknown
typealias FoldingRangeClientCapabilities = Unknown
typealias SelectionRangeClientCapabilities = Unknown
typealias LinkedEditingRangeClientCapabilities = Unknown
typealias CallHierarchyClientCapabilities = Unknown
typealias SemanticTokensClientCapabilities = Unknown
typealias MonikerClientCapabilities = Unknown
typealias TypeHierarchyClientCapabilities = Unknown
typealias InlineValueClientCapabilities = Unknown
typealias InlayHintClientCapabilities = Unknown