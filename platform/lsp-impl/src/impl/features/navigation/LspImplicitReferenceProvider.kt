package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.codeInsight.navigation.CtrlMouseActionElement
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionDisabled
import com.intellij.platform.lsp.api.customization.LspHoverSupport
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
    // We care only about the "Go To Declaration" and "Go To Type Declaration" actions, and about Ctrl+hover, which asks
    // for the same information without performing either of them.
    val request = currentRequest() ?: return null
    return when (request.actionId) {
      IdeActions.ACTION_GOTO_DECLARATION ->
        createResolvedReference(psiFile, offsetInElement, request.isCtrlHover, ::requestElementDefinitions,
                                fallbackToShowUsagesOnSelfDefinition = true)
      IdeActions.ACTION_GOTO_TYPE_DECLARATION ->
        createResolvedReference(psiFile, offsetInElement, request.isCtrlHover, ::requestTypeDefinitions,
                                fallbackToShowUsagesOnSelfDefinition = false)
      else -> null
    }
  }

  /**
   * The action this reference is needed for: the one being performed, or the one Ctrl+hover is computing the underline for.
   * Both are named by the thread context, on the thread that starts the work and in the coroutines it launches.
   *
   * With the Ctrl button pressed, mouse movement generates one [getImplicitReference] call per offset the pointer rests at,
   * so the definitions requested here are cached by [LspDefinitionCache].
   */
  private fun currentRequest(): ReferenceRequest? {
    ActionUtil.getActionThreadContext()?.actionId?.let { return ReferenceRequest(it, isCtrlHover = false) }
    CtrlMouseActionElement.current()?.actionId?.let { return ReferenceRequest(it, isCtrlHover = true) }
    return null
  }

  /**
   * An integration that turns LSP hover off has language support of its own, and that support also answers Ctrl+hover,
   * with a hint such as the TypeScript quick info for ts-go.
   * An LSP reference would replace that answer: the platform skips its own target lookup once any reference is found,
   * and prefers a reference to the declaration under the pointer.
   * So Ctrl+hover only asks the clients that show LSP hover, which is also where the hint of [LspDefinitionSymbol]
   * is going to come from (IJPL-252179). A performed action still asks every client.
   */
  private fun answersCtrlHover(lspClient: LspClientImpl): Boolean =
    lspClient.descriptor.lspCustomization.hoverCustomizer is LspHoverSupport

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
    isCtrlHover: Boolean,
    sendRequest: (lspClient: LspClientImpl, file: VirtualFile, offset: Int) -> List<LocationLink>,
    fallbackToShowUsagesOnSelfDefinition: Boolean,
  ): LspResolvedSymbolReference? {
    val file = psiFile.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null

    val lspClients = LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsForFileRequests(file)
      .filter { !isCtrlHover || answersCtrlHover(it) }
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
    if (locationLink.targetUri != lspClient.descriptor.getFileUri(file)) return false
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
   * it also covers a content file served by [LspDynamicFiles], which the opened-files registry does not track.
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

    val resolveResults: List<LspDefinitionSymbol> = clientsAndLocationLinks.flatMap { clientAndLocationLinks ->
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
        val targetFile = clientAndLocationLinks.lspClient.dynamicFiles.findTargetFile(locationLink.targetUri)
                         ?: return@mapNotNull null
        LspDefinitionSymbol(clientAndLocationLinks.lspClient.project, targetFile, locationLink.targetSelectionRange)
      }
    }

    if (rangeInFile == null || resolveResults.isEmpty()) return null

    return LspResolvedSymbolReference(psiFile, rangeInFile, resolveResults)
  }
}


private data class LspClientAndLocationLinks(val lspClient: LspClientImpl, val locationLinks: List<LocationLink>)


private class ReferenceRequest(val actionId: String, val isCtrlHover: Boolean)


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
