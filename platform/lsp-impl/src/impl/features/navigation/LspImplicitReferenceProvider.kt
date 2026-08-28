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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionDisabled
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.usages.LspSearchTarget
import com.intellij.platform.lsp.impl.features.usages.isFindReferencesEnabledFor
import com.intellij.platform.lsp.util.getLsp4jPosition
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
 * requests).
 *
 * When the caret is on a declaration, the 'Go To Declaration or Usages' action shows the usages instead.
 * See [createResolvedReference].
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
        createResolvedReference(psiFile, offsetInElement, ::requestElementDefinitions, fallbackToShowUsagesOnSelfDefinition = true)
      actionClass.isAssignableFrom(GotoTypeDeclarationAction::class.java) ->
        createResolvedReference(psiFile, offsetInElement, ::requestTypeDefinitions, fallbackToShowUsagesOnSelfDefinition = false)
      else -> null
    }
  }

  private fun requestElementDefinitions(lspClient: LspClientImpl, file: VirtualFile, offset: Int): List<LocationLink> {
    if (!lspClient.supportsGotoDefinition()) return emptyList()
    val goToDefCustomizer = lspClient.descriptor.lspCustomization.goToDefinitionCustomizer
    if (goToDefCustomizer !is LspGoToDefinitionSupport) return emptyList()
    return lspClient.requestExecutor.getElementDefinitions(file, offset)
  }

  private fun requestTypeDefinitions(lspClient: LspClientImpl, file: VirtualFile, offset: Int): List<LocationLink> {
    if (!lspClient.supportsGotoTypeDefinition()) return emptyList()
    if (lspClient.descriptor.lspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionDisabled) return emptyList()
    return lspClient.requestExecutor.getTypeDefinitions(file, offset)
  }

  /**
   * Sends the request to the LSP server and returns [LspResolvedSymbolReference] based on the received response.
   *
   * When [fallbackToShowUsagesOnSelfDefinition] is `true` and every response is a [self-definition][isSelfDefinition],
   * the caret is on a declaration.
   * In this case the reference resolves to a [LspSearchTarget], which has no navigation targets.
   * The 'Go To Declaration or Usages' action then shows the usages
   * (the [textDocument/references](https://microsoft.github.io/language-server-protocol/specification/#textDocument_references)
   * request), like it does for a declaration in a regular language.
   */
  private fun createResolvedReference(
    psiFile: PsiFile,
    offset: Int,
    sendRequest: (lspClient: LspClientImpl, file: VirtualFile, offset: Int) -> List<LocationLink>,
    fallbackToShowUsagesOnSelfDefinition: Boolean,
  ): LspResolvedSymbolReference? {
    val file = psiFile.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null

    val lspClients = LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsForFileRequests(file)
    val responses = lspClients.mapNotNull { lspClient ->
      val locationLinks = sendRequest(lspClient, file, offset)
      if (locationLinks.isNotEmpty()) LspClientAndLocationLinks(lspClient, locationLinks) else null
    }
    val (selfDefinitions, navigations) = responses.partition {
      fallbackToShowUsagesOnSelfDefinition && isSelfDefinition(it.lspClient, it.locationLinks, file, document, offset)
    }

    if (navigations.isNotEmpty()) return buildNavigationReference(psiFile, document, offset, navigations)
    if (selfDefinitions.isEmpty()) return null
    return createShowUsagesReference(psiFile, file, document, offset, lspClients, selfDefinitions.flatMap { it.locationLinks })
  }

  /**
   * A self-definition means that the server resolved the definition to the request position itself,
   * so the caret is on the declaration.
   * A server that returns a plain `Location` gives no origin selection range
   * (see [toLocationLink][com.intellij.platform.lsp.impl.util.toLocationLink]).
   * For such a response, a definition whose single-line name range covers the caret is a self-definition.
   * A multi-line range is likely the full declaration body, and a usage inside the body must still navigate.
   */
  private fun isSelfDefinition(
    lspClient: LspClientImpl,
    locationLinks: List<LocationLink>,
    file: VirtualFile,
    document: Document,
    offset: Int,
  ): Boolean {
    val locationLink = locationLinks.singleOrNull() ?: return false
    if (locationLink.targetUri != lspClient.getFileUriForRequests(file)) return false
    val targetSelectionRange = locationLink.targetSelectionRange ?: return false
    val originSelectionRange = locationLink.originSelectionRange
    if (originSelectionRange != null) return targetSelectionRange == originSelectionRange
    if (targetSelectionRange.start.line != targetSelectionRange.end.line) return false
    val targetRangeInDocument = getRangeInDocument(document, targetSelectionRange) ?: return false
    return targetRangeInDocument.containsOffset(offset)
  }

  /**
   * The reference-supporting clients come from the same [lspClients] list that answered the definition requests.
   * This list can be wider than the one the 'Find Usages' action uses
   * (see [LspSearchTargetsRule][com.intellij.platform.lsp.impl.features.usages.LspSearchTargetsRule]):
   * it also covers a library file that never got the `didOpen` notification.
   */
  private fun createShowUsagesReference(
    psiFile: PsiFile,
    file: VirtualFile,
    document: Document,
    offset: Int,
    lspClients: Collection<LspClientImpl>,
    selfDefinitionLinks: List<LocationLink>,
  ): LspResolvedSymbolReference? {
    val referenceClients = lspClients
      .filter { it.isFindReferencesEnabledFor(file) }
      .ifEmpty { return null }

    // The range must contain the request offset: `DeclarationOrReference.Reference.rangeWithOffset` fails otherwise.
    val rangeInFile = selfDefinitionLinks
      .mapNotNull { getRangeInDocument(document, it.targetSelectionRange) }
      .fold(TextRange(offset, offset), TextRange::union)

    val searchTarget = LspSearchTarget(referenceClients, file, getLsp4jPosition(document, offset))
    return LspResolvedSymbolReference(psiFile, rangeInFile, listOf(searchTarget))
  }

  private fun buildNavigationReference(
    psiFile: PsiFile,
    document: Document,
    offset: Int,
    clientsAndLocationLinks: List<LspClientAndLocationLinks>,
  ): LspResolvedSymbolReference? {
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
        val targetFile = clientAndLocationLinks.lspClient.libraryFiles.findTargetFile(locationLink.targetUri)
                         ?: return@mapNotNull null
        LspNavigatableSymbol(targetFile, locationLink.targetSelectionRange)
      }
    }

    if (rangeInFile == null || resolveResults.isEmpty()) return null

    return LspResolvedSymbolReference(psiFile, rangeInFile, resolveResults)
  }
}


private data class LspClientAndLocationLinks(val lspClient: LspClientImpl, val locationLinks: List<LocationLink>)


private class LspResolvedSymbolReference(
  private val psiFile: PsiFile,
  private val rangeInFile: TextRange,
  private val resolveResults: List<Symbol>,
) : PsiSymbolReference {
  override fun getElement(): PsiElement = psiFile
  override fun getRangeInElement(): TextRange = rangeInFile
  override fun resolveReference(): List<Symbol> = resolveResults
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
internal class CurrentActionHolder {
  var currentActionClass: Class<out AnAction>? = null
}
