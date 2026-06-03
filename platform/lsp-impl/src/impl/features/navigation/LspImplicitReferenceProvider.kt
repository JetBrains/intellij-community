package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.OverridingAction
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionDisabled
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.LocationLink

/**
 * Used for [Go To Declaration][GotoDeclarationAction] and [Go To Type Declaration][GotoTypeDeclarationAction] features
 * backed by the information from an LSP server
 * ([textDocument/definition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_definition) and
 * [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_typeDefinition)
 * requests)
 */
internal class LspImplicitReferenceProvider : ImplicitReferenceProvider {

  override fun getImplicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference? {
    val psiFile = element as? PsiFile ?: return null
    if (psiFile.project.isDefault) return null
    val file = psiFile.virtualFile ?: return null
    if (file is VirtualFileWindow) return null

    // There are several places in the IntelliJ codebase that call `getImplicitReference()` function.
    // For example, `IdentifierHighlighterPass.highlightReferencesAndDeclarations`, it calls this function on caret movement.
    // No need to send requests to the LSP server for features that won't work anyway.
    // We care only about the "Go To Declaration" and "Go To Type Declaration" actions.

    // TODO Unfortunately, Ctrl+hover in LSP-backed files doesn't work because of returning null from this function.
    // TODO It would be great to enable the Ctrl+hover feature somehow.
    // TODO Note that with Ctrl button pressed, mouse movement generates hundreds of getImplicitReference() calls,
    // TODO so caching of the getElementDefinitions() results will be needed.

    val actionClass = service<CurrentActionHolder>().currentActionClass ?: return null
    return when {
      actionClass.isAssignableFrom(GotoDeclarationAction::class.java) ->
        createResolvedReference(psiFile, offsetInElement, ::requestElementDefinitions)
      actionClass.isAssignableFrom(GotoTypeDeclarationAction::class.java) ->
        createResolvedReference(psiFile, offsetInElement, ::requestTypeDefinitions)
      else -> null
    }
  }

  private fun requestElementDefinitions(lspClient: LspClientImpl, file: VirtualFile, offset: Int): List<LocationLink> {
    if (!lspClient.supportsGotoDefinition()) return emptyList()
    val goToDefCustomizer = lspClient.descriptor.lspCustomization.goToDefinitionCustomizer
    if (goToDefCustomizer !is LspGoToDefinitionSupport) return emptyList()
    val definitions = lspClient.requestExecutor.getElementDefinitions(file, offset)

    if (definitions.size == 1
        && definitions[0].targetSelectionRange == definitions[0].originSelectionRange
        && definitions[0].targetUri == lspClient.descriptor.getFileUri(file))
      return emptyList()

    return definitions
  }

  private fun requestTypeDefinitions(lspClient: LspClientImpl, file: VirtualFile, offset: Int): List<LocationLink> {
    if (!lspClient.supportsGotoTypeDefinition()) return emptyList()
    if (lspClient.descriptor.lspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionDisabled) return emptyList()
    return lspClient.requestExecutor.getTypeDefinitions(file, offset)
  }

  /**
   * Sends the request to the LSP server and returns [LspResolvedSymbolReference] based on the received response
   */
  private fun createResolvedReference(
    psiFile: PsiFile,
    offset: Int,
    sendRequest: (lspClient: LspClientImpl, file: VirtualFile, offset: Int) -> List<LocationLink>,
  ): LspResolvedSymbolReference? {
    val file = psiFile.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null

    val clientsAndLocationLinks = LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsWithThisFileOpen(file)
      .mapNotNull {
        val locationLinks = sendRequest(it, file, offset)
        if (locationLinks.isNotEmpty()) LspClientAndLocationLinks(it, locationLinks) else null
      }
      .ifEmpty { return null }

    // In the case of `foo<caret>++`, a server may return references both for `foo` and for `++`.
    // IntelliJ's standard behavior is to respect only the right reference.
    val hasRangeToTheRight: Boolean = clientsAndLocationLinks.flatMap { it.locationLinks }.any { locationLink ->
      val originSelectionRange = locationLink.originSelectionRange ?: return@any false
      val endOffsetInOrigin = getOffsetInDocument(document, originSelectionRange.end) ?: return@any false
      endOffsetInOrigin > offset
    }

    var rangeInFile: TextRange? = null

    val resolveResults: List<LspNavigatableSymbol> = clientsAndLocationLinks.flatMap { clientAndLocationLinks ->
      clientAndLocationLinks.locationLinks.mapNotNull { locationLink ->
        val originSelectionRange = locationLink.originSelectionRange
        val textRange = if (originSelectionRange != null) {
          getRangeInDocument(document, originSelectionRange) ?: return@mapNotNull null
        }
        else {
          TextRange(offset, offset)
        }
        if (hasRangeToTheRight && textRange.endOffset <= offset) {
          // ignore references to the left of the caret
          return@mapNotNull null
        }
        rangeInFile = rangeInFile?.union(textRange) ?: textRange
        val targetFile = clientAndLocationLinks.lspClient.descriptor.findFileByUri(locationLink.targetUri)
                         ?: return@mapNotNull null
        LspNavigatableSymbol(targetFile, locationLink.targetSelectionRange)
      }
    }

    if (rangeInFile == null || resolveResults.isEmpty()) return null

    return LspResolvedSymbolReference(psiFile, rangeInFile, resolveResults)
  }
}


private data class LspClientAndLocationLinks(val lspClient: LspClient, val locationLinks: List<LocationLink>)


private class LspResolvedSymbolReference(
  private val psiFile: PsiFile,
  private val rangeInFile: TextRange,
  private val resolveResults: List<LspNavigatableSymbol>,
) : PsiSymbolReference {
  override fun getElement(): PsiElement = psiFile
  override fun getRangeInElement(): TextRange = rangeInFile
  override fun resolveReference(): List<LspNavigatableSymbol> = resolveResults
  override fun resolvesTo(target: Symbol) = false
}


private class CurrentActionListener : AnActionListener {
  private val AnAction.baseAction: AnAction
    get() = (this as? OverridingAction)?.let { (ActionManager.getInstance() as ActionManagerImpl).getBaseAction(this) }
            ?: this

  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    service<CurrentActionHolder>().currentActionClass = action.baseAction.javaClass
  }

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    service<CurrentActionHolder>().currentActionClass = null
  }
}


@Service(Service.Level.APP)
private class CurrentActionHolder {
  var currentActionClass: Class<AnAction>? = null
}
