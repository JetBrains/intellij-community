/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.PsiMethod;

import java.util.ArrayList;

/**
 * @author peter
 */
public class JavaMethodMergingContributor extends CompletionContributor {

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    final CompletionParameters parameters = context.getParameters();
    if (parameters.getCompletionType() != CompletionType.SMART && parameters.getCompletionType() != CompletionType.BASIC) {
      return null;
    }

    final LookupElement[] items = context.getItems();
    if (items.length > 1) {
      String commonName = null;
      LookupElement best = null;
      final ArrayList<PsiMethod> allMethods = new ArrayList<PsiMethod>();
      for (LookupElement item : items) {
        final Object o = item.getObject();
        if (item.getUserData(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null || !(o instanceof PsiMethod)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        final PsiMethod method = (PsiMethod)o;
        final JavaChainLookupElement chain = item.as(JavaChainLookupElement.class);
        final String name = method.getName() + "#" + (chain == null ? "" : chain.getQualifier().getLookupString());
        if (commonName != null && !commonName.equals(name)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        if (best == null && method.getParameterList().getParametersCount() > 0) {
          best = item;
        }
        commonName = name;
        allMethods.add(method);
        item.putUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, allMethods);
      }
      if (best == null) {
        best = items[0];
      }
      return AutoCompletionDecision.insertItem(best);
    }

    return super.handleAutoCompletionPossibility(context);
  }
}
