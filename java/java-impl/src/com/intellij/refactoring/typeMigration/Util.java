/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author db
 * Date: Nov 4, 2004
 */
public class Util {
  private Util() { }

  public static PsiElement getEssentialParent(final PsiElement element) {
    final PsiElement parent = element.getParent();

    if (parent instanceof PsiParenthesizedExpression) {
      return getEssentialParent(parent);
    }

    return parent;
  }

  public static PsiElement normalizeElement(final PsiElement element) {
    if (element instanceof PsiMethod) {
      final PsiMethod superMethod = ((PsiMethod)element).findDeepestSuperMethod();

      return superMethod == null ? element : superMethod;
    }
    else if (element instanceof PsiParameter && element.getParent() instanceof PsiParameterList) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

      if (method != null) {
        final int index = method.getParameterList().getParameterIndex(((PsiParameter)element));
        final PsiMethod superMethod = method.findDeepestSuperMethod();

        if (superMethod != null) {
          return superMethod.getParameterList().getParameters()[index];
        }
      }
    }

    return element;
  }

  public static boolean canBeMigrated(final PsiElement e) {
    if (e == null) {
      return false;
    }

    final PsiElement element = normalizeElement(e);

    if (!element.getManager().isInProject(element)) {
      return false;
    }

    final PsiType type = TypeMigrationLabeler.getElementType(element);

    if (type != null) {
      final PsiType elementType = type instanceof PsiArrayType ? type.getDeepComponentType() : type;

      if (elementType instanceof PsiPrimitiveType) {
        return !elementType.equals(PsiType.VOID);
      }

      if (elementType instanceof PsiClassType) {
        final PsiClass aClass = ((PsiClassType)elementType).resolve();
        return aClass != null;
      }
      else if (elementType instanceof PsiDisjunctionType) {
        final PsiType lub = ((PsiDisjunctionType)elementType).getLeastUpperBound();
        return lub != null;
      }
    }

    return false;
  }
}
