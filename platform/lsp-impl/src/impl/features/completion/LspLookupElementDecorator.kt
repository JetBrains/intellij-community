package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.codeInsight.lookup.SuspendingLookupElementRenderer
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import kotlinx.coroutines.sync.Semaphore
import org.eclipse.lsp4j.CompletionItem

/**
 * This class is needed to control LSP-based [LookupElement]:
 * - [LspCompletionObject] is used as [LookupElement.getObject]
 * - [LspCompletionItemInsertHandler] controls element insertion
 * - [LspCompletionSupport.renderLookupElement] tunes element presentation
 */
internal class LspLookupElementDecorator(
  lspClient: LspClientImpl,
  requestSemaphore: Semaphore,
  lookupElement: LookupElement,
  completionItem: CompletionItem,
) : LookupElementDecorator<LookupElement>(lookupElement) {

  private val lspCompletionObject: LspCompletionObject = LspCompletionObject(lspClient, requestSemaphore, completionItem)

  override fun getObject(): LspCompletionObject = lspCompletionObject

  override fun getDecoratorInsertHandler(): InsertHandler<LookupElement> = LspCompletionItemInsertHandler

  override fun renderElement(presentation: LookupElementPresentation) =
    LspInstantRenderer.renderLookupElement(lspCompletionObject, presentation)

  override fun getExpensiveRenderer(): LookupElementRenderer<LspLookupElementDecorator> = LspExpensiveRenderer

  private object LspInstantRenderer {
    fun renderLookupElement(completionObject: LspCompletionObject, presentation: LookupElementPresentation) {
      val completionSupport = completionObject.lspClient.descriptor.lspCustomization.completionCustomizer as? LspCompletionSupport ?: return
      val completionItem = completionObject.completionItem
      completionSupport.renderLookupElement(completionItem, presentation)
    }
  }

  private object LspExpensiveRenderer : SuspendingLookupElementRenderer<LspLookupElementDecorator>() {

    override suspend fun renderElementSuspending(element: LspLookupElementDecorator, presentation: LookupElementPresentation) {
      val completionObject = element.lspCompletionObject
      val completionSupport = completionObject.lspClient.descriptor.lspCustomization.completionCustomizer as? LspCompletionSupport ?: return
      if (completionSupport.shouldResolveCompletionItem(completionObject.completionItem)) {
        completionObject.resolveCompletionItem()
      }
      completionSupport.expensiveRenderLookupElement(completionObject.completionItem, presentation)
    }
  }
}
