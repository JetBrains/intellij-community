// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java

import com.intellij.codeInsight.completion.CompletionMemory
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaDocumentedElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiJavaModuleReferenceElement
import com.intellij.psi.PsiJavaReference
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.Nls

public class JavaDocumentationTargetProvider : DocumentationTargetProvider {
  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    if (file !is PsiJavaFile) return emptyList()

    val originalElement = file.findElementAt(offset) ?: return emptyList()
    if (originalElement.language !is JavaLanguage) return emptyList()
    var element = originalElement

    // Handle `element<caret> `/`element<caret>(` case
    val whiteSpaceStart = element is PsiWhiteSpace && offset == element.textRange.startOffset
    val token = element is PsiJavaTokenImpl && element.tokenType !== JavaTokenType.AT
    val error = element is PsiErrorElement
    if (whiteSpaceStart || token || error) {
      val prevLeaf = PsiTreeUtil.prevCodeLeaf(element)
      if (prevLeaf != null) element = prevLeaf
    }

    if (element.parent is PsiLiteralExpression) element = element.parent

    val parent = element.parent

    // list all candidates for `new Foo(<caret>)` / `foo.bar(<caret>)`
    val forceCandidatesTarget: PsiCallExpression? = when (parent) {
      is PsiExpressionList if parent.parent is PsiMethodCallExpression -> parent.parent
      is PsiExpressionList if parent.parent is PsiNewExpression -> parent.parent
      else -> null
    } as PsiCallExpression?

    if (forceCandidatesTarget != null) {
      // The user just picked an overload via completion: prefer the chosen method over a candidate list.
      val chosen = CompletionMemory.getChosenMethod(forceCandidatesTarget)
      if (chosen != null) return listOf(JavaDocumentationTarget(chosen, element))
      return listOf(JavaDocumentationTarget(forceCandidatesTarget, element, showAllCandidates = true))
    }

    var target: PsiElement =
      when {
        element is PsiKeyword && parent is PsiClass -> parent
        element is PsiKeyword && parent is PsiJavaModule -> parent
        element is PsiKeyword && parent is PsiPackageStatement -> parent.packageReference
        parent is PsiClassObjectAccessExpression -> parent.operand.innermostComponentReferenceElement
        parent is PsiJavaModuleReferenceElement -> parent.parent
        parent is PsiJavaCodeReferenceElement && parent.parent is PsiMethodCallExpression -> parent.parent
        parent is PsiJavaCodeReferenceElement && parent.parent is PsiNewExpression -> parent.parent
        parent is PsiDocParamRef -> parent.reference?.resolve()
        parent is PsiModifierList -> parent.parent
        parent is PsiNameValuePair -> parent.reference?.resolve()
        parent is PsiAnnotation -> parent.nameReferenceElement
        parent is PsiDocComment -> parent.owner
        element is PsiIdentifier -> parent
        element is FakePsiElement -> PsiTreeUtil.getParentOfType<PsiDocCommentBase>(originalElement)?.owner ?: element
        else -> null
      } ?: return emptyList()

    if (target is PsiJavaReference) {
      return target.multiResolve(false)
        .mapNotNull { it.element }
        .map { JavaDocumentationTarget(it, element) }
        .toList()
    }

    return listOf(JavaDocumentationTarget(target, element))
  }
}

public class JavaPsiDocumentationTargetProvider: PsiDocumentationTargetProvider {
  override fun documentationTarget(
    element: PsiElement,
    originalElement: PsiElement?,
  ): DocumentationTarget? {
    if (element.language !is JavaLanguage) return null
    return JavaDocumentationTarget(element, originalElement)
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