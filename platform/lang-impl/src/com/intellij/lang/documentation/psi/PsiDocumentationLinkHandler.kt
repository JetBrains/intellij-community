// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.lang.documentation.psi

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationHandler
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult

internal class PsiDocumentationLinkHandler : DocumentationLinkHandler {

  override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
    if (target !is PsiElementDocumentationTarget) {
      return null
    }
    val element = target.targetElement
    if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      val project = element.project
      val (resolved, anchor) = DocumentationManager.targetAndRef(project, url, element)
                               ?: return null
      return LinkResolveResult.resolvedTarget(PsiElementDocumentationTarget(project, resolved, sourceElement = null, anchor))
    }
    val provider = DocumentationManager.getProviderFromElement(element)
    if (provider is CompositeDocumentationProvider) {
      for (p in provider.allProviders) {
        if (p !is ExternalDocumentationHandler) {
          continue
        }
        if (p.canFetchDocumentationLink(url)) {
          return LinkResolveResult.resolvedTarget(PsiExternalDocumentationHandlerTarget(p, url, element))
        }
      }
    }
    return null
  }
}
