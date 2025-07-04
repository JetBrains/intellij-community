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
    val diagnostic: DiagnosticClientCapabilities? = null,
)

@Serializable
data class HoverClientCapabilities(
    /**
     * Whether hover supports dynamic registration.
     */
    val dynamicRegistration: Boolean?,

    /**
     * Client supports the follow content formats if the content
     * property refers to a `literal of type MarkupContent`.
     * The order describes the preferred format of the client.
     */
    val contentFormat: List<MarkupKind>?,
)

@Serializable
data class SignatureHelpClientCapabilities(
    /**
     * Whether signature help supports dynamic registration.
     */
    val dynamicRegistration: Boolean?,

    /**
     * The client supports the following `SignatureInformation`
     * specific properties.
     */
    val signatureInformation: SignatureInformation?,

    /**
     * The client supports to send additional context information for a
     * `textDocument/signatureHelp` request. A client that opts into
     * contextSupport will also support the `retriggerCharacters` on
     * `SignatureHelpOptions`.
     *
     * @since 3.15.0
     */
    val contextSupport: Boolean?,
) {
    @Serializable
    data class SignatureInformation(
        /**
         * Client supports the follow content formats for the documentation
         * property. The order describes the preferred format of the client.
         */
        val documentationFormat: List<MarkupKind>?,

        /**
         * Client capabilities specific to parameter information.
         */
        val parameterInformation: ParameterInformation?,

        /**
         * The client supports the `activeParameter` property on
         * `SignatureInformation` literal.
         *
         * @since 3.16.0
         */
        val activeParameterSupport: Boolean?,
    ) {
        @Serializable
        data class ParameterInformation(
            /**
             * The client supports processing label offsets instead of a
             * simple label string.
             *
             * @since 3.14.0
             */
            val labelOffsetSupport: Boolean?,
        )
    }
}

@Serializable
data class TypeDefinitionClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val linkSupport: Boolean? = null,
)

@Serializable
data class ImplementationClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val linkSupport: Boolean? = null,
)

@Serializable
data class ReferenceClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable
data class DocumentHighlightClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable
data class DocumentSymbolClientCapabilities(
    /**
     * Whether document symbol supports dynamic registration.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * Specific capabilities for the `SymbolKind` in the
     * `textDocument/documentSymbol` request.
     */
    val symbolKind: ValueSet<SymbolKind>? = null,

    /**
     * The client supports hierarchical document symbols.
     */
    val hierarchicalDocumentSymbolSupport: Boolean? = null,

    /**
     * The client supports tags on `SymbolInformation`. Tags are supported on
     * `DocumentSymbol` if `hierarchicalDocumentSymbolSupport` is set to true.
     * Clients supporting tags have to handle unknown tags gracefully.
     *
     * @since 3.16.0
     */
    val tagSupport: ValueSet<SymbolTag>? = null,

    /**
     * The client supports an additional label presented in the UI when
     * registering a document symbol provider.
     *
     * @since 3.16.0
     */
    val labelSupport: Boolean? = null,
)

@Serializable
data class CodeActionClientCapabilities(
    /**
     * Whether code action supports dynamic registration.
     */
    val dynamicRegistration: Boolean?,

    /**
     * The client supports code action literals as a valid
     * response of the `textDocument/codeAction` request.
     *
     * @since 3.8.0
     */
    val codeActionLiteralSupport: CodeActionLiteralSupport?,

    /**
     * Whether code action supports the `isPreferred` property.
     *
     * @since 3.15.0
     */
    val isPreferredSupport: Boolean?,

    /**
     * Whether code action supports the `disabled` property.
     *
     * @since 3.16.0
     */
    val disabledSupport: Boolean?,

    /**
     * Whether code action supports the `data` property which is
     * preserved between a `textDocument/codeAction` and a
     * `codeAction/resolve` request.
     *
     * @since 3.16.0
     */
    val dataSupport: Boolean?,


    /**
     * Whether the client supports resolving additional code action
     * properties via a separate `codeAction/resolve` request.
     *
     * @since 3.16.0
     */
    val resolveSupport: Properties<String>?,

    /**
     * Whether the client honors the change annotations in
     * text edits and resource operations returned via the
     * `CodeAction#edit` property by for example presenting
     * the workspace edit in the user interface and asking
     * for confirmation.
     *
     * @since 3.16.0
     */
    val honorsChangeAnnotations: Boolean?,
) {
    @Serializable
    data class CodeActionLiteralSupport(
        val codeActionKind: ValueSet<CodeActionKind>,
    )
}

@Serializable
data class CodeLensClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable
data class DocumentLinkClientCapabilities(
    /**
     * Whether document link supports dynamic registration.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * Whether the client supports the `tooltip` property on `DocumentLink`.
     *
     * @since 3.15.0
     */
    val tooltipSupport: Boolean? = null,
)

@Serializable
data class DocumentColorClientCapabilities(
    val dynamicRegistration: Boolean? = null,
)

@Serializable
data class DocumentFormattingClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable
data class DocumentRangeFormattingClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable
data class DocumentOnTypeFormattingClientCapabilities(
    val dynamicRegistration: Boolean?,
)

@Serializable(with = PrepareSupportDefaultBehaviorSerializer::class)
enum class PrepareSupportDefaultBehavior(val value: Int) {
    Identifier(1),
}

class PrepareSupportDefaultBehaviorSerializer : EnumAsIntSerializer<PrepareSupportDefaultBehavior>(
    serialName = "PrepareSupportDefaultBehavior",
    serialize = PrepareSupportDefaultBehavior::value,
    deserialize = { PrepareSupportDefaultBehavior.entries[it - 1] },
)

@Serializable
data class RenameClientCapabilities(
    /**
     * Whether rename supports dynamic registration.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * Client supports testing for validity of rename operations
     * before execution.
     *
     * @since version 3.12.0
     */
    val prepareSupport: Boolean? = null,

    /**
     * Client supports the default behavior result
     * (`{ defaultBehavior: boolean }`).
     *
     * The value indicates the default behavior used by the
     * client.
     *
     * @since version 3.16.0
     */
    val prepareSupportDefaultBehavior: PrepareSupportDefaultBehavior? = null,

    /**
     * Whether the client honors the change annotations in
     * text edits and resource operations returned via the
     * rename request's workspace edit by for example presenting
     * the workspace edit in the user interface and asking
     * for confirmation.
     *
     * @since 3.16.0
     */
    val honorsChangeAnnotations: Boolean? = null,
)

@Serializable
data class FoldingRangeClientCapabilities(
    /**
     * Whether implementation supports dynamic registration for folding range
     * providers. If this is set to `true` the client supports the new
     * `FoldingRangeRegistrationOptions` return value for the corresponding
     * server capability as well.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * The maximum number of folding ranges that the client prefers to receive
     * per document. The value serves as a hint, servers are free to follow the
     * limit. Unsigned.
     */
    val rangeLimit: Int? = null,

    /**
     * If set, the client signals that it only supports folding complete lines.
     * If set, client will ignore specified `startCharacter` and `endCharacter`
     * properties in a FoldingRange.
     */
    val lineFoldingOnly: Boolean? = null,

    /**
     * Specific options for the folding range kind.
     *
     * @since 3.17.0
     */
    val foldingRangeKind: ValueSet<FoldingRangeKind>? = null,

    /**
     * Specific options for the folding range.
     * @since 3.17.0
     */
    val foldingRange: FoldingRange? = null,
) {
    @Serializable
    data class FoldingRange(
        /**
         * If set, the client signals that it supports setting collapsedText on
         * folding ranges to display custom labels instead of the default text.
         *
         * @since 3.17.0
         */
        val collapsedText: Boolean? = null,
    )
}

@Serializable(with = FoldingRangeKind.Serializer::class)
enum class FoldingRangeKind(val value: String) {
    /**
     * Folding range for a comment
     */
    Comment("comment"),

    /**
     * Folding range for imports or includes
     */
    Imports("imports"),

    /**
     * Folding range for a region (e.g. `#region`)
     */
    Region("region"),

    ;

    class Serializer : EnumAsNameSerializer<FoldingRangeKind>(FoldingRangeKind::class, FoldingRangeKind::value)
}

typealias SelectionRangeClientCapabilities = Unknown
typealias LinkedEditingRangeClientCapabilities = Unknown
typealias CallHierarchyClientCapabilities = Unknown

@Serializable
data class SemanticTokensClientCapabilities(
    /**
     * Whether implementation supports dynamic registration. If this is set to
     * `true` the client supports the new `(TextDocumentRegistrationOptions &
     * StaticRegistrationOptions)` return value for the corresponding server
     * capability as well.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * Which requests the client supports and might send to the server
     * depending on the server's capability. Please note that clients might not
     * show semantic tokens or degrade some of the user experience if a range
     * or full request is advertised by the client but not provided by the
     * server. If for example the client capability `requests.full` and
     * `request.range` are both set to true but the server only provides a
     * range provider the client might not render a minimap correctly or might
     * even decide to not show any semantic tokens at all.
     */
    val requests: Requests,

    /**
     * The token types that the client supports.
     */
    val tokenTypes: List<String>,

    /**
     * The token modifiers that the client supports.
     */
    val tokenModifiers: List<String>,

    /**
     * The formats the clients supports.
     */
    val formats: List<TokenFormat>,

    /**
     * Whether the client supports tokens that can overlap each other.
     */
    val overlappingTokenSupport: Boolean? = null,

    /**
     * Whether the client supports tokens that can span multiple lines.
     */
    val multilineTokenSupport: Boolean? = null,

    /**
     * Whether the client allows the server to actively cancel a
     * semantic token request, e.g. supports returning
     * ErrorCodes.ServerCancelled. If a server does the client
     * needs to retrigger the request.
     *
     * @since 3.17.0
     */
    val serverCancelSupport: Boolean? = null,

    /**
     * Whether the client uses semantic tokens to augment existing
     * syntax tokens. If set to `true` client side created syntax
     * tokens and semantic tokens are both used for colorization. If
     * set to `false` the client only uses the returned semantic tokens
     * for colorization.
     *
     * If the value is `undefined` then the client behavior is not
     * specified.
     *
     * @since 3.17.0
     */
    val augmentsSyntaxTokens: Boolean? = null,
) {
    @Serializable
    data class Requests(
        /**
         * The client will send the `textDocument/semanticTokens/range` request
         * if the server provides a corresponding handler.
         */
        val range: OrBoolean<Unit>? = null,

        /**
         * The client will send the `textDocument/semanticTokens/full` request
         * if the server provides a corresponding handler.
         */
        val full: OrBoolean<Full>? = null,
    ) {
        @Serializable
        data class Full(
            /**
             * The client will send the `textDocument/semanticTokens/full/delta`
             * request if the server provides a corresponding handler.
             */
            val delta: Boolean? = null,
        )
    }
}

@Serializable(with = TokenFormat.Serializer::class)
enum class TokenFormat(val value: String) {
    Relative("relative"),

    ;

    class Serializer : EnumAsNameSerializer<TokenFormat>(TokenFormat::class, TokenFormat::value)
}

typealias MonikerClientCapabilities = Unknown
typealias TypeHierarchyClientCapabilities = Unknown
typealias InlineValueClientCapabilities = Unknown

@Serializable
data class InlayHintClientCapabilities(
    val dynamicRegistration: Boolean? = null,
    val resolveSupport: Properties<String>? = null,
)