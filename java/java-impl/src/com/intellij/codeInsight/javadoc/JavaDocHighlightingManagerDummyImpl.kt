// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod

/**
 *  Dummy implementation of JavaDocHighlightingManager that returns default attributes for all highlighting.
 *  Useful when the highlighting is irrelevant for your implementation.
 *
 *  @see JavaDocInfoMarkdownPrinter for a [JavaDocInfoPrinter] implementation that does not make use of the highlighting attributes
 */
public class JavaDocHighlightingManagerDummyImpl private constructor() : JavaDocHighlightingManager {

    override fun getKeywordAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getCommaAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getParenthesesAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getDotAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getBracketsAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getOperationSignAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getClassNameAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getClassDeclarationAttributes(aClass: PsiClass): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getMethodDeclarationAttributes(method: PsiMethod): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getFieldDeclarationAttributes(field: PsiField): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getParameterAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getTypeParameterNameAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getLocalVariableAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    override fun getMethodCallAttributes(): TextAttributes = DEFAULT_FLYWEIGHT

    public companion object {
      private val DEFAULT_FLYWEIGHT = TextAttributes()
      private val INSTANCE = JavaDocHighlightingManagerDummyImpl()
      public fun getInstance(): JavaDocHighlightingManagerDummyImpl {
        return INSTANCE
      }
    }
}