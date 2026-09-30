package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.highlighting.LspDocumentLink
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.IconUtil

/**
 * This class is used to create references and therefore to follow links that were received from an LSP server using the
 * [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentLink) request.
 * These links are rendered as links in the editor by [com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier].
 */
internal class LspDocumentLinkReferenceProvider : ImplicitReferenceProvider {
  override fun getImplicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference? {
    val psiFile = element as? PsiFile ?: return null
    if (psiFile.project.isDefault) return null
    val file = psiFile.virtualFile ?: return null
    if (file is VirtualFileWindow) return null

    LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsWithThisFileOpen(file).forEach { lspClient ->
      val documentLinkInfo = lspClient.getDocumentLinkInfos(file).find { it.textRange.contains(offsetInElement) }
      if (documentLinkInfo != null) {
        return LspDocumentLinkSymbolReference(lspClient, psiFile, documentLinkInfo.textRange, documentLinkInfo.highlightingInfo)
      }
    }

    return null
  }
}

private class LspDocumentLinkSymbolReference(
  private val lspClient: LspClientImpl,
  private val psiFile: PsiFile,
  private val textRange: TextRange,
  private val documentLink: LspDocumentLink,
) : PsiSymbolReference {
  override fun getElement(): PsiElement = psiFile

  override fun getRangeInElement(): TextRange = textRange

  override fun resolvesTo(target: Symbol): Boolean = false

  override fun resolveReference(): List<Symbol> {
    documentLink.resolveDocumentLink(lspClient)

    val uri = documentLink.targetUri ?: return emptyList()
    @Suppress("HttpUrlsUsage")
    if ((uri.startsWith("http://") || uri.startsWith("https://"))) {
      return listOf(LspOpenBrowserNavigatableSymbol(uri))
    }

    val targetFileOrDir = lspClient.descriptor.findFileByUri(uri) ?: return emptyList()
    return listOf(LspPathDocumentLinkSymbol(lspClient.project, targetFileOrDir))
  }
}


/**
 * The file or directory a document link points at, as opposed to [LspOpenBrowserNavigatableSymbol] for one that points at a URL.
 */
internal class LspPathDocumentLinkSymbol(
  private val project: Project,
  private val targetFileOrDir: VirtualFile,
) : LspNavigationSymbol {
  override fun computeDocumentationHint(): @NlsContexts.HintText String = targetFileOrDir.path

  override fun createPointer(): Pointer<LspPathDocumentLinkSymbol> = Pointer {
    if (targetFileOrDir.isValid) LspPathDocumentLinkSymbol(project, targetFileOrDir) else null
  }

  override fun computePresentation(): TargetPresentation = computeLinkPresentation(project, targetFileOrDir)

  override fun getNavigationTargets(project: Project): List<NavigationTarget> = listOf(
    LspDocumentLinkNavigationTarget(project, targetFileOrDir)
  )
}


private class LspDocumentLinkNavigationTarget(
  private val project: Project,
  private val targetFileOrDir: VirtualFile,
) : NavigationTarget {
  override fun createPointer(): Pointer<LspDocumentLinkNavigationTarget> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation = computeLinkPresentation(project, targetFileOrDir)

  override fun navigationRequest(): NavigationRequest? {
    if (targetFileOrDir.isDirectory) {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(targetFileOrDir) ?: return null
      return NavigationRequest.directoryNavigationRequest(psiDirectory)
    }
    return NavigationRequest.sourceNavigationRequest(project, targetFileOrDir, 0)
  }
}


private fun computeLinkPresentation(project: Project, targetFileOrDir: VirtualFile): TargetPresentation =
  TargetPresentation.builder(targetFileOrDir.name)
    .icon(IconUtil.computeFileIcon(targetFileOrDir, 0, project))
    .locationText(targetFileOrDir.name)
    .presentation()


/**
 * The web page a document link points at.
 */
internal class LspOpenBrowserNavigatableSymbol(private val url: @NlsSafe String) : LspNavigationSymbol {
  override fun computeDocumentationHint(): @NlsContexts.HintText String = url

  override fun createPointer(): Pointer<LspOpenBrowserNavigatableSymbol> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation = TargetPresentation.builder(url).icon(AllIcons.General.Web).presentation()

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
