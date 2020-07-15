// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.jsp.JspFile;

/**
 * @author yole
 */
public final class JspPsiUtil {
  public static boolean isInJspFile(@Nullable final PsiElement element) {
    return getJspFile(element) != null;
  }

  @Nullable
  public static JspFile getJspFile(final PsiElement element) {
    final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(element);
    return psiFile instanceof JspFile ? (JspFile)psiFile : null;

    /*final FileViewProvider provider = element.getContainingFile().getViewProvider();
    PsiFile file = provider.getPsi(StdLanguages.JSP);
    if (file instanceof JspFile) return (JspFile)file;
    file = provider.getPsi(StdLanguages.JSPX);
    return file instanceof JspFile ? (JspFile)file : null;*/
  }
}
