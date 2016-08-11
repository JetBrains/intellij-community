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

import com.intellij.navigation.AnonymousElementProvider;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesProvider implements AnonymousElementProvider {
  @NotNull
  @Override
  public PsiElement[] getAnonymousElements(@NotNull PsiElement parent) {
    if (suite(parent)) {
      if (parent instanceof PsiCompiledElement) {
        parent = parent.getNavigationElement();
      }
      if (suite(parent) && !(parent instanceof PsiCompiledElement)) {
        final List<PsiElement> elements = new ArrayList<>();
        final PsiElement element = parent;
        element.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitAnonymousClass(PsiAnonymousClass aClass) {
            final PsiExpressionList arguments = aClass.getArgumentList();
            if (arguments != null) {
              for (PsiExpression expression : arguments.getExpressions()) {
                visitExpression(expression);
              }
            }
            elements.add(aClass);
          }

          @Override
          public void visitClass(PsiClass aClass) {
            if (aClass == element) {
              super.visitClass(aClass);
            }
          }
        });

        if (! elements.isEmpty()) {
          return elements.toArray(new PsiElement[elements.size()]);
        }
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  private static boolean suite(PsiElement element) {
    return element instanceof PsiClass
      || element instanceof PsiMethod
      || element instanceof PsiField
      || element instanceof PsiClassInitializer;
  }
}
