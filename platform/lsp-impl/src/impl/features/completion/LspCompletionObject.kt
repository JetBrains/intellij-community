package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.documentation.createLspDocumentationData
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * The purpose of this class is to
 * [resolve](https://microsoft.github.io/language-server-protocol/specification/#completionItem_resolve)
 * the [initialCompletionItem]
 * (used by [LspExpensiveRenderer][LspLookupElementDecorator.LspExpensiveRenderer])
 * and to provide documentation for LSP-based completion items.
 *
 * Objects of this class are returned from the [LookupElement.getObject] function, thanks to [LspLookupElementDecorator].
 * Implementing [Pointer], [Symbol], and [DocumentationTarget] is needed so that
 * [IdeDocumentationTargetProviderImpl.documentationTargets][com.intellij.lang.documentation.ide.impl.IdeDocumentationTargetProviderImpl.documentationTargets]
 * could select this object to be a [DocumentationTarget] for the corresponding [LookupElement].
 */
internal class LspCompletionObject(
  val lspClient: LspClientImpl,
  private val requestSemaphore: Semaphore,
  private val initialCompletionItem: CompletionItem,
) : Pointer<LspCompletionObject>, Symbol, DocumentationTarget {

  private var resolvedCompletionItem: CompletionItem? = null

  val completionItem: CompletionItem
    get() = resolvedCompletionItem ?: initialCompletionItem

  /**
   * This function might send the `completionItem/resolve` request to the LSP server and await for the response suspending.
   * Cancelling the coroutine while awaiting the command triggers cancel notification on LSP. Actual command cancellation support
   * depends on the LSP implementation.
   */
  internal suspend fun resolveCompletionItem() {
    if (resolvedCompletionItem != null) return

    if (lspClient.serverCapabilities?.completionProvider?.resolveProvider != true) {
      resolvedCompletionItem = initialCompletionItem
      return
    }
    requestSemaphore.withPermit {
      val rawResolvedCompletionItem = lspClient.sendRequest {
        it.textDocumentService.resolveCompletionItem(initialCompletionItem)
      }
      if (rawResolvedCompletionItem != null) {
        // LSP spec prohibits using a newly received label
        // TODO revisit all the rest properties
        rawResolvedCompletionItem.label = initialCompletionItem.label
      }
      resolvedCompletionItem = rawResolvedCompletionItem ?: initialCompletionItem
    }
  }

  override fun computePresentation(): TargetPresentation = TargetPresentation.builder(completionItem.label).presentation()

  @RequiresReadLock
  override fun computeDocumentation(): DocumentationResult? {
    resolvedCompletionItem?.documentation?.let { return lsp4jDocsToDocumentation(it) }
    initialCompletionItem.documentation?.let { return lsp4jDocsToDocumentation(it) }

    if (resolvedCompletionItem != null) return null
    if (lspClient.serverCapabilities?.completionProvider?.resolveProvider != true) return null

    return DocumentationResult.asyncDocumentation {
      resolvedCompletionItem = lspClient.sendRequest { it.textDocumentService.resolveCompletionItem(initialCompletionItem) }
                               ?: initialCompletionItem
      resolvedCompletionItem?.documentation?.let { readAction { lsp4jDocsToDocumentation(it) } }
    }
  }

  @RequiresReadLock
  private fun lsp4jDocsToDocumentation(lsp4jDocs: Either<String, MarkupContent>): DocumentationResult.Documentation? {
    val markupContent = when {
      lsp4jDocs.isLeft && lsp4jDocs.left!!.isNotEmpty() -> MarkupContent(MarkupKind.PLAINTEXT, lsp4jDocs.left!!)
      lsp4jDocs.isRight && lsp4jDocs.right!!.value.isNotEmpty() -> lsp4jDocs.right
      else -> return null
    }

    return createLspDocumentationData(markupContent).toQuickDocHtml(lspClient.project)
  }

  override fun createPointer(): Pointer<LspCompletionObject> = Pointer.hardPointer(this)
  override fun dereference(): LspCompletionObject = this
}
