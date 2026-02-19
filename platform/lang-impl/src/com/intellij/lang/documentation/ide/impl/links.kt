// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationHandler
import com.intellij.lang.documentation.ide.ui.TOGGLE_EXPANDABLE_DEFINITION
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEntry
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.InternalLinkResult
import com.intellij.platform.backend.documentation.impl.handleLink
import com.intellij.psi.PsiManager
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext

internal suspend fun handleLink(
  project: Project,
  targetPointer: Pointer<out DocumentationTarget>,
  url: String,
  page: DocumentationPage,
): Any? = when {
  url.startsWith("open") -> libraryEntry(project, targetPointer)
  url == TOGGLE_EXPANDABLE_DEFINITION -> {
    val expandableDefinition = page.expandableDefinition!!
    expandableDefinition.toggleExpanded()
    InternalLinkResult.Updater(expandableDefinition)
  }
  else -> handleLink(targetPointer, url)
}

private suspend fun libraryEntry(project: Project, targetPointer: Pointer<out DocumentationTarget>): OrderEntry? = withContext(Default) {
  readAction {
    val target = targetPointer.dereference() ?: return@readAction null
    @Suppress("TestOnlyProblems")
    if (target is PsiElementDocumentationTarget) { // currently, only PSI targets are supported
      @Suppress("DEPRECATION", "removal")
      com.intellij.codeInsight.documentation.DocumentationManager.libraryEntry(project, target.targetElement)
    }
    else {
      null
    }
  }
}

internal fun openUrl(project: Project, targetPointer: Pointer<out DocumentationTarget>, url: String): Boolean {
  EDT.assertIsEdt()
  return when {
    handleExternal(project, targetPointer, url) -> true
    url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL) -> false
    else -> browseAbsolute(project, url)
  }
}

// old API fallback
private fun handleExternal(project: Project, targetPointer: Pointer<out DocumentationTarget>, url: String): Boolean {
  @Suppress("TestOnlyProblems")
  val target = targetPointer.dereference() as? PsiElementDocumentationTarget ?: return false
  val element = target.targetElement
  @Suppress("DEPRECATION", "removal")
  val provider = com.intellij.codeInsight.documentation.DocumentationManager.getProviderFromElement(element) as? CompositeDocumentationProvider ?: return false
  for (p in provider.allProviders) {
    if (p is ExternalDocumentationHandler && p.handleExternalLink(PsiManager.getInstance(project), url, element)) {
      return true
    }
  }
  return false
}

fun browseAbsolute(project: Project, url: String): Boolean = when {
  BrowserUtil.isAbsoluteURL(url) -> {
    BrowserUtil.browse(url, project)
    true
  }
  else -> false
}
