@file:Suppress("unused")

package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.CallHierarchyOptions
import com.jetbrains.lsp.protocol.CodeActionRegistrationOptions
import com.jetbrains.lsp.protocol.CodeLensRegistrationOptions
import com.jetbrains.lsp.protocol.CompletionRegistrationOptions
import com.jetbrains.lsp.protocol.DeclarationRegistrationOptions
import com.jetbrains.lsp.protocol.DefinitionParams
import com.jetbrains.lsp.protocol.DefinitionRegistrationOptions
import com.jetbrains.lsp.protocol.DefinitionRequestType
import com.jetbrains.lsp.protocol.DiagnosticOptions
import com.jetbrains.lsp.protocol.DocumentColorRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentFormattingRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentHighlightOptions
import com.jetbrains.lsp.protocol.DocumentLinkOptions
import com.jetbrains.lsp.protocol.DocumentOnTypeFormattingOptions
import com.jetbrains.lsp.protocol.DocumentRangeFormattingRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentSymbol
import com.jetbrains.lsp.protocol.DocumentSymbolParams
import com.jetbrains.lsp.protocol.DocumentSymbolRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentSymbolRequest
import com.jetbrains.lsp.protocol.ExecuteCommandOptions
import com.jetbrains.lsp.protocol.FoldingRangeOptions
import com.jetbrains.lsp.protocol.HoverRegistrationOptions
import com.jetbrains.lsp.protocol.Implementation
import com.jetbrains.lsp.protocol.ImplementationParams
import com.jetbrains.lsp.protocol.ImplementationRegistrationOptions
import com.jetbrains.lsp.protocol.InlayHintRegistrationOptions
import com.jetbrains.lsp.protocol.InlineValueOptions
import com.jetbrains.lsp.protocol.LinkedEditingRangeOptions
import com.jetbrains.lsp.protocol.Location
import com.jetbrains.lsp.protocol.Locations
import com.jetbrains.lsp.protocol.MonikerOptions
import com.jetbrains.lsp.protocol.NotebookDocumentSyncOptions
import com.jetbrains.lsp.protocol.OrBoolean
import com.jetbrains.lsp.protocol.ReferenceParams
import com.jetbrains.lsp.protocol.ReferenceRegistrationOptions
import com.jetbrains.lsp.protocol.ReferenceRequestType
import com.jetbrains.lsp.protocol.RenameRegistrationOptions
import com.jetbrains.lsp.protocol.SelectionRangeOptions
import com.jetbrains.lsp.protocol.SemanticTokensRegistrationOptions
import com.jetbrains.lsp.protocol.SignatureHelpRegistrationOptions
import com.jetbrains.lsp.protocol.TextDocumentSync
import com.jetbrains.lsp.protocol.TypeDefinitionRegistrationOptions
import com.jetbrains.lsp.protocol.TypeHierarchyOptions
import com.jetbrains.lsp.protocol.WorkspaceSymbol
import com.jetbrains.lsp.protocol.WorkspaceSymbolParams
import com.jetbrains.lsp.protocol.WorkspaceSymbolRegistrationOptions
import com.jetbrains.lsp.protocol.WorkspaceSymbolRequests
import kotlinx.coroutines.CoroutineScope

/**
 * File contains DSL functions for every ServerCapabilities's field
 */

fun LspServerCapabilitiesBuilder.textDocumentSync(
    value: TextDocumentSync,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(textDocumentSync = value) }, register = register)

fun LspServerCapabilitiesBuilder.notebookDocumentSync(
    value: NotebookDocumentSyncOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(notebookDocumentSync = value) }, register = register)

fun LspServerCapabilitiesBuilder.completionProvider(
    value: CompletionRegistrationOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(completionProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.hoverProvider(
    value: OrBoolean<HoverRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(hoverProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.signatureHelpProvider(
    value: SignatureHelpRegistrationOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(signatureHelpProvider = value ) }, register = register)

fun LspServerCapabilitiesBuilder.declarationProvider(
    value: OrBoolean<DeclarationRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(declarationProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.definitionProvider(
    value: OrBoolean<DefinitionRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(DefinitionParams) -> List<Location>,
): Unit = capability(update = { copy(definitionProvider = value) }, register = {
    request(
        DefinitionRequestType,
        handler
    )
})

fun LspServerCapabilitiesBuilder.typeDefinitionProvider(
    value: OrBoolean<TypeDefinitionRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(typeDefinitionProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.implementationProvider(
    value: OrBoolean<ImplementationRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(ImplementationParams) -> Locations?,
): Unit = capability(update = { copy(implementationProvider = value) }, register = {
    request(
        Implementation.ImplementationRequest,
        handler
    )
})

fun LspServerCapabilitiesBuilder.referencesProvider(
    value: OrBoolean<ReferenceRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(ReferenceParams) -> List<Location>?,
): Unit =
    capability(update = { copy(referencesProvider = value) }, register = {
        request(
            ReferenceRequestType,
            handler
        )
    })

fun LspServerCapabilitiesBuilder.documentHighlightProvider(
    value: OrBoolean<DocumentHighlightOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentHighlightProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentSymbolProvider(
    value: OrBoolean<DocumentSymbolRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(DocumentSymbolParams) -> List<DocumentSymbol>,
): Unit = capability(update = { copy(documentSymbolProvider = value) }, register = {
    request(
        DocumentSymbolRequest,
        handler
    )
})

fun LspServerCapabilitiesBuilder.codeActionProvider(
    value: OrBoolean<CodeActionRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(codeActionProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.codeLensProvider(
    value: CodeLensRegistrationOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(codeLensProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentLinkProvider(
    value: DocumentLinkOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentLinkProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.colorProvider(
    value: OrBoolean<DocumentColorRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(colorProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentFormattingProvider(
    value: OrBoolean<DocumentFormattingRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentFormattingProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentRangeFormattingProvider(
    value: OrBoolean<DocumentRangeFormattingRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentRangeFormattingProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentOnTypeFormattingProvider(
    value: DocumentOnTypeFormattingOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentOnTypeFormattingProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.renameProvider(
    value: OrBoolean<RenameRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(renameProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.foldingRangeProvider(
    value: OrBoolean<FoldingRangeOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(foldingRangeProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.executeCommandProvider(
    value: ExecuteCommandOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(executeCommandProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.selectionRangeProvider(
    value: OrBoolean<SelectionRangeOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(selectionRangeProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.linkedEditingRangeProvider(
    value: OrBoolean<LinkedEditingRangeOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(linkedEditingRangeProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.callHierarchyProvider(
    value: OrBoolean<CallHierarchyOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(callHierarchyProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.semanticTokensProvider(
    value: SemanticTokensRegistrationOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(semanticTokensProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.monikerProvider(
    value: OrBoolean<MonikerOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(monikerProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.typeHierarchyProvider(
    value: OrBoolean<TypeHierarchyOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(typeHierarchyProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.inlineValueProvider(
    value: OrBoolean<InlineValueOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(inlineValueProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.inlayHintProvider(
    value: OrBoolean<InlayHintRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(inlayHintProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.diagnosticProvider(
    value: DiagnosticOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(diagnosticProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.workspaceSymbolProvider(
    value: OrBoolean<WorkspaceSymbolRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(WorkspaceSymbolParams) -> List<WorkspaceSymbol>,
): Unit = capability(update = { copy(workspaceSymbolProvider = value) }, register = {
    request(
        WorkspaceSymbolRequests.WorkspaceSymbolRequest,
        handler
    )
})
