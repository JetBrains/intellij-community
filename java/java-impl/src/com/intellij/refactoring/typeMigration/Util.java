/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author db
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

  @Nullable
  public static PsiElement normalizeElement(final PsiElement element) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiType initialMethodReturnType = method.getReturnType();
      if (initialMethodReturnType == null) {
        return null;
      }
      final List<PsiMethod> normalized = new SmartList<>();
      final Queue<PsiMethod> queue = new Queue<>(1);
      queue.addLast(method);
      while (!queue.isEmpty()) {
        final PsiMethod currentMethod = queue.pullFirst();
        if (initialMethodReturnType.equals(currentMethod.getReturnType())) {
          for (PsiMethod toConsume : currentMethod.findSuperMethods(false)) {
            queue.addLast(toConsume);
          }
          normalized.add(currentMethod);
        }
      }
      //TODO Dmitry Batkovich multiple result is possible
      return normalized.isEmpty() ? element : normalized.get(normalized.size() - 1);
    }
    else if (element instanceof PsiParameter && element.getParent() instanceof PsiParameterList) {
      final PsiElement declarationScope = ((PsiParameter)element).getDeclarationScope();
      if (declarationScope instanceof PsiLambdaExpression) {
        final PsiType interfaceType = ((PsiLambdaExpression)declarationScope).getFunctionalInterfaceType();
        if (interfaceType != null) {
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
          if (interfaceMethod != null) {
            final int index = ((PsiParameterList)element.getParent()).getParameterIndex((PsiParameter)element);
            return interfaceMethod.getParameterList().getParameters()[index];
          }
        }
        return null;
      }
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

  public static boolean canBeMigrated(@NotNull final PsiElement[] es) {
    return Arrays.stream(es).allMatch(Util::canBeMigrated);
  }

  private static boolean canBeMigrated(@Nullable final PsiElement e) {
    if (e == null) {
      return false;
    }

    final PsiElement element = normalizeElement(e);

    if (element == null || !element.isWritable()) {
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
        return true;
      }
    }

    return false;
  }
}
