package com.intellij.platform.lsp.impl.features.usages

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.lsp.impl.documentMapping
import com.intellij.platform.lsp.impl.mapLocation
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.psi.PsiManager
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams

/**
 * Helps to implement the 'Find Usages' and 'Show Usages' features backed by an LSP server
 * ([textDocument/references](https://microsoft.github.io/language-server-protocol/specification/#textDocument_references) request).
 *
 * @see LspSearchTargetsRule
 */
internal class LspUsageSearcher : UsageSearcher {
  override fun collectSearchRequest(parameters: UsageSearchParameters): Query<Usage>? {
    val lspSearchTarget = parameters.target as? LspSearchTarget ?: return null
    return LspReferencesQuery(lspSearchTarget)
  }
}

private class LspReferencesQuery(private val searchTarget: LspSearchTarget) : AbstractQuery<Usage>() {
  override fun processResults(consumer: Processor<in Usage>): Boolean {
    for (lspServer in searchTarget.lspServers) {
      val docPosition = runReadActionBlocking {
        val document = FileDocumentManager.getInstance().getDocument(searchTarget.file) ?: return@runReadActionBlocking null
        val offset = getOffsetInDocument(document, searchTarget.position) ?: return@runReadActionBlocking null
        lspServer.documentMapping.getDocumentPosition(searchTarget.file, document, offset)
      } ?: continue
      val documentIdentifier = docPosition.document.id
      val position = docPosition.position
      val referenceContext = ReferenceContext(/* includeDeclaration = */ true)
      val params = ReferenceParams(documentIdentifier, position, referenceContext)

      val resultLocations = lspServer.sendRequestSync(FIND_REFERENCES_TIMEOUT_MS) {
        it.textDocumentService.references(params)
      }

      if (resultLocations.isNullOrEmpty()) continue

      runReadActionBlocking {
        val psiManager = PsiManager.getInstance(lspServer.project)

        for (resultLocation in resultLocations) {
          val mappedLocation = lspServer.documentMapping.findDocumentByUrl(resultLocation.uri)
                                 ?.mapLocation(resultLocation) ?: resultLocation
          val resultFile = lspServer.descriptor.findFileByUri(mappedLocation.uri) ?: continue
          val resultPsiFile = psiManager.findFile(resultFile) ?: continue
          val resultRange = getRangeInDocument(resultPsiFile.fileDocument, mappedLocation.range) ?: continue

          consumer.process(PsiUsage.textUsage(resultPsiFile, resultRange))
        }
      }
    }

    return true
  }
}

private const val FIND_REFERENCES_TIMEOUT_MS = 60_000
