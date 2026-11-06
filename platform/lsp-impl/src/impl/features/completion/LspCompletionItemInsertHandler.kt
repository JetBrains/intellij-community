package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspCommandsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.util.Lsp4jService
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.platform.lsp.util.getLsp4jPosition
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.InsertTextFormat

internal object LspCompletionItemInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, lookupElement: LookupElement) {
    handleAdditionalTextEdits(context, lookupElement)
    (lookupElement as? LookupElementDecorator<*>)?.delegate?.handleInsert(context)
    handleSnippetFormat(context, lookupElement)
    lookupElement.lsp4jCompletionItem?.command?.let { command ->
      val lspClient = lookupElement.lspClient
      val lspCommandsSupport = lspClient?.descriptor?.lspCustomization?.commandsCustomizer as? LspCommandsSupport
      lspCommandsSupport?.executeCommand(lspClient, context.file.virtualFile, command)
    }
  }

  private fun handleAdditionalTextEdits(context: InsertionContext, lookupElement: LookupElement) {
    val edits = lookupElement.lsp4jCompletionItem?.additionalTextEdits ?: return
    val mainEditRange = lookupElement.lsp4jCompletionItem?.textEdit?.map({ it.range }, { it.insert })
    val startPosition = mainEditRange?.start ?: getLsp4jPosition(context.document, context.startOffset)

    // Full handling of additionalTextEdits is complicated
    // because we need to agree offsets returned by the language servers with the built-in IJ Platform completion.
    // For now, it's straightforward to apply offsets that come before the main textEdit offset, since they are not affected by the above.
    edits.filter { edit ->
      edit.range.start.line < startPosition.line ||
      (edit.range.start.line == startPosition.line && edit.range.start.character <= startPosition.character)
    }.let {
      applyTextEdits(context.document, it)
    }
  }

  private fun handleSnippetFormat(context: InsertionContext, lookupElement: LookupElement) {
    if (lookupElement.lsp4jCompletionItem?.insertTextFormat != InsertTextFormat.Snippet) return

    Lsp4jService.getInstance().startSnippet(context.editor, TextRange(context.startOffset, context.tailOffset), lookupElement.lookupString)
  }
}

private val LookupElement.lsp4jCompletionItem: CompletionItem?
  get() = (this.`object` as? LspCompletionObject)?.completionItem
          ?: this.`object` as? CompletionItem // for SnippetParsingTest

private val LookupElement.lspClient: LspClientImpl?
  get() = (this.`object` as? LspCompletionObject)?.lspClient
