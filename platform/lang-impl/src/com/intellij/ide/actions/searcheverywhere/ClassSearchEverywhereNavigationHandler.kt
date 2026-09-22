// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Navigates to `Foo#bar`. It resolves the member in the selected class through the structure view.
 * It falls back to the class when it cannot resolve the member.
 */
@Internal
class ClassSearchEverywhereNavigationHandler(project: Project) : SearchEverywhereNavigationHandler(project) {
  override suspend fun createSourceNavigationRequest(
    project: Project,
    element: PsiElement,
    file: VirtualFile,
    searchText: String,
    offset: Int,
  ): NavigationRequest? {
    val memberName = getMemberName(searchText)
    if (memberName != null) {
      memberNavigationRequest(memberPattern = memberName, fullPattern = searchText, psiElement = element, file = file)?.let {
        return it
      }
    }
    return super.createSourceNavigationRequest(project, element, file, searchText, offset)
  }
}

private suspend fun memberNavigationRequest(
  memberPattern: String,
  fullPattern: String,
  psiElement: PsiElement,
  file: VirtualFile,
): NavigationRequest? {
  val view = readAction { createStructureView(psiElement, file) } ?: return null
  try {
    return readAction {
      findMember(view = view, memberPattern = memberPattern, fullPattern = fullPattern, psiElement = psiElement)?.navigationRequest()
    }
  }
  finally {
    // The view holds an AsyncTreeModel, whose dispose asserts the EDT, and this runs off the EDT.
    // NonCancellable keeps the view from leaking when the navigation is canceled.
    withContext(NonCancellable + Dispatchers.EDT) {
      Disposer.dispose(view)
    }
  }
}

private fun createStructureView(psiElement: PsiElement, file: VirtualFile): StructureView? {
  val factory = LanguageStructureViewBuilder.getInstance().forLanguage(psiElement.language)
  val builder = factory?.getStructureViewBuilder(psiElement.containingFile) ?: return null
  val editors = FileEditorManager.getInstance(psiElement.project).getEditorList(file)
  if (editors.isEmpty()) {
    return null
  }
  return builder.createStructureView(editors[0], psiElement.project)
}

private fun findMember(view: StructureView, memberPattern: String, fullPattern: String, psiElement: PsiElement): Navigatable? {
  val element = findElement(view.treeModel.root, psiElement, 4) ?: return null
  val matcher = NameUtil.buildMatcher(memberPattern).build()
  var max = Int.MIN_VALUE
  var target: Any? = null
  for (treeElement in element.children) {
    if (treeElement is StructureViewTreeElement) {
      val value = treeElement.value
      if (value is PsiElement && value is Navigatable && fullPattern == CopyReferenceAction.elementToFqn(value)) {
        return value
      }

      val presentableText = treeElement.getPresentation().presentableText
      if (presentableText != null) {
        val degree = matcher.matchingDegree(presentableText)
        if (degree > max) {
          max = degree
          target = treeElement.value
        }
      }
    }
  }
  return target as? Navigatable
}

private fun findElement(node: StructureViewTreeElement, element: PsiElement, hopes: Int): StructureViewTreeElement? {
  val value = node.value as? PsiElement ?: return null
  if (value.isEquivalentTo(element)) {
    return node
  }

  if (hopes != 0) {
    for (child in node.children) {
      if (child is StructureViewTreeElement) {
        findElement(child, element, hopes - 1)?.let {
          return it
        }
      }
    }
  }
  return null
}

private fun getMemberName(searchedText: String): String? {
  val index = searchedText.lastIndexOf('#')
  return if (index == -1) null else searchedText.substring(index + 1).trim().ifEmpty { null }
}
