// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.documentSymbol

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.platform.lsp.api.customization.LspSymbolKindCustomizer
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.Processor
import org.eclipse.lsp4j.DocumentSymbol
import javax.swing.Icon

/**
 * Shows the `textDocument/documentSymbol` symbols around the caret as members in the navigation bar.
 * The extension works when "Show members in navigation bar" is on and the client has
 * [breadcrumbsSupport][com.intellij.platform.lsp.api.customization.LspDocumentSymbolCustomizer.breadcrumbsSupport].
 */
internal class LspNavBarModelExtension : AbstractNavBarModelExtension() {
  override fun getPresentableText(`object`: Any?): @NlsSafe String? = (`object` as? LspDocumentSymbolPsiElement)?.name

  override fun getIcon(`object`: Any?): Icon? = (`object` as? LspDocumentSymbolPsiElement)?.getIcon(false)

  override fun getParent(psiElement: PsiElement?): PsiElement? = (psiElement as? LspDocumentSymbolPsiElement)?.parent

  override fun getLeafElement(dataProvider: DataMap): PsiElement? {
    if (!UISettings.getInstance().showMembersInNavigationBar) return null
    val psiFile = dataProvider[CommonDataKeys.PSI_FILE] ?: return null
    val editor = dataProvider[CommonDataKeys.EDITOR] ?: return null
    val file = psiFile.viewProvider.virtualFile
    val lspClient = findBreadcrumbsLspClient(psiFile.project, file) ?: return null
    val documentSymbols = lspClient.requestExecutor.getDocumentSymbolsCaching(file) ?: return null

    val document = editor.document
    val customizer = lspClient.descriptor.lspCustomization.symbolKindCustomizer
    var leaf: LspDocumentSymbolPsiElement? = null
    for (symbol in findSymbolsAroundOffset(document, documentSymbols, editor.caretModel.offset)) {
      leaf = LspDocumentSymbolPsiElement.create(customizer, psiFile, document, leaf ?: psiFile, symbol) ?: break
    }
    return leaf
  }

  override fun processChildren(`object`: Any?, rootElement: Any?, processor: Processor<Any>): Boolean {
    val children = when (`object`) {
      is LspDocumentSymbolPsiElement -> `object`.children()
      is PsiFile -> topLevelSymbols(`object`)
      else -> return true
    }
    return children.all { processor.process(it) }
  }

  override fun normalizeChildren(): Boolean = false

  private fun topLevelSymbols(psiFile: PsiFile): List<LspDocumentSymbolPsiElement> {
    if (!UISettings.getInstance().showMembersInNavigationBar) return emptyList()
    val file = psiFile.viewProvider.virtualFile
    val lspClient = findBreadcrumbsLspClient(psiFile.project, file) ?: return emptyList()
    val documentSymbols = lspClient.requestExecutor.getDocumentSymbolsCaching(file) ?: return emptyList()
    val document = psiFile.viewProvider.document ?: return emptyList()
    val customizer = lspClient.descriptor.lspCustomization.symbolKindCustomizer
    return documentSymbols.mapNotNull { LspDocumentSymbolPsiElement.create(customizer, psiFile, document, psiFile, it) }
  }
}

/**
 * Returns `true` when the navigation bar shows the document symbols as members.
 * Then the editor does not show the LSP breadcrumbs bar.
 * Set the `lsp.navbar.members.hide.editor.breadcrumbs` registry key to `false` to always show the bar.
 */
internal fun isEditorBreadcrumbsReplacedByNavBar(): Boolean {
  val settings = UISettings.getInstance()
  return settings.showNavigationBar &&
         settings.showMembersInNavigationBar &&
         Registry.`is`("lsp.navbar.members.hide.editor.breadcrumbs")
}

/**
 * A [DocumentSymbol] in the navigation bar.
 * The [parent] is the enclosing symbol, or the [psiFile] for a top-level symbol.
 */
private class LspDocumentSymbolPsiElement(
  private val customizer: LspSymbolKindCustomizer,
  private val psiFile: PsiFile,
  private val document: Document,
  private val parent: PsiElement,
  private val symbol: DocumentSymbol,
  private val range: TextRange,
  private val selectionOffset: Int,
) : FakePsiElement() {
  override fun getParent(): PsiElement = parent

  override fun getContainingFile(): PsiFile = psiFile

  override fun getName(): @NlsSafe String = symbol.name

  override fun getIcon(open: Boolean): Icon? = customizer.getIcon(symbol.kind)

  override fun getTextRange(): TextRange = range

  override fun getTextOffset(): Int = selectionOffset

  override fun isValid(): Boolean = psiFile.isValid

  override fun navigationRequest(): NavigationRequest? {
    return NavigationRequests.getInstance().sourceNavigationRequest(project, psiFile.viewProvider.virtualFile, selectionOffset, range)
  }

  fun children(): List<LspDocumentSymbolPsiElement> {
    return symbol.children.orEmpty().mapNotNull { create(customizer, psiFile, document, this, it) }
  }

  companion object {
    /**
     * Returns `null` when the [symbol] range is outside the [document].
     */
    fun create(
      customizer: LspSymbolKindCustomizer,
      psiFile: PsiFile,
      document: Document,
      parent: PsiElement,
      symbol: DocumentSymbol,
    ): LspDocumentSymbolPsiElement? {
      val range = getRangeInDocument(document, symbol.range) ?: return null
      val selectionOffset = getOffsetInDocument(document, symbol.selectionRange.start) ?: range.startOffset
      return LspDocumentSymbolPsiElement(customizer, psiFile, document, parent, symbol, range, selectionOffset)
    }
  }
}
