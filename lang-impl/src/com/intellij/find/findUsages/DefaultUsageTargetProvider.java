package com.intellij.find.findUsages;

import com.intellij.find.FindManager;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;

public class DefaultUsageTargetProvider implements UsageTargetProvider {
  public UsageTarget[] getTargets(Editor editor, PsiFile file) {
    return null;
  }

  public UsageTarget[] getTargets(PsiElement psiElement) {
    if (psiElement instanceof NavigationItem) {
      if (FindManager.getInstance(psiElement.getProject()).canFindUsages(psiElement)) {
        return new UsageTarget[]{new PsiElement2UsageTargetAdapter(psiElement)};
      }
    }
    return null;
  }
}
