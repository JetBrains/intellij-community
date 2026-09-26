package com.jetbrains.lsp.test

import com.jetbrains.lsp.implementation.LspServerCapabilities
import com.jetbrains.lsp.implementation.LspServerCapabilitiesBuilder
import com.jetbrains.lsp.implementation.callHierarchyProvider
import com.jetbrains.lsp.implementation.codeActionProvider
import com.jetbrains.lsp.implementation.codeLensProvider
import com.jetbrains.lsp.implementation.completionProvider
import com.jetbrains.lsp.implementation.diagnosticProvider
import com.jetbrains.lsp.implementation.documentFormattingProvider
import com.jetbrains.lsp.implementation.documentRangeFormattingProvider
import com.jetbrains.lsp.implementation.executeCommandProvider
import com.jetbrains.lsp.implementation.foldingRangeProvider
import com.jetbrains.lsp.implementation.hoverProvider
import com.jetbrains.lsp.implementation.inlayHintProvider
import com.jetbrains.lsp.implementation.lspServerCapabilities
import com.jetbrains.lsp.implementation.renameProvider
import com.jetbrains.lsp.implementation.semanticTokensProvider
import com.jetbrains.lsp.implementation.signatureHelpProvider
import com.jetbrains.lsp.implementation.typeDefinitionProvider
import com.jetbrains.lsp.implementation.typeHierarchyProvider
import com.jetbrains.lsp.implementation.willRenameFiles
import com.jetbrains.lsp.protocol.CallHierarchyRequests
import com.jetbrains.lsp.protocol.CodeActions
import com.jetbrains.lsp.protocol.CodeLenses
import com.jetbrains.lsp.protocol.Commands
import com.jetbrains.lsp.protocol.CompletionRequestType
import com.jetbrains.lsp.protocol.CompletionResolveRequestType
import com.jetbrains.lsp.protocol.DiagnosticOptions
import com.jetbrains.lsp.protocol.Diagnostics
import com.jetbrains.lsp.protocol.ExecuteCommandOptions
import com.jetbrains.lsp.protocol.FileOperationFilter
import com.jetbrains.lsp.protocol.FileOperationPattern
import com.jetbrains.lsp.protocol.FileOperationRegistrationOptions
import com.jetbrains.lsp.protocol.FoldingRangeRequestType
import com.jetbrains.lsp.protocol.FormattingRequestType
import com.jetbrains.lsp.protocol.HoverRequestType
import com.jetbrains.lsp.protocol.InlayHints
import com.jetbrains.lsp.protocol.OrBoolean
import com.jetbrains.lsp.protocol.PrepareRenameRequestType
import com.jetbrains.lsp.protocol.RangeFormattingRequestType
import com.jetbrains.lsp.protocol.RenameRegistrationOptions
import com.jetbrains.lsp.protocol.RenameRequestType
import com.jetbrains.lsp.protocol.SemanticTokensLegend
import com.jetbrains.lsp.protocol.SemanticTokensRequests
import com.jetbrains.lsp.protocol.ServerCapabilities
import com.jetbrains.lsp.protocol.ServerWorkspaceCapabilities
import com.jetbrains.lsp.protocol.SignatureHelpRegistrationOptions
import com.jetbrains.lsp.protocol.SignatureHelpRequest
import com.jetbrains.lsp.protocol.TypeDefinitionRequestType
import com.jetbrains.lsp.protocol.TypeHierarchyRequests
import com.jetbrains.lsp.protocol.Workspace
import com.jetbrains.lsp.protocol.WorkspaceFoldersServerCapabilities
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The DSL must never advertise an option flag whose handler is missing, nor register a handler for a request the
 * capability does not claim. The handlers below are never invoked - only registered - so they can all fail loudly.
 */
class LspServerCapabilitiesDslTest {

    private val emptyLegend = SemanticTokensLegend(tokenTypes = emptyList(), tokenModifiers = emptyList())

    private fun caps(build: LspServerCapabilitiesBuilder.() -> Unit): LspServerCapabilities =
        lspServerCapabilities(build = build)

    @Test
    fun `completion resolve is advertised only when its handler is given`() {
        val without = caps { completionProvider(handler = { error("not called") }) }
        assertEquals(false, without.serverCapabilities.completionProvider?.resolveProvider)
        assertNull(without.lspHandlers.requestHandler(CompletionResolveRequestType.method))
        assertNotNull(without.lspHandlers.requestHandler(CompletionRequestType.method))

        val with = caps {
            completionProvider(
                resolveHandler = { error("not called") },
                handler = { error("not called") },
            )
        }
        assertEquals(true, with.serverCapabilities.completionProvider?.resolveProvider)
        assertNotNull(with.lspHandlers.requestHandler(CompletionResolveRequestType.method))
    }

    @Test
    fun `code action resolve is advertised only when its handler is given`() {
        val without = caps { codeActionProvider(codeActionKinds = emptyList(), handler = { error("not called") }) }
        assertEquals(
            false,
            without.serverCapabilities.codeActionProvider.orNull()?.resolveProvider,
        )
        assertNull(without.lspHandlers.requestHandler(CodeActions.ResolveCodeAction.method))
        assertNotNull(without.lspHandlers.requestHandler(CodeActions.CodeActionRequest.method))

        val with = caps {
            codeActionProvider(
                codeActionKinds = emptyList(),
                resolveHandler = { error("not called") },
                handler = { error("not called") },
            )
        }
        assertEquals(true, with.serverCapabilities.codeActionProvider.orNull()?.resolveProvider)
        assertNotNull(with.lspHandlers.requestHandler(CodeActions.ResolveCodeAction.method))
    }

    @Test
    fun `code lens resolve is advertised only when its handler is given`() {
        val without = caps { codeLensProvider(handler = { error("not called") }) }
        assertEquals(false, without.serverCapabilities.codeLensProvider?.resolveProvider)
        assertNull(without.lspHandlers.requestHandler(CodeLenses.ResolveCodeLens.method))
        assertNotNull(without.lspHandlers.requestHandler(CodeLenses.CodeLensRequestType.method))

        val with = caps {
            codeLensProvider(resolveHandler = { error("not called") }, handler = { error("not called") })
        }
        assertEquals(true, with.serverCapabilities.codeLensProvider?.resolveProvider)
        assertNotNull(with.lspHandlers.requestHandler(CodeLenses.ResolveCodeLens.method))
    }

    @Test
    fun `inlay hint resolve is advertised only when its handler is given`() {
        val without = caps { inlayHintProvider(handler = { error("not called") }) }
        assertEquals(false, without.serverCapabilities.inlayHintProvider.orNull()?.resolveProvider)
        assertNull(without.lspHandlers.requestHandler(InlayHints.ResolveInlayHint.method))
        assertNotNull(without.lspHandlers.requestHandler(InlayHints.InlayHintRequestType.method))

        val with = caps {
            inlayHintProvider(resolveHandler = { error("not called") }, handler = { error("not called") })
        }
        assertEquals(true, with.serverCapabilities.inlayHintProvider.orNull()?.resolveProvider)
        assertNotNull(with.lspHandlers.requestHandler(InlayHints.ResolveInlayHint.method))
    }

    @Test
    fun `rename prepare is advertised only when its handler is given`() {
        val without = caps { renameProvider(handler = { error("not called") }) }
        assertEquals(OrBoolean<RenameRegistrationOptions>(true), without.serverCapabilities.renameProvider)
        assertNull(without.lspHandlers.requestHandler(PrepareRenameRequestType.method))
        assertNotNull(without.lspHandlers.requestHandler(RenameRequestType.method))

        val with = caps {
            renameProvider(prepareHandler = { error("not called") }, handler = { error("not called") })
        }
        assertEquals(
            OrBoolean.of(RenameRegistrationOptions(prepareProvider = true)),
            with.serverCapabilities.renameProvider,
        )
        assertNotNull(with.lspHandlers.requestHandler(PrepareRenameRequestType.method))
    }

    @Test
    fun `semantic tokens advertises only the halves it can serve`() {
        val fullOnly = caps { semanticTokensProvider(emptyLegend, fullHandler = { error("not called") }) }
        assertEquals(OrBoolean(true), fullOnly.serverCapabilities.semanticTokensProvider?.full)
        assertNull(fullOnly.serverCapabilities.semanticTokensProvider?.range)
        assertNotNull(fullOnly.lspHandlers.requestHandler(SemanticTokensRequests.SemanticTokensFullRequest.method))
        assertNull(fullOnly.lspHandlers.requestHandler(SemanticTokensRequests.SemanticTokensRangeRequest.method))

        val rangeOnly = caps { semanticTokensProvider(emptyLegend, rangeHandler = { error("not called") }) }
        assertEquals(OrBoolean(true), rangeOnly.serverCapabilities.semanticTokensProvider?.range)
        assertNull(rangeOnly.serverCapabilities.semanticTokensProvider?.full)
        assertNotNull(rangeOnly.lspHandlers.requestHandler(SemanticTokensRequests.SemanticTokensRangeRequest.method))
        assertNull(rangeOnly.lspHandlers.requestHandler(SemanticTokensRequests.SemanticTokensFullRequest.method))

        val both = caps {
            semanticTokensProvider(
                emptyLegend,
                fullHandler = { error("not called") },
                rangeHandler = { error("not called") },
            )
        }
        assertEquals(OrBoolean(true), both.serverCapabilities.semanticTokensProvider?.full)
        assertEquals(OrBoolean(true), both.serverCapabilities.semanticTokensProvider?.range)
    }

    @Test
    fun `semantic tokens without any handler is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            caps { semanticTokensProvider(emptyLegend) }
        }
    }

    @Test
    fun `type hierarchy registers all three of its requests`() {
        val result = caps {
            typeHierarchyProvider(
                supertypesHandler = { error("not called") },
                subtypesHandler = { error("not called") },
                prepareHandler = { error("not called") },
            )
        }
        assertEquals(OrBoolean(true), result.serverCapabilities.typeHierarchyProvider)
        for (request in listOf(
            TypeHierarchyRequests.PrepareTypeHierarchyRequestType,
            TypeHierarchyRequests.SupertypesRequestType,
            TypeHierarchyRequests.SubtypesRequestType,
        )) {
            assertNotNull(result.lspHandlers.requestHandler(request.method), request.method)
        }
    }

    @Test
    fun `call hierarchy registers all three of its requests`() {
        val result = caps {
            callHierarchyProvider(
                incomingCallsHandler = { error("not called") },
                outgoingCallsHandler = { error("not called") },
                prepareHandler = { error("not called") },
            )
        }
        assertEquals(OrBoolean(true), result.serverCapabilities.callHierarchyProvider)
        for (request in listOf(
            CallHierarchyRequests.PrepareCallHierarchyRequestType,
            CallHierarchyRequests.IncomingCallsRequestType,
            CallHierarchyRequests.OutgoingCallsRequestType,
        )) {
            assertNotNull(result.lspHandlers.requestHandler(request.method), request.method)
        }
    }

    @Test
    fun `each single-request capability is wired to its own method`() {
        assertHandled(HoverRequestType.method) { hoverProvider(handler = { error("not called") }) }
        assertHandled(TypeDefinitionRequestType.method) { typeDefinitionProvider(handler = { error("not called") }) }
        assertHandled(FoldingRangeRequestType.method) { foldingRangeProvider(handler = { error("not called") }) }
        assertHandled(FormattingRequestType.method) { documentFormattingProvider(handler = { error("not called") }) }
        assertHandled(RangeFormattingRequestType.method) {
            documentRangeFormattingProvider(handler = { error("not called") })
        }
        assertHandled(SignatureHelpRequest.method) {
            signatureHelpProvider(
                SignatureHelpRegistrationOptions(triggerCharacters = null, retriggerCharacters = null),
                handler = { error("not called") },
            )
        }
        assertHandled(Commands.ExecuteCommand.method) {
            executeCommandProvider(ExecuteCommandOptions(commands = emptyList()), handler = { error("not called") })
        }
        assertHandled(Diagnostics.DocumentDiagnosticRequestType.method) {
            diagnosticProvider(
                DiagnosticOptions(identifier = null, interFileDependencies = true, workspaceDiagnostics = false),
                handler = { error("not called") },
            )
        }
    }

    @Test
    fun `willRenameFiles keeps the workspace capabilities already set`() {
        val workspaceFolders = WorkspaceFoldersServerCapabilities(
            supported = true,
            changeNotifications = JsonPrimitive(true),
        )
        val filters = listOf(FileOperationFilter(pattern = FileOperationPattern("**/*")))
        val result = lspServerCapabilities(
            initial = ServerCapabilities(workspace = ServerWorkspaceCapabilities(workspaceFolders = workspaceFolders)),
        ) {
            willRenameFiles(filters = filters, handler = { error("not called") })
        }

        val workspace = assertNotNull(result.serverCapabilities.workspace)
        assertEquals(workspaceFolders, workspace.workspaceFolders)
        assertEquals(FileOperationRegistrationOptions(filters = filters), workspace.fileOperations?.willRename)
        assertNotNull(result.lspHandlers.requestHandler(Workspace.WillRenameFiles.method))
    }

    private fun assertHandled(method: String, build: LspServerCapabilitiesBuilder.() -> Unit) {
        assertNotNull(caps(build).lspHandlers.requestHandler(method), method)
    }
}

private fun <T> OrBoolean<T>?.orNull(): T? = (this as? OrBoolean.Value<T>)?.value
