/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Ref;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.AsyncConsumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class JavaMethodMergingContributor extends CompletionContributor{

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    final CompletionProvider<LookupElement, CompletionParameters> methodMerger =
        new CompletionProvider<LookupElement, CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters,
                                     final ProcessingContext context,
                                     @NotNull final CompletionResultSet<LookupElement> result) {
            final Ref<Boolean> wereNonGrouped = Ref.create(false);
            final Map<String, LookupItem<PsiMethod>> methodNameToItem = new LinkedHashMap<String, LookupItem<PsiMethod>>();
            final List<LookupItem<PsiMethod>> allMethodItems = new ArrayList<LookupItem<PsiMethod>>();
            result.setSuccessorFilter(new AsyncConsumer<LookupElement>() {
              public void consume(final LookupElement element) {
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
                String name = method.getName();
                LookupItem<PsiMethod> existing = methodNameToItem.get(name);
                ArrayList<PsiMethod> allMethods;
                if (existing != null) {
                  if (existing.getObject().getParameterList().getParametersCount() == 0 &&
                      method.getParameterList().getParametersCount() > 0) {
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

              public void finished() {
                final boolean justOneMethodName = !wereNonGrouped.get() && methodNameToItem.size() == 1;
                if (!CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS || justOneMethodName) {
                  for (final LookupItem<PsiMethod> item : methodNameToItem.values()) {
                    result.addElement(item);
                  }
                } else {
                  for (final LookupItem<PsiMethod> item : allMethodItems) {
                    result.addElement(item);
                  }
                }
              }
            });


          }
        };
    registrar.extend(CompletionType.BASIC, psiElement(), methodMerger);
    registrar.extend(CompletionType.SMART, psiElement(), methodMerger);
  }

}