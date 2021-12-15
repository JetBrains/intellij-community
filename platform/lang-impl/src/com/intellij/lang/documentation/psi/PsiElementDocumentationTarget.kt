// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.psi

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.lang.documentation.*
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.SlowOperations
import com.intellij.util.castSafelyTo
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Supplier

@VisibleForTesting
class PsiElementDocumentationTarget private constructor(
  val targetElement: PsiElement,
  private val sourceElement: PsiElement?,
  private val pointer: PsiElementDocumentationTargetPointer,
) : DocumentationTarget {

  internal constructor(
    project: Project,
    targetElement: PsiElement,
  ) : this(
    project, targetElement, sourceElement = null, anchor = null
  )

  internal constructor(
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
    return pointer.fetchExternal(targetElement, provider, urls, localDoc?.copy(linkUrls = urls))
  }

  @Suppress("TestOnlyProblems")
  @RequiresReadLock
  private fun localDoc(provider: DocumentationProvider): DocumentationData? {
    val originalPsi = targetElement.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY)?.element
    val doc = provider.generateDoc(targetElement, originalPsi)
    val locationInfo = provider.getLocationInfo(targetElement)?.toString()
    if (targetElement is PsiFile) {
      val fileDoc = DocumentationManager.generateFileDoc(targetElement, doc == null)
      if (fileDoc != null) {
        return DocumentationData(
          if (doc == null) fileDoc else doc + fileDoc,
          locationInfo,
          pointer.anchor,
          null,
          emptyList(),
          pointer.imageResolver
        )
      }
    }
    if (doc != null) {
      return DocumentationData(doc, locationInfo, pointer.anchor, null, emptyList(), pointer.imageResolver)
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

    fun fetchExternal(
      targetElement: PsiElement,
      provider: ExternalDocumentationProvider,
      urls: List<String>,
      localDoc: DocumentationResult?,
    ): DocumentationResult = DocumentationResult.asyncDocumentation(Supplier {
      LOG.debug("External documentation URLs: $urls")
      for (url in urls) {
        ProgressManager.checkCanceled()
        val doc = provider.fetchExternalDocumentation(project, targetElement, listOf(url), false)
                  ?: continue
        val locationInfo = provider.castSafelyTo<DocumentationProvider>()?.getLocationInfo(targetElement)?.toString()
        LOG.debug("Fetched documentation from $url")
        return@Supplier DocumentationResult.externalDocumentation(doc, anchor, url, locationInfo, imageResolver)
      }
      localDoc
    })

    val imageResolver: DocumentationImageResolver = DocumentationImageResolver { url ->
      SlowOperations.allowSlowOperations("old API fallback").use {
        dereference()?.targetElement?.let { targetElement ->
          DocumentationManager.getElementImage(targetElement, url)
        }
      }
    }
  }
}
