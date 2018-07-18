/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
* @author peter
*/
public class JavaCompletionSession {
  private final Set<String> myAddedClasses = new HashSet<>();
  private final Set<String> myKeywords = new HashSet<>();
  private final MultiMap<CompletionResultSet, LookupElement> myBatchItems = MultiMap.create(); 
  private final CompletionResultSet myResult;

  public JavaCompletionSession(CompletionResultSet result) {
    myResult = result;
  }

  void registerBatchItems(CompletionResultSet result, Collection<LookupElement> elements) {
    myBatchItems.putValues(result, elements);
  }

  void flushBatchItems() {
    for (Map.Entry<CompletionResultSet, Collection<LookupElement>> entry : myBatchItems.entrySet()) {
      entry.getKey().addAllElements(entry.getValue());
    }
    myBatchItems.clear();
  }

  public void addClassItem(LookupElement lookupElement) {
    if (!myResult.getPrefixMatcher().prefixMatches(lookupElement)) return;
    
    PsiClass psiClass = extractClass(lookupElement);
    if (psiClass != null) {
      registerClass(psiClass);
    }
    myResult.addElement(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(lookupElement));
  }

  @NotNull PrefixMatcher getMatcher() {
    return myResult.getPrefixMatcher();
  }

  @Nullable private static PsiClass extractClass(LookupElement lookupElement) {
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

  @Nullable
  private static String getClassName(@NotNull PsiClass psiClass) {
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
