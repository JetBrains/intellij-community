package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.completion.BaseCompletionService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProcessEx
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getLsp4jPosition
import kotlinx.coroutines.sync.Semaphore
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.jetbrains.annotations.ApiStatus

internal class LspCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val psiFile = parameters.originalFile
    val project = psiFile.getProject()
    if (project.isDefault) return // Welcome screen -> some project wizards may have completion in its fields

    val file = psiFile.getOriginalFile().getVirtualFile()?.let { (it as? VirtualFileWindow)?.delegate ?: it } ?: return
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return
    val offset = InjectedLanguageManager.getInstance(project).injectedToHost(psiFile, parameters.offset)

    for (client in LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file)) {
      ProgressManager.checkCanceled()

      client.serverCapabilities?.completionProvider ?: continue
      val completionSupport = client.descriptor.lspCustomization.completionCustomizer as? LspCompletionSupport ?: continue
      if (!completionSupport.shouldRunCodeCompletion(parameters)) continue

      // LSP server wants to handle code completion, so disable less smart WordCompletionContributor for the current completion session
      (parameters.process as CompletionProcessEx).putUserData(BaseCompletionService.FORBID_WORD_COMPLETION, true)

      val completionList = client.requestExecutor.getCompletionList(file, offset, parameters.isAutoPopup) ?: continue

      if (completionList.isIncomplete) {
        result.restartCompletionOnAnyPrefixChange()
      }

      if (completionList.items.isEmpty()) continue

      val defaultPrefix = result.prefixMatcher.prefix
      val prefix = completionSupport.getCompletionPrefix(parameters, defaultPrefix)
      val originalResultSet = if (prefix != defaultPrefix) result.withPrefixMatcher(prefix) else result

      processCompletionItemsImpl(client, document, offset, originalResultSet, completionList.items) {
        completionSupport.createLookupElement(parameters, it)
      }
    }
  }
}

/**
 * @param createLookupElement converts an instance of [T] to the [LookupElement], or returns `null` if the completion item is not needed.
 * Created `LookupElement` MUST return the corresponding [org.eclipse.lsp4j.CompletionItem] object from its [LookupElement.getObject].
 */
@ApiStatus.Internal
fun <T> processCompletionItemsImpl(
  lspClient: LspClientImpl,
  document: Document,
  offset: Int,
  originalResultSet: CompletionResultSet,
  items: List<T>,
  createLookupElement: (item: T) -> LookupElement?,
) {
  // Different CompletionItems may have different insert ranges, which means the need to use different prefix matchers.
  var currentResultSet: CompletionResultSet = originalResultSet
  var currentInsertRange: Range? = null
  val completionPosition = getLsp4jPosition(document, offset)

  // We should not overflow the LSP server with resolveCompletionItem commands, because it blocks
  // other commands and may lead to unresponsiveness of the server if completion item resolve takes a lot of time.
  // Not every LSP supports command cancellation properly.
  val requestSemaphore = Semaphore(2)
  for (item in items) {
    val lookupElement = createLookupElement(item) ?: continue
    val completionItem = lookupElement.`object` as? CompletionItem ?: continue
    val insertRange = getInsertRange(completionItem)
    if (insertRange != currentInsertRange) {
      currentInsertRange = insertRange
      currentResultSet = when (insertRange) {
        null -> originalResultSet
        else -> getCompletionPrefix(document, completionPosition, insertRange)?.let { originalResultSet.withPrefixMatcher(it) }
                ?: originalResultSet
      }
    }

    currentResultSet.addElement(LspLookupElementDecorator(lspClient, requestSemaphore, lookupElement, completionItem))
  }
}

private fun getInsertRange(item: CompletionItem): Range? = item.textEdit?.map({ it.range }, { it.insert })

private fun getCompletionPrefix(document: Document, completionPosition: Position, insertRange: Range): String? {
  if (insertRange.start.line != insertRange.end.line ||
      insertRange.start.line != completionPosition.line ||
      insertRange.start.character > completionPosition.character ||
      insertRange.end.character < completionPosition.character) {
    // The data from the server doesn't follow the specification requirements.
    return null
  }

  val lineStartOffset = document.getLineStartOffset(completionPosition.line)
  val start = lineStartOffset + insertRange.start.character
  val end = lineStartOffset + completionPosition.character
  return document.charsSequence.subSequence(start, end).toString()
}
