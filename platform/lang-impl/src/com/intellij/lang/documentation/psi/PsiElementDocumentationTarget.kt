// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.psi

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.function.Supplier

internal class PsiElementDocumentationTarget private constructor(
  val targetElement: PsiElement,
  private val sourceElement: PsiElement?,
  private val pointer: PsiElementDocumentationTargetPointer,
) : DocumentationTarget {

  constructor(
    project: Project,
    targetElement: PsiElement,
  ) : this(
    project, targetElement, sourceElement = null, anchor = null
  )

  constructor(
    project: Project,
    targetElement: PsiElement,
    sourceElement: PsiElement?,
    anchor: String?,
  ) : this(
    targetElement = targetElement,
    sourceElement = sourceElement,
    pointer = PsiElementDocumentationTargetPointer(
      project = project,
      targetPointer = targetElement.createSmartPointer(),
      sourcePointer = sourceElement?.createSmartPointer(),
      anchor = anchor
    )
  )

  override fun createPointer(): Pointer<out DocumentationTarget> = pointer

  override val presentation: TargetPresentation get() = targetPresentation(targetElement)

  override val navigatable: Navigatable? get() = targetElement as? Navigatable

  override fun computeDocumentation(): DocumentationResult? {
    val provider = DocumentationManager.getProviderFromElement(targetElement, sourceElement)
    val localDoc = localDoc(provider) // compute in this read action
    if (provider !is ExternalDocumentationProvider) {
      return localDoc
    }
    val urls = provider.getUrlFor(targetElement, targetElement.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY)?.element)
    if (urls == null || urls.isEmpty()) {
      return localDoc
    }
    return fetchExternal(pointer.project, targetElement, provider, urls, pointer.anchor, localDoc)
  }

  @RequiresReadLock
  private fun localDoc(provider: DocumentationProvider): DocumentationResult? {
    val originalPsi = targetElement.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY)?.element
    val doc = provider.generateDoc(targetElement, originalPsi)
    if (targetElement is PsiFile) {
      val fileDoc = DocumentationManager.generateFileDoc(targetElement, doc == null)
      if (fileDoc != null) {
        return DocumentationResult.documentation(if (doc == null) fileDoc else doc + fileDoc, pointer.anchor)
      }
    }
    if (doc != null) {
      return DocumentationResult.documentation(doc, pointer.anchor)
    }
    return null
  }

  private class PsiElementDocumentationTargetPointer(
    val project: Project,
    private val targetPointer: Pointer<out PsiElement>,
    private val sourcePointer: Pointer<PsiElement>?,
    val anchor: String?,
  ) : Pointer<PsiElementDocumentationTarget> {

    override fun dereference(): PsiElementDocumentationTarget? {
      val target = targetPointer.dereference() ?: return null
      val source = if (sourcePointer == null) {
        null
      }
      else {
        sourcePointer.dereference() ?: return null
      }
      return PsiElementDocumentationTarget(target, source, this)
    }
  }
}

private fun fetchExternal(
  project: Project,
  targetElement: PsiElement,
  provider: ExternalDocumentationProvider,
  urls: List<String>,
  anchor: String?,
  localDoc: DocumentationResult?,
): DocumentationResult = DocumentationResult.asyncDocumentation(Supplier {
  LOG.debug("External documentation URLs: $urls")
  for (url in urls) {
    ProgressManager.checkCanceled()
    val doc = provider.fetchExternalDocumentation(project, targetElement, listOf(url), false)
              ?: continue
    LOG.debug("Fetched documentation from $url")
    return@Supplier DocumentationResult.externalDocumentation(doc, anchor, url)
  }
  localDoc
})
