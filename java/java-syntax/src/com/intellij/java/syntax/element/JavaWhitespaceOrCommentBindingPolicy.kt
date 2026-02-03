// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import com.intellij.platform.syntax.syntaxElementTypeSetOf

internal object JavaWhitespaceOrCommentBindingPolicy : WhitespaceOrCommentBindingPolicy {
  private val leftBoundTokens = syntaxElementTypeSetOf(
    SyntaxTokenTypes.ERROR_ELEMENT, // todo move somewhere?
    JavaSyntaxElementType.TYPE_PARAMETER_LIST,
    JavaSyntaxElementType.NAME_VALUE_PAIR,
    JavaSyntaxElementType.ANNOTATION_PARAMETER_LIST,
    JavaSyntaxElementType.EXTENDS_LIST,
    JavaSyntaxElementType.IMPLEMENTS_LIST,
    JavaSyntaxElementType.EXTENDS_BOUND_LIST,
    JavaSyntaxElementType.THROWS_LIST,
    JavaSyntaxElementType.PROVIDES_WITH_LIST,
    JavaSyntaxElementType.PERMITS_LIST,
    JavaSyntaxElementType.REFERENCE_PARAMETER_LIST,
    JavaSyntaxElementType.EMPTY_EXPRESSION,
    JavaSyntaxElementType.EXPRESSION_LIST,
    JavaSyntaxElementType.ANNOTATION_PARAMETER_LIST,
  )

  override fun isLeftBound(elementType: SyntaxElementType): Boolean =
    elementType in leftBoundTokens
}