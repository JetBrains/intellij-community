package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtilBase;

/**
 * @author yole
 */
public class JspPsiUtil {
  public static boolean isInJspFile(@Nullable final PsiElement element) {
    return getJspFile(element) != null;
  }

  public static JspFile getJspFile(final PsiElement element) {
    final PsiFile psiFile = PsiUtilBase.getTemplateLanguageFile(element);
    return psiFile instanceof JspFile ? (JspFile)psiFile : null;

    /*final FileViewProvider provider = element.getContainingFile().getViewProvider();
    PsiFile file = provider.getPsi(StdLanguages.JSP);
    if (file instanceof JspFile) return (JspFile)file;
    file = provider.getPsi(StdLanguages.JSPX);
    return file instanceof JspFile ? (JspFile)file : null;*/
  }
}
