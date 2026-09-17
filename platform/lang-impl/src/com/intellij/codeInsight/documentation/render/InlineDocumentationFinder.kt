package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
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

  fun getInlineDocumentation(item: DocRenderItem): InlineDocumentation? {
    if (item.highlighter.isValid) {
      val file = EditorContextManager.getPsiFileForEditor(item.editor, item.editor.project ?: return null) ?: return null
      return findInlineDocumentation(file, item.highlighter.textRange)
    }
    return null
  }
  fun getInlineDocumentationTarget(item: DocRenderItem): DocumentationTarget?
}

/**
 * A default implementation for the [InlineDocumentationFinder] interface.
 */
@ApiStatus.Internal
class InlineDocumentationFinderImpl : InlineDocumentationFinder {

  override fun getInlineDocumentationTarget(item: DocRenderItem): DocumentationTarget? {
    val documentation = getInlineDocumentation(item)
    return documentation?.ownerTarget
  }
}