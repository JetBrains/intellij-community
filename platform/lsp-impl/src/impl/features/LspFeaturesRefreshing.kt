package com.intellij.platform.lsp.impl.features

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.ide.impl.StructureViewWrapperImpl
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.features.codeLens.LSP_CODE_VISION_PROVIDER_ID
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LspFeaturesRefreshing {
  internal fun restartStructureView() {
    application.messageBus.syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run()
    application.messageBus.syncPublisher(BreadcrumbsXmlWrapper.FORCE_RELOAD_BREADCRUMBS).run()
  }

  internal fun refreshInlayHints(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
      DaemonCodeAnalyzer.getInstance(project).restart("LspFeaturesRefreshing.refreshInlayHints")
    }
  }

  internal suspend fun refreshInlayHints(project: Project, file: VirtualFile, reason: Any) {
    val editors = EditorFactory.getInstance().allEditors.filter { it.virtualFile == file }
    val psiFile = readAction {
      findPsiFileIfOpen(project, file)
    }
    withContext(Dispatchers.EDT) {
      editors.forEach { editor ->
        InlayHintsPassFactoryInternal.clearModificationStamp(editor) // needs EDT
      }
      if (psiFile != null && psiFile.isValid) {
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile, reason) // should be run on EDT atomically with clearModificationStamp
      }
    }
  }

  internal fun refreshCodeLenses(project: Project) {
    runInEdt {
      project.service<CodeVisionHost>()
        .invalidateProvider(
          CodeVisionHost.LensInvalidateSignal(null, listOf(LSP_CODE_VISION_PROVIDER_ID))
        )
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  private fun findPsiFileIfOpen(project: Project, file: VirtualFile): PsiFile? {
    val originFile = BackedVirtualFile.getOriginFileIfBacked(file)
    return if (FileEditorManager.getInstance(project).isFileOpen(originFile)) {
      PsiManager.getInstance(project).findFile(file)
    }
    else {
      null
    }
  }
}
