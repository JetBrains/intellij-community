package com.intellij.platform.lsp.impl.features.formatter

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.ImportOptimizer
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.LspServerNotificationsHandlerImpl
import com.intellij.platform.lsp.impl.mapTextEdit
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextEdit

internal class LspFormattingService : AsyncDocumentFormattingService() {
  override fun getName() = LspBundle.message("lsp.based.formatter")
  override fun getNotificationGroupId() = LspServerNotificationsHandlerImpl.SHOW_MESSAGE_NOTIFICATION_GROUP
  override fun getFeatures() = setOf(Feature.FORMAT_FRAGMENTS, Feature.OPTIMIZE_IMPORTS)

  // No need to save the document, LSP servers work fine with unsaved content
  override fun prepareForFormatting(document: Document, formattingContext: FormattingContext): Unit = Unit

  override fun canFormat(psiFile: PsiFile): Boolean = canFormat(psiFile, *emptyArray<Feature>())

  override fun canFormat(psiFile: PsiFile, vararg features: Feature): Boolean {
    val goal = LspFormattingGoal.getFormattingGoal(features) ?: return false
    val lspServer: LspServer? = when (goal) {
      LspFormattingGoal.FullFileFormatting -> findServerToFormatThisFile(psiFile, true)
      LspFormattingGoal.RangeFormatting -> findServerToFormatThisFile(psiFile, false)
      LspFormattingGoal.OptimizeImports -> findServerToOptimizeImports(psiFile)
    }
    return lspServer != null
  }

  private fun findServerToFormatThisFile(psiFile: PsiFile, isFullFileFormatting: Boolean): LspServerImpl? {
    if (psiFile.project.isDefault) return null
    val file = extractValidFile(psiFile) ?: return null

    return LspServerManagerImpl.getInstanceImpl(psiFile.project).findRunningServer { lspServer ->
      lspServer.descriptor.isSupportedFile(file) && canServerFormatFile(lspServer, psiFile, file, isFullFileFormatting)
    }
  }

  private fun canServerFormatFile(lspServer: LspServerImpl, psiFile: PsiFile, file: VirtualFile, isFullFileFormatting: Boolean): Boolean {
    if (isFullFileFormatting && !lspServer.hasFullFileFormattingCapability()) return false
    if (!isFullFileFormatting && !lspServer.hasRangeFormattingCapability()) return false
    val formattingSupport = lspServer.descriptor.lspCustomization.formattingCustomizer as? LspFormattingSupport ?: return false
    val fmb: FormattingModelBuilder? = LanguageFormatting.INSTANCE.forContext(psiFile)
    val ideCanFormatThisFileItself: Boolean = fmb != null
    val serverExplicitlyWantsToFormatThisFile: Boolean = lspServer.doesServerExplicitlyWantToFormatThisFile(file, isFullFileFormatting)

    return formattingSupport.shouldFormatThisFileExclusivelyByServer(file,
                                                                     ideCanFormatThisFileItself,
                                                                     serverExplicitlyWantsToFormatThisFile)
  }

  // It's safe to unconditionally return `LspImportOptimizer` because all callers of `getImportOptimizers()`
  // immediately call `ImportOptimizer.supports()`, so `LspImportOptimizer.supports()` will take care of replying `true` or `false` correctly.
  override fun getImportOptimizers(file: PsiFile): Set<ImportOptimizer> = setOf(LspImportOptimizer())

  private fun extractValidFile(psiFile: PsiFile): VirtualFile? =
    psiFile.virtualFile?.takeIf { it.isInLocalFileSystem && it !is VirtualFileWindow }

  override fun createFormattingTask(formattingRequest: AsyncFormattingRequest): FormattingTask? {
    val file: VirtualFile = formattingRequest.context.virtualFile ?: return null
    val isFullFileFormatting = isFullFileFormatting(formattingRequest)
    val lspServer = findServerToFormatThisFile(formattingRequest.context.containingFile, isFullFileFormatting) ?: return null
    return if (isFullFileFormatting) LspFullFileFormattingTask(lspServer, file, formattingRequest)
    else LspRangeFormattingTask(lspServer, file, formattingRequest)
  }

  private fun isFullFileFormatting(formattingRequest: AsyncFormattingRequest): Boolean {
    val ranges = formattingRequest.getFormattingRanges()
    if (ranges.isEmpty()) return false

    val text = formattingRequest.getDocumentText()
    return ranges.size == 1 && ranges[0]!!.startOffset == 0 && ranges[0]!!.endOffset == text.length
  }

  private enum class LspFormattingGoal {
    FullFileFormatting,
    RangeFormatting,
    OptimizeImports;

    companion object {
      fun getFormattingGoal(features: Array<out Feature>): LspFormattingGoal? = when {
        features.isEmpty() -> FullFileFormatting
        features.size > 1 -> null // the only possible combination is AD_HOC + FRAGMENTS, but AD_HOC is not supported
        features[0] == Feature.FORMAT_FRAGMENTS -> RangeFormatting
        features[0] == Feature.OPTIMIZE_IMPORTS -> OptimizeImports
        else -> null
      }
    }
  }

  private abstract class LspFormattingTask(
    protected val lspServer: LspServerImpl,
    protected val file: VirtualFile,
    protected val formattingRequest: AsyncFormattingRequest,
  ) : FormattingTask {
    override fun isRunUnderProgress() = true

    override fun cancel(): Boolean = true

    protected fun createFormattingOptions(): FormattingOptions {
      val codeStyleSettings = formattingRequest.context.codeStyleSettings
      return FormattingOptions().apply {
        tabSize = codeStyleSettings.getIndentSize(file.fileType)
        isInsertSpaces = !codeStyleSettings.useTabCharacter(file.fileType)
      }
    }

    abstract fun fetchTextEdits(): List<TextEdit>?
    override fun run() {
      val textEdits: List<TextEdit>? = fetchTextEdits()
      if (textEdits.isNullOrEmpty()) {
        formattingRequest.onTextReady(null)
        return
      }
      val tempDocument = DocumentImpl(formattingRequest.documentText, false, true)
      val ok = applyTextEdits(tempDocument, textEdits)
      val result = if (ok) tempDocument.text else null
      formattingRequest.onTextReady(result)
    }
  }

  private class LspFullFileFormattingTask(
    lspServer: LspServerImpl,
    file: VirtualFile,
    formattingRequest: AsyncFormattingRequest,
  ) : LspFormattingTask(lspServer, file, formattingRequest) {
    override fun fetchTextEdits(): List<TextEdit>? {
      val formattingOptions = createFormattingOptions()
      val lspDocuments = lspServer.documentMapping.getDocumentsInFileSync(file)
      val results = lspDocuments.flatMap { lspDocument ->
        val params = DocumentFormattingParams(lspDocument.id, formattingOptions)
        lspServer.sendRequestSync { it.textDocumentService.formatting(params) }
          ?.map(lspDocument::mapTextEdit)
          ?: emptyList()
      }
      return results.ifEmpty { null }
    }
  }

  private class LspRangeFormattingTask(
    lspServer: LspServerImpl,
    file: VirtualFile,
    formattingRequest: AsyncFormattingRequest,
  ) : LspFormattingTask(lspServer, file, formattingRequest) {
    override fun fetchTextEdits(): List<TextEdit> {
      val formattingOptions = createFormattingOptions()
      val document = runReadActionBlocking { formattingRequest.context.containingFile.fileDocument }

      return formattingRequest.formattingRanges.flatMap { textRange ->
        val lspDocuments = runReadActionBlocking {
          val range = getLsp4jRange(document, textRange.startOffset, textRange.length)
          lspServer.documentMapping.getDocumentRangesSync(file, document, range)
        }
        lspDocuments.flatMap { (lspDocument, cellRange) ->
          val params = DocumentRangeFormattingParams(lspDocument.id, formattingOptions, cellRange)
          lspServer.sendRequestSync { it.textDocumentService.rangeFormatting(params) }
            ?.map(lspDocument::mapTextEdit)
            ?: emptyList()
        }
      }
    }
  }
}
