/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class JavaPsiElementExternalizer implements PsiElementExternalizer {
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

  @Nullable
  @Override
  public PsiElement findByQualifiedName(Project project, @NotNull String qualifiedName) {
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
