// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.psi

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.navigation.SingleTargetElementInfo
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.DocumentationProvider.DocumentationParts
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.model.Pointer
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.*
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Image
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
    project, targetElement, sourceElement = null
  )

  internal constructor(
    project: Project,
    targetElement: PsiElement,
    sourceElement: PsiElement?,
  ) : this(
    project, targetElement, sourceElement, anchor = null
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

  override fun computePresentation(): TargetPresentation = targetPresentation(targetElement)

  override val navigatable: Navigatable? get() = targetElement as? Navigatable

  override fun computeDocumentationHint(): String? {
    return SingleTargetElementInfo.generateInfo(targetElement, sourceElement, isNavigatableQuickDoc(sourceElement, targetElement))
  }

  override fun computeDocumentation(): DocumentationResult? {
    return blockingContextToIndicator {
      doComputeDocumentation()
    }
  }

  private fun doComputeDocumentation(): DocumentationResult? {
    val provider = DocumentationManager.getProviderFromElement(targetElement, sourceElement)
    val localDoc = localDoc(provider) // compute in this read action
    if (provider !is ExternalDocumentationProvider) {
      return localDoc
    }
    val urls = provider.getUrlFor(targetElement, targetElement.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY)?.element)
    if (urls.isNullOrEmpty()) {
      return localDoc
    }
    return pointer.fetchExternal(targetElement, provider, urls, localDoc?.copy(links = localDoc.links.copy(linkUrls = urls)))
  }

  @RequiresReadLock
  private fun localDoc(provider: DocumentationProvider): DocumentationData? {
    val parts = localDocParts(provider)
                ?: return null
    return DocumentationData(
      content = DocumentationContentData(
        html = parts.doc,
        definitionDetails = parts.definitionDetails,
        imageResolver = pointer.imageResolver,
        targetElement = targetElement
      ),
      anchor = pointer.anchor,
    )
  }

  @RequiresReadLock
  private fun localDocParts(provider: DocumentationProvider): @Nls DocumentationParts? {
    val originalPsi = targetElement.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY)?.element
                      ?: sourceElement
    val doc = provider.getDocumentationParts(targetElement, originalPsi)
    if (targetElement is PsiFileSystemItem) {
      val fileDoc = DocumentationManager.generateFileDoc(targetElement, doc == null)
      if (fileDoc != null) {
        return if (doc == null)
          DocumentationParts(fileDoc, null)
        else
          DocumentationParts(doc.doc + fileDoc, doc.definitionDetails)
      }
    }
    return doc
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
      localDoc: DocumentationResult.Documentation?,
    ): DocumentationResult = DocumentationResult.asyncDocumentation(Supplier {
      LOG.debug("External documentation URLs: $urls")
      for (url in urls) {
        ProgressManager.checkCanceled()
        val doc = provider.fetchExternalDocumentation(project, targetElement, listOf(url), false)
                  ?: continue
        LOG.debug("Fetched documentation from $url")
        return@Supplier DocumentationData(
          content = DocumentationContentData(
            html = doc,
            imageResolver = imageResolver,
            targetElement = targetElement
          ),
          anchor = anchor,
          links = LinkData(externalUrl = url),
        )
      }
      localDoc
    })

    val imageResolver: DocumentationImageResolver = DocumentationImageResolver { url ->
      ReadAction.compute<Image?, Throwable> {
        dereference()?.targetElement?.let { targetElement ->
          DocumentationManager.getElementImage(targetElement, url)
        }
      }
    }
  }
}
