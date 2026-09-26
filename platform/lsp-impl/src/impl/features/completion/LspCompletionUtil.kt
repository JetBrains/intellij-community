package com.intellij.platform.lsp.impl.features.completion

import com.intellij.openapi.editor.Document
import com.intellij.platform.lsp.impl.LspClientImpl
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemDefaults
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionTriggerKind
import org.eclipse.lsp4j.InsertReplaceEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.Objects

internal fun createCompletionContext(
  lspClient: LspClientImpl,
  document: Document,
  offset: Int,
  isAutoPopup: Boolean,
): CompletionContext = when {
  isAutoPopup -> getTypedChar(document, offset)
                   ?.takeIf { isCompletionTriggerCharacter(lspClient, it) }
                 ?.let { CompletionContext(CompletionTriggerKind.TriggerCharacter, it) }
               ?: CompletionContext(CompletionTriggerKind.Invoked)
  else -> CompletionContext(CompletionTriggerKind.Invoked)
}

internal fun Either<List<CompletionItem>, CompletionList>.toCompletionList(): CompletionList = map(
  { CompletionList(false, it) },
  { completionList ->
    completionList.itemDefaults?.let { itemDefaults ->
      completionList.items.forEach { item -> applyItemDefaults(item, itemDefaults) }
    }
    completionList
  }
)

private fun getTypedChar(document: Document, offset: Int): String? =
  if (offset > 0 && offset <= document.textLength) document.charsSequence[offset - 1].toString() else null

private fun isCompletionTriggerCharacter(lspClient: LspClientImpl, typedChar: String): Boolean =
  lspClient.serverCapabilities?.completionProvider?.triggerCharacters?.contains(typedChar) == true

private fun applyItemDefaults(item: CompletionItem, itemDefaults: CompletionItemDefaults) {
  if (item.commitCharacters == null) item.commitCharacters = itemDefaults.commitCharacters
  val defaultEditRange = itemDefaults.editRange
  if (item.textEdit == null && defaultEditRange != null) {
    val textEditText = Objects.requireNonNullElse(item.textEditText, item.label)
    if (defaultEditRange.isLeft) {
      item.textEdit = Either.forLeft(TextEdit(defaultEditRange.left!!, textEditText))
    }
    else {
      val insertReplaceRange = defaultEditRange.right!!
      item.textEdit = Either.forRight(InsertReplaceEdit(textEditText, insertReplaceRange.insert, insertReplaceRange.replace))
    }
  }
  if (item.insertTextFormat == null) item.insertTextFormat = itemDefaults.insertTextFormat
  if (item.insertTextMode == null) item.insertTextMode = itemDefaults.insertTextMode
  if (item.data == null) item.data = itemDefaults.data
}
