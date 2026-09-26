package com.intellij.platform.lsp.impl.features

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.features.codeLens.LSP_CODE_VISION_PROVIDER_ID
import com.intellij.psi.PsiManager
import com.intellij.util.application
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper

internal object LspFeaturesRefreshing {
  internal fun refreshBreadcrumbs() {
    application.messageBus.syncPublisher(BreadcrumbsXmlWrapper.FORCE_RELOAD_BREADCRUMBS).run()
  }

  /**
   * Restarts the daemon for the file, so the next `LineMarkersPass` reads the fresh
   * [LspInheritanceMarkersCache][com.intellij.platform.lsp.impl.features.lineMarkers.LspInheritanceMarkersCache] snapshot.
   */
  internal fun refreshLineMarkers(project: Project, file: VirtualFile) {
    runInEdt {
      if (project.isDisposed) return@runInEdt
      val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runInEdt
      DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
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
}
