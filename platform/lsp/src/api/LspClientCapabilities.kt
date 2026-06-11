// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import org.eclipse.lsp4j.CallHierarchyCapabilities
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities
import org.eclipse.lsp4j.CodeLensCapabilities
import org.eclipse.lsp4j.ColorProviderCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemKindCapabilities
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.CompletionItemTagSupportCapabilities
import org.eclipse.lsp4j.CompletionListCapabilities
import org.eclipse.lsp4j.DefinitionCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.DiagnosticsTagSupport
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities
import org.eclipse.lsp4j.DocumentHighlightCapabilities
import org.eclipse.lsp4j.DocumentLinkCapabilities
import org.eclipse.lsp4j.DocumentSymbolCapabilities
import org.eclipse.lsp4j.ExecuteCommandCapabilities
import org.eclipse.lsp4j.FailureHandlingKind
import org.eclipse.lsp4j.FoldingRangeCapabilities
import org.eclipse.lsp4j.FoldingRangeKind
import org.eclipse.lsp4j.FoldingRangeKindSupportCapabilities
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities
import org.eclipse.lsp4j.FormattingCapabilities
import org.eclipse.lsp4j.GeneralClientCapabilities
import org.eclipse.lsp4j.HoverCapabilities
import org.eclipse.lsp4j.InlayHintCapabilities
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.OnTypeFormattingCapabilities
import org.eclipse.lsp4j.ParameterInformationCapabilities
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.RangeFormattingCapabilities
import org.eclipse.lsp4j.ReferencesCapabilities
import org.eclipse.lsp4j.RenameCapabilities
import org.eclipse.lsp4j.ResourceOperationKind
import org.eclipse.lsp4j.SelectionRangeCapabilities
import org.eclipse.lsp4j.SemanticTokensCapabilities
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequestsFull
import org.eclipse.lsp4j.SemanticTokensWorkspaceCapabilities
import org.eclipse.lsp4j.ShowDocumentCapabilities
import org.eclipse.lsp4j.SignatureHelpCapabilities
import org.eclipse.lsp4j.SignatureInformationCapabilities
import org.eclipse.lsp4j.StaleRequestCapabilities
import org.eclipse.lsp4j.SymbolCapabilities
import org.eclipse.lsp4j.SymbolKindCapabilities
import org.eclipse.lsp4j.SymbolTag
import org.eclipse.lsp4j.SymbolTagSupportCapabilities
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TokenFormat
import org.eclipse.lsp4j.TypeDefinitionCapabilities
import org.eclipse.lsp4j.TypeHierarchyCapabilities
import org.eclipse.lsp4j.WindowClientCapabilities
import org.eclipse.lsp4j.WindowShowMessageRequestCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either

internal fun createClientCapabilities(lspCustomization: LspCustomization): ClientCapabilities = ClientCapabilities().apply {
  workspace = WorkspaceClientCapabilities().apply {
    configuration = true
    applyEdit = true
    workspaceFolders = true
    //configuration = true // keep false by default because [getWorkspaceConfiguration] returns null by default
    workspaceEdit = WorkspaceEditCapabilities().apply {
      documentChanges = true
      resourceOperations = listOf(ResourceOperationKind.Create)
      failureHandling = FailureHandlingKind.Abort
      normalizesLineEndings = true
    }
    didChangeWatchedFiles = DidChangeWatchedFilesCapabilities(true).apply {
      relativePatternSupport = true
    }
    executeCommand = ExecuteCommandCapabilities(false)
    semanticTokens = SemanticTokensWorkspaceCapabilities().apply {
      refreshSupport = true
    }
    inlayHint = InlayHintWorkspaceCapabilities().apply {
      refreshSupport = true
    }
    symbol = SymbolCapabilities(true).apply {
      symbolKind = SymbolKindCapabilities().apply {
        valueSet = lspCustomization.symbolKindCustomizer.supportedKinds
      }
    }
  }

  textDocument = TextDocumentClientCapabilities().apply {
    synchronization = SynchronizationCapabilities().apply {
      // `dynamicRegistration = false` because the IDE doesn't check dynamically registered capabilities before sending
      // `didOpen` / `didChange` / `didClose` notifications.
      // It means that the IDE relies on static capability `LspClient.initializeResult.capabilities.textDocumentSync`
      dynamicRegistration = false
      willSave = false
      willSaveWaitUntil = false
      didSave = true
    }
    inlayHint = InlayHintCapabilities(true)
    documentHighlight = DocumentHighlightCapabilities(true)
    diagnostic = DiagnosticCapabilities(true)
    definition = DefinitionCapabilities().apply {
      linkSupport = true
    }
    typeDefinition = TypeDefinitionCapabilities().apply {
      linkSupport = true
    }
    completion = CompletionCapabilities().apply {
      completionItem = CompletionItemCapabilities().apply {
        documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
        deprecatedSupport = true
        tagSupport = CompletionItemTagSupportCapabilities(listOf(CompletionItemTag.Deprecated))
        insertReplaceSupport = true
        labelDetailsSupport = true
        snippetSupport = true
        resolveSupport = CompletionItemResolveSupportCapabilities().apply {
          properties = listOf("documentation")
        }
      }
      completionItemKind = CompletionItemKindCapabilities().apply {
        valueSet = CompletionItemKind.entries
      }
      completionList = CompletionListCapabilities().apply {
        itemDefaults = listOf("commitCharacters", "editRange", "insertTextFormat", "insertTextMode", "data")
      }
    }
    hover = HoverCapabilities().apply {
      contentFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
    }
    signatureHelp = SignatureHelpCapabilities(true).apply {
      signatureInformation = SignatureInformationCapabilities().apply {
        documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
        parameterInformation = ParameterInformationCapabilities().apply {
          labelOffsetSupport = true
        }
        activeParameterSupport = true
      }
    }
    documentSymbol = DocumentSymbolCapabilities().apply {
      dynamicRegistration = true
      symbolKind = SymbolKindCapabilities().apply {
        valueSet = lspCustomization.symbolKindCustomizer.supportedKinds
      }
      hierarchicalDocumentSymbolSupport = true
      tagSupport = SymbolTagSupportCapabilities(listOf(SymbolTag.Deprecated))
      labelSupport = false
    }
    foldingRange = FoldingRangeCapabilities().apply {
      dynamicRegistration = true
      rangeLimit = null
      lineFoldingOnly = false
      foldingRangeKind = FoldingRangeKindSupportCapabilities().apply {
        valueSet = listOf(FoldingRangeKind.Imports, FoldingRangeKind.Region)
      }
      foldingRange = FoldingRangeSupportCapabilities().apply {
        collapsedText = true
      }
    }

    val semanticTokensSupport = lspCustomization.semanticTokensCustomizer
    if (semanticTokensSupport is LspSemanticTokensSupport) {
      semanticTokens = SemanticTokensCapabilities().apply {
        requests = SemanticTokensClientCapabilitiesRequests().apply {
          range = Either.forLeft(false)
          full = Either.forRight(SemanticTokensClientCapabilitiesRequestsFull().apply {
            delta = false
          })
          tokenTypes = semanticTokensSupport.tokenTypes
          tokenModifiers = semanticTokensSupport.tokenModifiers
          formats = listOf(TokenFormat.Relative)
          overlappingTokenSupport = true
          multilineTokenSupport = true
          serverCancelSupport = false
        }
      }
    }

    publishDiagnostics = PublishDiagnosticsCapabilities().apply {
      versionSupport = true
      tagSupport = Either.forRight(DiagnosticsTagSupport(listOf(DiagnosticTag.Unnecessary, DiagnosticTag.Deprecated)))
      dataSupport = true
    }
    codeAction = CodeActionCapabilities().apply {
      codeActionLiteralSupport = CodeActionLiteralSupportCapabilities().apply {
        codeActionKind = CodeActionKindCapabilities(
            listOf(
                CodeActionKind.QuickFix,
                CodeActionKind.Empty,
                CodeActionKind.Source,
                CodeActionKind.Refactor,
            )
        )
      }
      disabledSupport = true
      dataSupport = true
      resolveSupport = CodeActionResolveSupportCapabilities().apply {
        properties = listOf("edit")
      }
    }
    formatting = FormattingCapabilities(true)
    rangeFormatting = RangeFormattingCapabilities(true)
    onTypeFormatting = OnTypeFormattingCapabilities(true)
    references = ReferencesCapabilities(true)
    colorProvider = ColorProviderCapabilities(true)
    documentLink = DocumentLinkCapabilities(true).apply {
      tooltipSupport = true
    }
    callHierarchy = CallHierarchyCapabilities(true)
    typeHierarchy = TypeHierarchyCapabilities(true)
    selectionRange = SelectionRangeCapabilities(true)
    codeLens = CodeLensCapabilities(true)
    rename = RenameCapabilities(true).apply {
      prepareSupport = true
    }
  }

  notebookDocument = null

  window = WindowClientCapabilities().apply {
    showMessage = WindowShowMessageRequestCapabilities()
    showDocument = ShowDocumentCapabilities(true)
    workDoneProgress = true
  }

  general = GeneralClientCapabilities().apply {
    staleRequestSupport = StaleRequestCapabilities().apply {
      isCancel = true
    }
  }

  experimental = null
}