package com.intellij.codeInsight.navigation.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
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
    return new PsiElement[] { PsiUtil.resolveClassInType(type) };
  }
}
