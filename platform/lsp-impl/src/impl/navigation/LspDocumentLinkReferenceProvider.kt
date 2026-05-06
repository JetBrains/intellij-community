package com.intellij.platform.lsp.impl.navigation

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.highlighting.LspDocumentLink
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * This class is used to create references and therefore to follow links that were received from an LSP server using the
 * [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentLink) request.
 * These links are rendered as links in the editor by [com.intellij.platform.lsp.impl.highlighting.LspHighlightingApplier].
 */
class LspDocumentLinkReferenceProvider : ImplicitReferenceProvider {
  override fun getImplicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference? {
    val psiFile = element as? PsiFile ?: return null
    if (psiFile.project.isDefault) return null
    val file = psiFile.virtualFile ?: return null
    if (file is VirtualFileWindow) return null

    LspServerManagerImpl.getInstanceImpl(psiFile.project).getServersWithThisFileOpen(file).forEach { lspServer ->
      val documentLinkInfo = lspServer.getDocumentLinkInfos(file).find { it.textRange.contains(offsetInElement) }
      if (documentLinkInfo != null) {
        return LspDocumentLinkSymbolReference(lspServer, psiFile, documentLinkInfo.textRange, documentLinkInfo.highlightingInfo)
      }
    }

    return null
  }
}

private class LspDocumentLinkSymbolReference(
  private val lspServer: LspServerImpl,
  private val psiFile: PsiFile,
  private val textRange: TextRange,
  private val documentLink: LspDocumentLink,
) : PsiSymbolReference {
  override fun getElement(): PsiElement = psiFile

  override fun getRangeInElement(): TextRange = textRange

  override fun resolvesTo(target: Symbol): Boolean = false

  override fun resolveReference(): List<Symbol> {
    documentLink.resolveDocumentLink(lspServer)

    val uri = documentLink.targetUri ?: return emptyList()
    @Suppress("HttpUrlsUsage")
    if ((uri.startsWith("http://") || uri.startsWith("https://"))) {
      return listOf(LspOpenBrowserNavigatableSymbol(uri))
    }

    val targetFileOrDir = lspServer.descriptor.findFileByUri(uri) ?: return emptyList()
    return listOf(LspNavigatableSymbol(targetFileOrDir, null))
  }
}


private class LspOpenBrowserNavigatableSymbol(private val url: @NlsSafe String) : NavigatableSymbol, DocumentationTarget {
  override fun createPointer(): Pointer<LspOpenBrowserNavigatableSymbol> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation = TargetPresentation.builder(url).icon(AllIcons.General.Web).presentation()

  override fun computeDocumentationHint(): @NlsContexts.HintText String = url

  override fun getNavigationTargets(project: Project): List<NavigationTarget> = listOf(
    object : NavigationTarget {
      override fun createPointer(): Pointer<NavigationTarget> = Pointer.hardPointer(this)
      override fun computePresentation(): TargetPresentation = this@LspOpenBrowserNavigatableSymbol.computePresentation()
      override fun navigationRequest(): NavigationRequest? = object : Navigatable {
        override fun canNavigate(): Boolean = true
        override fun navigate(requestFocus: Boolean) = BrowserUtil.browse(url)
      }.navigationRequest()
    }
  )
}
