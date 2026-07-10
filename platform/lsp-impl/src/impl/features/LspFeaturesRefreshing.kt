package com.intellij.platform.lsp.impl.features

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.features.codeLens.LSP_CODE_VISION_PROVIDER_ID
import com.intellij.util.application
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper

internal object LspFeaturesRefreshing {
  internal fun refreshBreadcrumbs() {
    application.messageBus.syncPublisher(BreadcrumbsXmlWrapper.FORCE_RELOAD_BREADCRUMBS).run()
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
