// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.jsp

import com.intellij.java.impl.template.JavaTemplateFormattingSupport
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.psi.JspPsiUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.jsp.JspFile
import com.intellij.psi.jsp.JspLanguage
import com.intellij.psi.jsp.JspTemplateExpressionType
import com.intellij.psi.jsp.JspxLanguage
import com.intellij.psi.tree.IElementType

internal class JspFormattingSupport : JavaTemplateFormattingSupport {
  override fun isAdditionalWhitespace(type: IElementType): Boolean = type is JspTemplateExpressionType
  override fun hasSignificantWhitespace(language: Language): Boolean = language is JspLanguage || language is JspxLanguage

  override fun traversalRoots(file: PsiFile): List<PsiElement>? = if (file is JspFile) listOfNotNull(file.javaClass) else null

  override fun referenceRoots(file: PsiFile): List<ASTNode>? {
    val jspFile = JspPsiUtil.getJspFile(file) ?: return null
    return listOfNotNull(jspFile.javaClass?.node)
  }
}
