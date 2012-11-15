package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public interface MemberImplementorExplorer {
  ExtensionPointName<MemberImplementorExplorer> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.methodImplementor");

  @NotNull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);
}
