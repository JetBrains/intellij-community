// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.jsp

import com.intellij.java.debugger.template.JavaTemplateDebuggerSupport
import com.intellij.psi.JspPsiUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal class JspTemplateDebuggerSupport : JavaTemplateDebuggerSupport {
  override fun getTemplateSourceFile(element: PsiElement): PsiFile? = JspPsiUtil.getJspFile(element)
}
