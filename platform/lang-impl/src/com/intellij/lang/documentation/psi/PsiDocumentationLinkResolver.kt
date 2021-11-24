// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.psi

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.*
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2

internal class PsiDocumentationLinkResolver : DocumentationLinkResolver {

  override fun resolveLink(target: DocumentationTarget, url: String): LinkResult? {
    if (target !is PsiElementDocumentationTarget) {
      return null
    }
    val element = target.targetElement
    if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      val project = element.project
      val (resolved, anchor) = DocumentationManager.targetAndRef(project, url, element)
                            ?: return null
      return LinkResult.resolvedTarget(PsiElementDocumentationTarget(project, resolved, sourceElement = null, anchor))
    }
    val provider = DocumentationManager.getProviderFromElement(element)
    if (provider is CompositeDocumentationProvider) {
      for (p in provider.allProviders) {
        if (p !is ExternalDocumentationHandler) {
          continue
        }
        if (p.canFetchDocumentationLink(url)) {
          return LinkResult.resolvedTarget(PsiExternalDocumentationHandlerTarget(p, url, element))
        }
      }
    }
    return null
  }
}
