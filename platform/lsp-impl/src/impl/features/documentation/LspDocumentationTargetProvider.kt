package com.intellij.platform.lsp.impl.features.documentation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.api.customization.LspHoverSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.MarkupContent

internal class LspDocumentationTargetProvider : DocumentationTargetProvider {
  override fun documentationTargets(psiFile: PsiFile, offset: Int): List<DocumentationTarget> {
    val project = psiFile.project
    if (project.isDefault) return emptyList()

    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val hostPsiFile = injectedLanguageManager.getTopLevelFile(psiFile)
    val offsetInHost = injectedLanguageManager.injectedToHost(psiFile, offset)
    val file = hostPsiFile.virtualFile ?: return emptyList()

    return LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file).mapNotNull { lspClient ->
      if (lspClient.descriptor.lspCustomization.hoverCustomizer !is LspHoverSupport) return@mapNotNull null
      if (!lspClient.supportsHover()) return@mapNotNull null

      // When `DocumentationTargetProvider.documentationTargets()` is called on hover, it is cancellable,
      // so using a bigger timeout wouldn't ever cause freezes.
      // However, the situation is different if the Quick Documentation action has been caused by a shortcut pressed.
      // In this case, the `AnAction.update()` method deliberately blocks EDT: although `AnAction.update()` runs in a background thread,
      // it is not cancellable when processing a shortcut.
      // This is done this way to be sure that the state doesn't change between `AnAction.update()` and `AnAction.actionPerformed()`.
      // The sign of such a not cancellable update is the `runUpdateSessionForInputEvent()` method in the stack trace.
      // Unfortunately, there's no reliable way to check whether the current `DocumentationTargetProvider.documentationTargets()`
      // call is cancellable or not. So, let's stay on the safe side and use small timeout.
      // According to the experiments, it is enough for LSP servers.
      val timeoutMs = LspClientImpl.NOT_CANCELLABLE_REQUEST_TIMEOUT_MS

      lspClient.requestExecutor.getHoverCaching(file, offsetInHost, timeoutMs)?.let { textRangeAndMarkupContent ->
        val presentableText =
          textRangeAndMarkupContent.textRange.takeIf { it.length > 0 && it.endOffset <= hostPsiFile.textLength }
            ?.substring(hostPsiFile.text)
        LspDocumentationTarget(presentableText, textRangeAndMarkupContent.markupContent, project)
      }
    }
  }
}

private class LspDocumentationTarget(
  private val presentableText: @NlsSafe String?,
  private val markupContent: MarkupContent,
  private val project: Project,
) : DocumentationTarget {
  override fun createPointer() = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation =
    TargetPresentation
      .builder(presentableText ?: CodeInsightBundle.message("documentation.tool.window.title"))
      .presentation()

  @RequiresReadLock
  override fun computeDocumentation(): DocumentationResult =
    createLspDocumentationData(markupContent)
      .toQuickDocHtml(project)
}
