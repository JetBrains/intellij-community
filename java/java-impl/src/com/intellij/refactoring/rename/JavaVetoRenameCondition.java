package com.intellij.refactoring.rename;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.JspPsiUtil;

public class JavaVetoRenameCondition implements Condition<PsiElement> {
  public boolean value(final PsiElement element) {
    return element instanceof PsiJavaFile &&
           !JspPsiUtil.isInJspFile(element) &&
           !CollectHighlightsUtil.isOutsideSourceRootJavaFile((PsiFile)element) &&
           ((PsiJavaFile) element).getClasses().length > 0;
  }
}
