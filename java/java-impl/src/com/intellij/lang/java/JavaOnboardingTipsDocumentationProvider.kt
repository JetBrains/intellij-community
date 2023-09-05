// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.tree.IElementType
import java.util.function.Consumer

private const val tipPrefix = "//TIP"

class JavaOnboardingTipsDocumentationProvider: DocumentationProvider {
  private val enabled get() = Registry.`is`("doc.onboarding.tips.render")

  override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
    if (!enabled || file !is PsiJavaFile) return

    val visitedComments = mutableSetOf<PsiElement>()

    file.accept(object: PsiRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        if (visitedComments.contains(comment)) return
        if (comment.node.elementType != JavaTokenType.END_OF_LINE_COMMENT) return

        if (comment.text.startsWith(tipPrefix)) {
          val wrapper = createOnboardingTipComment(comment, visitedComments)
          sink.accept(wrapper)
        }
      }
    })
  }

  override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? {
    if (!enabled || file !is PsiJavaFile) return null
    var result: PsiDocCommentBase? = null
    file.accept(object: PsiRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        if (comment.textRange.startOffset != range.startOffset) return
        result = OnboardingTipComment(comment.parent, range)
      }
    })

    return result
  }

  override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
    if (!enabled || comment !is OnboardingTipComment) return null
    val result = comment.text
      .split("\n")
      .map { it.trim() }
      .map { if (it.startsWith(tipPrefix)) it.substring(tipPrefix.length, it.length) else it }
      .map { if (it.startsWith("//")) it.substring(2, it.length) else it }
      .joinToString(separator = " ").trim()
    return "<tip><p>$result</p></tip>"
  }
}

private fun createOnboardingTipComment(start: PsiComment, visitedComments: MutableSet<PsiElement>): OnboardingTipComment {
  var current: PsiElement = start
  while(true) {
    var nextSibling = current.nextSibling
    if (nextSibling is PsiWhiteSpace) nextSibling = nextSibling.nextSibling
    if (nextSibling?.node?.elementType != JavaTokenType.END_OF_LINE_COMMENT) break
    visitedComments.add(nextSibling)
    current = nextSibling
  }
  return OnboardingTipComment(current.parent, TextRange(start.textRange.startOffset, current.textRange.endOffset))
}

private class OnboardingTipComment(private val parent: PsiElement, private val range: TextRange): FakePsiElement(), PsiDocCommentBase  {
  override fun getParent() = parent

  override fun getTokenType(): IElementType = JavaTokenType.END_OF_LINE_COMMENT

  override fun getTextRange() = range

  override fun getText() = range.substring(parent.containingFile.text)

  override fun getOwner() = parent
}
