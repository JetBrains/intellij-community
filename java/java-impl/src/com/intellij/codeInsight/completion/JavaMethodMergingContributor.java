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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author peter
 */
public class JavaMethodMergingContributor extends CompletionContributor {

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    final CompletionParameters parameters = context.getParameters();
    if (parameters.getCompletionType() != CompletionType.SMART && parameters.getCompletionType() != CompletionType.BASIC) {
      return null;
    }

    if (ParameterInfoController.areParameterTemplatesEnabledOnCompletion()) {
      return null;
    }

    final LookupElement[] items = context.getItems();
    if (items.length > 1) {
      String commonName = null;
      final ArrayList<PsiMethod> allMethods = new ArrayList<>();
      for (LookupElement item : items) {
        Object o = item.getPsiElement();
        if (item.getUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR) != null || !(o instanceof PsiMethod)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        String name = joinLookupStrings(item);
        if (commonName != null && !commonName.equals(name)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        commonName = name;
        allMethods.add((PsiMethod)o);
      }

      for (LookupElement item : items) {
        JavaCompletionUtil.putAllMethods(item, allMethods);
      }

      return AutoCompletionDecision.insertItem(findBestOverload(items));
    }

    return super.handleAutoCompletionPossibility(context);
  }

  public static String joinLookupStrings(LookupElement item) {
    return StreamEx.of(item.getAllLookupStrings()).sorted().joining("#");
  }

  public static LookupElement findBestOverload(LookupElement[] items) {
    LookupElement best = items[0];
    for (int i = 1; i < items.length; i++) {
      LookupElement item = items[i];
      if (getPriority(best) < getPriority(item)) {
        best = item;
      }
    }
    return best;
  }

  private static int getPriority(LookupElement element) {
    PsiMethod method = assertNotNull(getItemMethod(element));
    return (PsiType.VOID.equals(method.getReturnType()) ? 0 : 1) +
           (method.getParameterList().getParametersCount() > 0 ? 2 : 0);
  }

  @Nullable
  private static PsiMethod getItemMethod(LookupElement item) {
    Object o = item.getPsiElement();
    return o instanceof PsiMethod ? (PsiMethod)o : null;
  }
}
