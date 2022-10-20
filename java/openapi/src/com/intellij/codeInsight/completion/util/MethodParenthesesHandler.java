/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @NotNull
  public static ThreeState hasParameters(List<PsiMethod> methods) {
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
