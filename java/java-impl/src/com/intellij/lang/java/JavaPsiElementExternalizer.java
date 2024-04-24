// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.PsiElementExternalizer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaPsiElementExternalizer implements PsiElementExternalizer {
  @Override
  public String getQualifiedName(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    } else if (element instanceof PsiMember) {
      final String name = getQualifiedName(((PsiMember)element).getContainingClass());
      return name + "#" + ((PsiMember)element).getName();
    }
    return null;
  }

  @Override
  public @Nullable PsiElement findByQualifiedName(Project project, @NotNull String qualifiedName) {
    final String[] parts = qualifiedName.split("#");
    if (parts.length > 0) {
      final String fqn = parts[0];
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.projectScope(project));
      if (psiClass != null) {
        if (parts.length == 2) {
          final String memberName = parts[1];
          final PsiField field = psiClass.findFieldByName(memberName, false);
          if (field != null) {
            return field;
          }
          final PsiMethod[] methods = psiClass.findMethodsByName(memberName, false);
          if (methods.length > 0) {
            return methods[0];
          }
        }
        return psiClass;
      }
    }
    return null;
  }
}
