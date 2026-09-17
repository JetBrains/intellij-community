// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.jsp

import com.intellij.debugger.source.DebuggerSourceFileResolver
import com.intellij.psi.JspPsiUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.jsp.JspFile

internal class JspDebuggerSourceFileResolver : DebuggerSourceFileResolver {
  override fun resolveSourceFile(element: PsiElement): PsiFile? = JspPsiUtil.getJspFile(element)

  override fun isTemplateSource(file: PsiFile): Boolean = file is JspFile
}
