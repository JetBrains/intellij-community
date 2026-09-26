// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.refactorings.jsp

import com.intellij.java.impl.refactorings.template.JavaTemplateRefactoringSupport
import com.intellij.lang.Language
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.impl.source.jsp.jspJava.JspClass
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod
import com.intellij.xml.util.JspFileTypeUtil

internal class JspTemplateRefactoringSupport : JavaTemplateRefactoringSupport {
  override fun allowsClassRefactoring(psiClass: PsiClass): Boolean = psiClass !is JspClass

  override fun supportsInlineRefactoring(language: Language): Boolean = JspFileTypeUtil.isJspOrJspX(language)

  override fun allowsInplaceIntroduceVariable(expression: PsiExpression): Boolean {
    val block = expression.parent?.parent
    return block !is JspCodeBlock || block.parent !is JspHolderMethod
  }
}
