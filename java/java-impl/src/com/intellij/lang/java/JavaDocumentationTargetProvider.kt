// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java

import com.intellij.codeInsight.completion.CompletionMemory
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaDocumentedElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.Nls

public class JavaPsiDocumentationTargetProvider: PsiDocumentationTargetProvider {
  override fun documentationTarget(
    element: PsiElement,
    originalElement: PsiElement?,
  ): DocumentationTarget? {
    if (element.language !is JavaLanguage) return null
    if (element is PsiJavaFile) return null

    // list all candidates for `new Foo(<caret>)` / `foo.bar(<caret>)`
    val forceCandidatesTarget: PsiCallExpression? = when (element) {
      is PsiExpressionList if element.parent is PsiMethodCallExpression -> element.parent
      is PsiJavaCodeReferenceElement if element.parent is PsiNewExpression -> element.parent
      is PsiExpressionList if element.parent is PsiNewExpression -> element.parent
      else -> null
    } as PsiCallExpression?

    if (forceCandidatesTarget != null) {
      // The user just picked an overload via completion: prefer the chosen method over a candidate list.
      val chosen = CompletionMemory.getChosenMethod(forceCandidatesTarget)
      if (chosen != null) return JavaDocumentationTarget(chosen, originalElement)
      return JavaDocumentationTarget(forceCandidatesTarget, originalElement, showAllCandidates = true)
    }

    val target = when (element) {
      is PsiReferenceExpression -> element.parent
      else -> element
    }

    return JavaDocumentationTarget(target, originalElement)
  }
}

public class JavaInlineDocumentationProvider : InlineDocumentationProvider {
  override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
    if (file !is PsiJavaFile) return emptyList()

    val result = mutableListOf<InlineDocumentation>()
    PsiTreeUtil.processElements(file) { element ->
      val declaration = element as? PsiJavaDocumentedElement
      val comment = declaration?.docComment
      if (comment != null) {
        result.add(JavaInlineDocumentation(comment, declaration))
      }
      true
    }
    return result
  }

  override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
    val comment = PsiTreeUtil.getParentOfType(PsiUtilCore.getElementAtOffset(file, textRange.startOffset), PsiDocCommentBase::class.java) ?: return null
    if (comment.textRange == textRange) {
      val declaration = comment.owner as? PsiDocCommentOwner ?: return null
      return JavaInlineDocumentation(comment, declaration)
    }
    return null
  }
}

private class JavaInlineDocumentation(private val comment: PsiDocCommentBase, private val documentedElement: PsiJavaDocumentedElement) :
  InlineDocumentation {
  override fun getDocumentationRange(): TextRange {
    return comment.textRange
  }

  override fun getDocumentationOwnerRange(): TextRange? {
    return documentedElement.textRange
  }

  override fun renderText(): @Nls String? {
    return JavaDocumentationProvider.generateRenderedDocStatic(comment)
  }

  override fun getOwnerTarget(): DocumentationTarget {
    return JavaDocumentationTarget(documentedElement, documentedElement)
  }
}