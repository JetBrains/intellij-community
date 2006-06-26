/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

public class PsiSuperMethodUtil {
  private PsiSuperMethodUtil() {}

  public static PsiMethod findConstructorInSuper(PsiMethod constructor) {
    return findConstructorInSuper(constructor, new HashSet<PsiMethod>());
  }

  public static PsiMethod findConstructorInSuper(PsiMethod constructor, Set<PsiMethod> visited) {
    if (visited.contains(constructor)) return null;
    visited.add(constructor);
    final PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiElement firstChild = statements[0].getFirstChild();
        if (firstChild instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)firstChild).getMethodExpression();
          final @NonNls String text = methodExpr.getText();
          if (text.equals("super")) {
            PsiElement superConstructor = methodExpr.resolve();
            if (superConstructor instanceof PsiMethod) {
              return (PsiMethod)superConstructor;
            }
          } else if (text.equals("this")) {
            final PsiElement resolved = methodExpr.resolve();
            if (resolved instanceof PsiMethod) {
              return findConstructorInSuper((PsiMethod)resolved, visited);
            }
            return null;
          }
        }
      }
    }

    PsiClass containingClass = constructor.getContainingClass();
    if (containingClass != null) {
      PsiClass superClass = containingClass.getSuperClass();
      if (superClass != null) {
        MethodSignature defConstructor = MethodSignatureUtil.createMethodSignature(superClass.getName(), PsiType.EMPTY_ARRAY,
                                                                                   PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        return MethodSignatureUtil.findMethodBySignature(superClass, defConstructor, false);
      }
    }
    return null;
  }
}
