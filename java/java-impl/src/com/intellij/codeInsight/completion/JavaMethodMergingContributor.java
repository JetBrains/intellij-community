// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

public final class JavaMethodMergingContributor extends CompletionContributor implements DumbAware {
  static final Key<Boolean> MERGED_ELEMENT = Key.create("merged.element");

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    final CompletionParameters parameters = context.getParameters();
    if (parameters.getCompletionType() != CompletionType.SMART && parameters.getCompletionType() != CompletionType.BASIC) {
      return null;
    }

    if (ParameterInfoControllerBase.areParameterTemplatesEnabledOnCompletion()) {
      return null;
    }

    final LookupElement[] items = context.getItems();
    if (ContainerUtil.exists(items, t -> t.as(MethodTags.TagLookupElementDecorator.class) != null)) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    if (items.length > 1) {
      String commonName = null;
      final ArrayList<PsiMethod> allMethods = new ArrayList<>();
      for (LookupElement item : items) {
        Object o = item.getPsiElement();
        if (!(o instanceof PsiMethod psiMethod)) {
          return super.handleAutoCompletionPossibility(context);
        }
        if (item.getUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR) != null) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        String name = joinLookupStrings(item);
        if (commonName != null && !commonName.equals(name)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        commonName = name;
        allMethods.add(psiMethod);
      }

      for (LookupElement item : items) {
        JavaCompletionUtil.putAllMethods(item, allMethods);
      }

      LookupElement best = findBestOverload(items);
      markAsMerged(best);
      return AutoCompletionDecision.insertItem(best);
    }

    return super.handleAutoCompletionPossibility(context);
  }

  private static void markAsMerged(LookupElement element) {
    JavaMethodCallElement methodCallElement = element.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
    if (methodCallElement != null) methodCallElement.putUserData(MERGED_ELEMENT, Boolean.TRUE);
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
    PsiMethod method = Objects.requireNonNull(getItemMethod(element));
    return (PsiTypes.voidType().equals(method.getReturnType()) ? 0 : 1) +
           (method.getParameterList().isEmpty() ? 0 : 2);
  }

  private static @Nullable PsiMethod getItemMethod(LookupElement item) {
    Object o = item.getPsiElement();
    return o instanceof PsiMethod ? (PsiMethod)o : null;
  }

  /**
   * Mark item to forcefully disallow merge with another item that refers to the same PsiMethod.
   *
   * @param item to mark
   */
  public static void disallowMerge(LookupElement item) {
    item.putUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR, true);
  }
}
