package com.intellij.codeInsight;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public interface MemberImplementorExplorer {
  @NotNull PsiMethod[] getMethodsToImplement(PsiClass aClass);
}
