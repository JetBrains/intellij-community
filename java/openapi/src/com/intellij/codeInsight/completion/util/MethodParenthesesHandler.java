// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MethodParenthesesHandler extends ParenthesesInsertHandler<LookupElement> {
  private final PsiMethod myMethod;
  private final boolean myOverloadsMatter;

  public MethodParenthesesHandler(final PsiMethod method, boolean overloadsMatter) {
    myMethod = method;
    myOverloadsMatter = overloadsMatter;
  }

  @Override
  protected boolean placeCaretInsideParentheses(final InsertionContext context, final LookupElement item) {
    return myOverloadsMatter
           ? overloadsHaveParameters(context.getElements(), myMethod) != ThreeState.NO
           : !myMethod.getParameterList().isEmpty();
  }

  public static ThreeState overloadsHaveParameters(LookupElement[] allItems, PsiMethod method) {
    List<PsiMethod> overloads = JBIterable.of(allItems)
      .map(LookupElement::getPsiElement)
      .filter(PsiMethod.class)
      .filter(element -> element.getName().equals(method.getName()))
      .toList();
    return overloads.isEmpty() ? ThreeState.fromBoolean(!method.getParameterList().isEmpty()) : hasParameters(overloads);
  }

  public static @NotNull ThreeState hasParameters(List<PsiMethod> methods) {
    boolean hasEmpty = methods.isEmpty();
    boolean hasNonEmpty = false;
    for (PsiMethod method : methods) {
      if (!method.getParameterList().isEmpty()) {
        hasNonEmpty = true;
      } else {
        hasEmpty = true;
      }
    }
    return hasNonEmpty && hasEmpty ? ThreeState.UNSURE : hasNonEmpty ? ThreeState.YES : ThreeState.NO;
  }
}
