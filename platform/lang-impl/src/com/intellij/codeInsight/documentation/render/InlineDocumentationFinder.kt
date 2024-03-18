package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.ApiStatus

/**
 * Designed to act as an overridable delegate interface for the implementation of the [DocRenderItemImpl.getInlineDocumentation]
 * and [DocRenderItemImpl.getInlineDocumentationTarget] methods in the [DocRenderItemImpl] class.
 *
 * Important for Rider because [InlineDocumentation] handling involves the R# backend there (simply calling [findInlineDocumentation] won't do).
 */
@ApiStatus.Internal
interface InlineDocumentationFinder {

  companion object {
    @JvmStatic fun getInstance(project: Project?): InlineDocumentationFinder? = project?.getService(InlineDocumentationFinder::class.java)
  }

  fun getInlineDocumentation(item: DocRenderItem): InlineDocumentation?
  fun getInlineDocumentationTarget(item: DocRenderItem): DocumentationTarget?
}

/**
 * A default implementation for the [InlineDocumentationFinder] interface.
 */
@ApiStatus.Internal
class InlineDocumentationFinderImpl : InlineDocumentationFinder {
  override fun getInlineDocumentation(item: DocRenderItem): InlineDocumentation? {
    if (item.highlighter.isValid) {
      val psiDocumentManager = PsiDocumentManager.getInstance(item.editor.project ?: return null)
      val file = psiDocumentManager.getPsiFile(item.editor.document) ?: return null
      return findInlineDocumentation(file, item.highlighter.textRange)
    }
    return null
  }

  override fun getInlineDocumentationTarget(item: DocRenderItem): DocumentationTarget? {
    val documentation = getInlineDocumentation(item)
    return documentation?.ownerTarget
  }
}