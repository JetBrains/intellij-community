// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaCompletionSession {
  private final Set<String> myAddedClasses = new HashSet<>();
  private final Set<String> myKeywords = new HashSet<>();
  private final List<LookupElement> myBatchItems = new ArrayList<>();
  private final CompletionResultSet myResult;

  public JavaCompletionSession(CompletionResultSet result) {
    myResult = result;
  }

  void registerBatchItems(Collection<? extends LookupElement> elements) {
    myBatchItems.addAll(elements);
  }

  void flushBatchItems() {
    myResult.addAllElements(myBatchItems);
    myBatchItems.clear();
  }

  public void addClassItem(LookupElement lookupElement) {
    if (!myResult.getPrefixMatcher().prefixMatches(lookupElement)) return;

    registerClassFrom(lookupElement);
    myResult.addElement(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(lookupElement));
  }

  void registerClassFrom(LookupElement lookupElement) {
    PsiClass psiClass = extractClass(lookupElement);
    if (psiClass != null) {
      registerClass(psiClass);
    }
  }

  @NotNull PrefixMatcher getMatcher() {
    return myResult.getPrefixMatcher();
  }

  private static @Nullable PsiClass extractClass(LookupElement lookupElement) {
    final Object object = lookupElement.getObject();
    if (object instanceof PsiClass) {
      return (PsiClass)object;
    }
    if (object instanceof PsiMethod && ((PsiMethod)object).isConstructor()) {
      return ((PsiMethod)object).getContainingClass();
    }
    return null;
  }

  public void registerClass(@NotNull PsiClass psiClass) {
    ContainerUtil.addIfNotNull(myAddedClasses, getClassName(psiClass));
  }

  private static @Nullable String getClassName(@NotNull PsiClass psiClass) {
    String name = psiClass.getQualifiedName();
    return name == null ? psiClass.getName() : name;
  }

  public boolean alreadyProcessed(@NotNull LookupElement element) {
    final PsiClass psiClass = extractClass(element);
    return psiClass != null && alreadyProcessed(psiClass);
  }

  public boolean alreadyProcessed(@NotNull PsiClass object) {
    final String name = getClassName(object);
    return name == null || myAddedClasses.contains(name);
  }

  public boolean isKeywordAlreadyProcessed(@NotNull String keyword) {
    return myKeywords.contains(keyword);
  }

  void registerKeyword(@NotNull String keyword) {
    myKeywords.add(keyword);
  }
}
