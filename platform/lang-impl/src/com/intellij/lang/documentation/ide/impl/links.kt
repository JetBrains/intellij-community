// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ExternalDocumentationHandler
import com.intellij.lang.documentation.impl.handleLink
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEntry
import com.intellij.psi.PsiManager
import com.intellij.util.SlowOperations
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun handleLink(
  project: Project,
  targetPointer: Pointer<out DocumentationTarget>,
  url: String,
): Any? {
  if (url.startsWith("open")) {
    return libraryEntry(project, targetPointer)
  }
  return handleLink(targetPointer, url)
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
  return BrowserUtil.browseAbsolute(url)
}

private fun handleExternal(project: Project, targetPointer: Pointer<out DocumentationTarget>, url: String): Boolean {
  return SlowOperations.allowSlowOperations("old API fallback").use {
    doHandleExternal(project, targetPointer, url)
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
