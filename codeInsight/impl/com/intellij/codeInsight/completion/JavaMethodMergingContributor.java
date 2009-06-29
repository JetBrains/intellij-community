/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;
import com.intellij.util.containers.CollectionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class JavaMethodMergingContributor extends CompletionContributor {
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.SMART && parameters.getCompletionType() != CompletionType.BASIC) return;

    final CompletionProcess process = CompletionService.getCompletionService().getCurrentCompletion();
    ProgressManager.getInstance().checkCanceled();
    if (process == null || !process.willAutoInsert(AutoCompletionPolicy.SETTINGS_DEPENDENT, result.getPrefixMatcher())) return;

    final Ref<Boolean> wereNonGrouped = Ref.create(false);
    final Map<String, LookupElement> methodNameToItem = CollectionFactory.linkedMap();
    final List<LookupElement> allMethodItems = CollectionFactory.arrayList();

    result.runRemainingContributors(parameters, new Consumer<LookupElement>() {
          public void consume(final LookupElement item) {
            item.putUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, null);
            Object o = item.getObject();
            if (item.getUserData(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null || !(o instanceof PsiMethod)) {
              result.addElement(item);
              wereNonGrouped.set(true);
              return;
            }

            allMethodItems.add(item);
            final PsiMethod method = (PsiMethod)o;
            final JavaChainLookupElement chain = item.as(JavaChainLookupElement.class);
            final String name = method.getName() + "#" + (chain == null ? "" : chain.getQualifier().getLookupString());
            final LookupElement existing = methodNameToItem.get(name);
            final ArrayList<PsiMethod> allMethods;
            if (existing != null) {
              if (((PsiMethod)existing.getObject()).getParameterList().getParametersCount() == 0 && method.getParameterList().getParametersCount() > 0) {
                methodNameToItem.put(name, item);
              }
              allMethods = (ArrayList<PsiMethod>)existing.getUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE);
            }
            else {
              methodNameToItem.put(name, item);
              allMethods = new ArrayList<PsiMethod>();
            }
            allMethods.add(method);
            item.putUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, allMethods);
          }

        });

    final boolean justOneMethodName = !wereNonGrouped.get() && methodNameToItem.size() == 1;
    if (justOneMethodName) {
      for (final LookupElement item : methodNameToItem.values()) {
        result.addElement(item);
      }
    }
    else {
      for (final LookupElement item : allMethodItems) {
        result.addElement(item);
      }
    }
  }

}
