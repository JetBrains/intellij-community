// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * @author db
 */
public final class Util {
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
      final Deque<PsiMethod> queue = new ArrayDeque<>(1);
      queue.addLast(method);
      while (!queue.isEmpty()) {
        PsiMethod currentMethod = queue.removeFirst();
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

  public static boolean canBeMigrated(final PsiElement @NotNull [] es) {
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
