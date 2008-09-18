package com.intellij.codeInsight.navigation.actions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaTypeDeclarationProvider implements TypeDeclarationProvider {
  @Nullable
  public PsiElement[] getSymbolTypeDeclarations(final PsiElement targetElement) {
    PsiType type;
    if (targetElement instanceof PsiVariable){
      type = ((PsiVariable)targetElement).getType();
    }
    else if (targetElement instanceof PsiMethod){
      type = ((PsiMethod)targetElement).getReturnType();
    }
    else{
      return null;
    }
    if (type == null) return null;
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null ? null : new PsiElement[] {psiClass};
  }
}
