// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager
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
import com.intellij.util.SlowOperations
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun handleLink(
  project: Project,
  targetPointer: Pointer<out DocumentationTarget>,
  url: String,
  page: DocumentationPage
): Any? {
  when {
    url.startsWith("open") -> {
      return libraryEntry(project, targetPointer)
    }
    url == TOGGLE_EXPANDABLE_DEFINITION -> {
      val expandableDefinition = page.expandableDefinition!!
      expandableDefinition.toggleExpanded()
      return InternalLinkResult.Updater(expandableDefinition)
    }
    else -> return handleLink(targetPointer, url)
  }
}

private suspend fun libraryEntry(project: Project, targetPointer: Pointer<out DocumentationTarget>): OrderEntry? {
  return withContext(Dispatchers.Default) {
    readAction {
      val target = targetPointer.dereference() ?: return@readAction null
      if (target is PsiElementDocumentationTarget) { // currently, only PSI targets are supported
        DocumentationManager.libraryEntry(project, target.targetElement)
      }
      else {
        null
      }
    }
  }
}

internal fun openUrl(project: Project, targetPointer: Pointer<out DocumentationTarget>, url: String): Boolean {
  EDT.assertIsEdt()
  if (handleExternal(project, targetPointer, url)) {
    return true
  }
  if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
    return false
  }
  return browseAbsolute(project, url)
}

private fun handleExternal(project: Project, targetPointer: Pointer<out DocumentationTarget>, url: String): Boolean {
  return SlowOperations.allowSlowOperations(SlowOperations.GENERIC).use {
    doHandleExternal(project, targetPointer, url) // old API fallback
  }
}

private fun doHandleExternal(project: Project, targetPointer: Pointer<out DocumentationTarget>, url: String): Boolean {
  val target = targetPointer.dereference() as? PsiElementDocumentationTarget ?: return false
  val element = target.targetElement
  val provider = DocumentationManager.getProviderFromElement(element) as? CompositeDocumentationProvider ?: return false
  for (p in provider.allProviders) {
    if (p !is ExternalDocumentationHandler) {
      continue
    }
    if (p.handleExternalLink(PsiManager.getInstance(project), url, element)) {
      return true
    }
  }
  return false
}

fun browseAbsolute(project: Project, url: String): Boolean {
  if (BrowserUtil.isAbsoluteURL(url)) {
    BrowserUtil.browse(url, project)
    return true
  }
  return false
}
