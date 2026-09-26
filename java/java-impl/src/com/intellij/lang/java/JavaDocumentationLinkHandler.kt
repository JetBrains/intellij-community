// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult

public class JavaDocumentationLinkHandler : DocumentationLinkHandler {
  override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
    if (target !is JavaDocumentationTarget || !url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      return null
    }
    return LinkResolveResult.asyncResult {
      val element = target.element
      val linkTarget = readAction {
        val (targetElement, _) = DocumentationManager.targetAndRef(element.project, url, element) ?: return@readAction null
        val documentationTarget = psiDocumentationTargets(targetElement, targetElement).first()
        LinkResolveResult.Async.resolvedTarget(documentationTarget)
      }
      return@asyncResult linkTarget
    }
  }
}