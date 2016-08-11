/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
class ApproximateResolver {
  /**
   * Tries to calculate the type of a given expression using lightweight resolve: no type inference for method calls, no exact candidate selection, works only with raw types.
   * If it returns a non-empty set, the "real" type class is guaranteed to be present in that set. This method can be used to quickly
   * discard search variants of certainly incompatible types without doing full resolve (which is likely to be more expensive).
   *
   * @param expression the expression to determine the type of
   * @param maxDepth a guard against too long chained calls, determines how deeply this resolve will go into a chained call
   * @return a set of possible classes corresponding to types that this expression might have, or null if it can't be determined
   */
  @Nullable
  static Set<PsiClass> getPossibleTypes(@NotNull PsiExpression expression, int maxDepth) {
    if (maxDepth == 0) return null;

    expression = PsiUtil.skipParenthesizedExprDown(expression);

    if (expression instanceof PsiTypeCastExpression) return extractClass(expression.getType());

    boolean method = false;
    if (expression instanceof PsiMethodCallExpression) {
      method = true;
      expression = ((PsiMethodCallExpression)expression).getMethodExpression();
    }

    if (!(expression instanceof PsiReferenceExpression)) return null;

    String name = ((PsiReferenceExpression)expression).getReferenceName();
    if (name == null) return null;

    PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
    if (qualifier != null) {
      Set<PsiClass> qualifierType = getPossibleTypes(qualifier, maxDepth - 1);
      if (qualifierType == null) return null;

      return method ? getPossibleCallTypes(qualifierType, name) : getPossibleMemberTypes(qualifierType, name);
    }

    if (method) return null;

    PsiElement target = ((PsiReferenceExpression)expression).resolve();
    return target instanceof PsiClass ? Collections.singleton((PsiClass)target) : extractClass(expression.getType());
  }

  @Nullable
  private static Set<PsiClass> extractClass(PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return psiClass == null || psiClass instanceof PsiTypeParameter ? null : Collections.singleton(psiClass);
  }

  @Nullable
  private static Set<PsiClass> getPossibleCallTypes(Set<PsiClass> inClasses, @NotNull String name) {
    Set<PsiClass> allTypes = ContainerUtil.newHashSet();
    for (PsiClass aClass : inClasses) {
      for (PsiMethod method : aClass.findMethodsByName(name, true)) {
        PsiClass type = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
        if (type == null || type instanceof PsiTypeParameter) return null;
        allTypes.add(type);
      }
    }
    return allTypes;
  }

  @Nullable
  private static Set<PsiClass> getPossibleMemberTypes(Set<PsiClass> inClasses, @NotNull String name) {
    Set<PsiClass> allTypes = ContainerUtil.newHashSet();
    for (PsiClass aClass : inClasses) {
      PsiField field = aClass.findFieldByName(name, true);
      if (field != null) {
        PsiClass fieldType = PsiUtil.resolveClassInClassTypeOnly(field.getType());
        if (fieldType == null || fieldType instanceof PsiTypeParameter) return null;
        allTypes.add(fieldType);
      }

      ContainerUtil.addIfNotNull(allTypes, aClass.findInnerClassByName(name, true));
    }
    return allTypes;
  }
}
