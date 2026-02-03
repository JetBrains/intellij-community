// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;


public final class JspPsiUtil {
  public static boolean isInJspFile(final @Nullable PsiElement element) {
    return getJspFile(element) != null;
  }

  public static @Nullable JspFile getJspFile(final PsiElement element) {
    final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(element);
    return psiFile instanceof JspFile ? (JspFile)psiFile : null;

    /*final FileViewProvider provider = element.getContainingFile().getViewProvider();
    PsiFile file = provider.getPsi(StdLanguages.JSP);
    if (file instanceof JspFile) return (JspFile)file;
    file = provider.getPsi(StdLanguages.JSPX);
    return file instanceof JspFile ? (JspFile)file : null;*/
  }
}
