// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.util.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

/**
 * @param showAllCandidates true if we must show all candidates for the element. If false, candidates will only be shown when the method call is ambiguous.
 */
public class JavaDocumentationTarget(public val element: PsiElement, private val originalElement: PsiElement?, public val showAllCandidates: Boolean = false) : DocumentationTarget {
  @RequiresReadLock
  @RequiresBackgroundThread
  override fun createPointer(): Pointer<out DocumentationTarget> {
    val elementPtr = element.createSmartPointer()
    val originalElementPtr = originalElement?.createSmartPointer()
    return Pointer {
      val element = elementPtr.dereference() ?: return@Pointer null
      JavaDocumentationTarget(element, originalElementPtr?.dereference(), showAllCandidates)
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun computeDocumentation(): DocumentationResult {
    return DocumentationResult.asyncDocumentation {
      val generated = readAction { generateDoc(element, originalElement) }

      // Try fetching external documentation from URLs
      if (!generated.urls.isNullOrEmpty()) {
        val fetched = withContext(Dispatchers.IO) {
          JavaDocumentationProvider.fetchExternalJavadoc(element, element.project, generated.urls)
        }
        if (fetched != null) {
          return@asyncDocumentation DocumentationResult.documentation(fetched)
            .externalUrl(generated.urls.first())
        }
      }

      // Fall back to locally generated doc
      return@asyncDocumentation generated.html?.let { DocumentationResult.documentation(it) }
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun computeDocumentationHint(): @NlsContexts.HintText String? {
      val target = resolveCallTarget(element) ?: element
      return JavaDocumentationProvider().getQuickNavigateInfo(target, originalElement)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun computePresentation(): TargetPresentation {
    return targetPresentation(element)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  private fun generateDoc(element: PsiElement, originalElement: PsiElement?): GeneratedDoc {
    when (element) {
      is PsiMethodCallExpression -> {
        if (!showAllCandidates) {
          val resolvedCall = element.resolveMethod()
          if (resolvedCall != null) return generateDoc(resolvedCall, originalElement)
        }
        return GeneratedDoc(html = JavaDocumentationProvider.getMethodCandidateInfo(element))
      }
      is PsiNewExpression -> {
        if (!showAllCandidates) {
          val resolvedConstructor = element.resolveConstructor()
          if (resolvedConstructor != null) return generateDoc(resolvedConstructor, originalElement)
        }
        val targetClass = element.classReference?.resolve() as? PsiClass ?: return GeneratedDoc()
        val constructors = targetClass.constructors
        if (constructors.size > 0) {
          return GeneratedDoc(html = JavaDocumentationProvider.generateDocForConstructorCandidates(originalElement, constructors, targetClass))
        }
      }
    }

    val urls = JavaDocumentationProvider.getExternalJavaDocUrl(element)
    return GeneratedDoc(
      html = JavaDocumentationProvider.generateExternalJavadoc(element, urls),
      urls = urls,
    )
  }

  private data class GeneratedDoc(
    val html: @Nls String? = null,
    val urls: List<String>? = null,
  )
}

private fun resolveCallTarget(element: PsiElement): PsiElement? = when (element) {
  is PsiMethodCallExpression -> element.resolveMethod()
  is PsiNewExpression -> element.resolveConstructor() ?: (element.classReference?.resolve() as? PsiClass)
  else -> null
}
