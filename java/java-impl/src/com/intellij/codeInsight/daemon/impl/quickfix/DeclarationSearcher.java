// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

class DeclarationSearcher {
  private final PsiMethod myMethod;
  private final PsiType myTargetType;

  private final Map<PsiElement, PsiVariable> cache = new HashMap<>();

  DeclarationSearcher(@NotNull PsiMethod method, @NotNull PsiType targetType) {
    myMethod = method;
    myTargetType = targetType;
  }

  public @Nullable PsiVariable getDeclaration(@NotNull PsiElement endPositionElement) {
    final PsiVariable localVariable = getLocalDeclaration(endPositionElement);
    if (localVariable != null) {
      return localVariable;
    }
    return getParameterDeclaration();
  }

  private @Nullable PsiVariable getParameterDeclaration() {
    for (PsiParameter parameter : myMethod.getParameterList().getParameters()) {
      if (myTargetType.equals(parameter.getType())) {
        return goThroughCache(myMethod, parameter);
      }
    }
    return null;
  }

  private @Nullable PsiVariable getLocalDeclaration(@NotNull PsiElement endPositionElement) {
    final PsiElement parent = endPositionElement.getParent();
    if (parent == null) return null;

    // reuse of cache is possible IF requests are done up-to-down. otherwise - not first declaration can be returned
    final PsiVariable cachedCandidate = cache.get(parent);
    if (cachedCandidate != null) {
      return cachedCandidate;
    }

    if (parent != myMethod) {
      // go up
      final PsiVariable parentResult = getLocalDeclaration(parent);
      if (parentResult != null) {
        return parentResult;
      }
    }

    // look self
    for (PsiElement element = parent.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element == endPositionElement) {
        break;
      }
      if (element instanceof PsiDeclarationStatement) {
        final PsiElement[] declared = ((PsiDeclarationStatement) element).getDeclaredElements();
        for (final PsiElement declaredElement : declared) {
          if (declaredElement instanceof PsiLocalVariable && myTargetType.equals(((PsiLocalVariable)declaredElement).getType())) {
            return goThroughCache(parent, (PsiVariable) declaredElement);
          }
        }
      } else if (element instanceof PsiParameter) {
        // foreach
        if (myTargetType.equals(((PsiParameter) element).getType())) {
          return goThroughCache(parent, (PsiVariable) element);
        }
      }
    }

    return null;
  }

  private PsiVariable goThroughCache(final PsiElement parent, final PsiVariable variable) {
    cache.put(parent, variable);
    return variable;
  }
}
