/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class JavaMethodMergingContributor extends CompletionContributor {
  public boolean fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      if (!CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION) return true;
    }
    else if (parameters.getCompletionType() == CompletionType.SMART) {
      if (!CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION) return true;
    }
    else {
      return true;
    }

    final Ref<Boolean> wereNonGrouped = Ref.create(false);
    final Map<String, LookupItem<PsiMethod>> methodNameToItem = new LinkedHashMap<String, LookupItem<PsiMethod>>();
    final List<LookupItem<PsiMethod>> allMethodItems = new ArrayList<LookupItem<PsiMethod>>();
    final boolean toContinue =
        CompletionService.getCompletionService().getVariantsFromContributors(EP_NAME, parameters, this, new Consumer<LookupElement>() {
          public void consume(final LookupElement element) {
            if (!(element instanceof LookupItem)) {
              result.addElement(element);
              return;
            }

            LookupItem item = (LookupItem)element;
            item.setAttribute(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, null);
            Object o = item.getObject();
            if (item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null || !(o instanceof PsiMethod)) {
              result.addElement(item);
              wereNonGrouped.set(true);
              return;
            }

            allMethodItems.add(item);
            PsiMethod method = (PsiMethod)o;
            String name = method.getName() + "#" + item.getAttribute(JavaCompletionUtil.QUALIFIER_PREFIX_ATTRIBUTE);
            LookupItem<PsiMethod> existing = methodNameToItem.get(name);
            ArrayList<PsiMethod> allMethods;
            if (existing != null) {
              if (existing.getObject().getParameterList().getParametersCount() == 0 && method.getParameterList().getParametersCount() > 0) {
                methodNameToItem.put(name, item);
              }
              allMethods = (ArrayList<PsiMethod>)existing.getAttribute(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE);
            }
            else {
              methodNameToItem.put(name, item);
              allMethods = new ArrayList<PsiMethod>();
            }
            allMethods.add(method);
            item.setAttribute(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, allMethods);
          }

        });

    final boolean justOneMethodName = !wereNonGrouped.get() && methodNameToItem.size() == 1;
    if (!CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS || justOneMethodName) {
      for (final LookupItem<PsiMethod> item : methodNameToItem.values()) {
        result.addElement(item);
      }
    }
    else {
      for (final LookupItem<PsiMethod> item : allMethodItems) {
        result.addElement(item);
      }
    }
    return toContinue;
  }

}
