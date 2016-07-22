/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author peter
*/
public class SearchRequestCollector {
  private final Object lock = new Object();
  private final List<PsiSearchRequest> myWordRequests = ContainerUtil.newArrayList();
  private final List<QuerySearchRequest> myQueryRequests = ContainerUtil.newArrayList();
  private final List<Processor<Processor<PsiReference>>> myCustomSearchActions = ContainerUtil.newArrayList();
  private final SearchSession mySession;

  public SearchRequestCollector(@NotNull SearchSession session) {
    mySession = session;
  }

  @NotNull
  public SearchSession getSearchSession() {
    return mySession;
  }

  public void searchWord(@NotNull String word, @NotNull SearchScope searchScope, boolean caseSensitive, @NotNull PsiElement searchTarget) {
    final short searchContext = (short)(UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_COMMENTS
                                | (searchTarget instanceof PsiFileSystemItem ? UsageSearchContext.IN_STRINGS : 0));
    searchWord(word, searchScope, searchContext, caseSensitive, searchTarget);
  }

  public void searchWord(@NotNull String word,
                         @NotNull SearchScope searchScope,
                         short searchContext,
                         boolean caseSensitive,
                         @NotNull PsiElement searchTarget) {
    searchWord(word, searchScope, searchContext, caseSensitive, getContainerName(searchTarget), new SingleTargetRequestResultProcessor(searchTarget));
  }

  private void searchWord(@NotNull String word,
                          @NotNull SearchScope searchScope,
                          short searchContext,
                          boolean caseSensitive,
                          String containerName,
                          @NotNull RequestResultProcessor processor) {
    if (!makesSenseToSearch(word, searchScope)) return;
    synchronized (lock) {
      PsiSearchRequest request = new PsiSearchRequest(searchScope, word, searchContext, caseSensitive, containerName, processor);
      myWordRequests.add(request);
    }
  }
  public void searchWord(@NotNull String word,
                          @NotNull SearchScope searchScope,
                          short searchContext,
                          boolean caseSensitive,
                          @NotNull PsiElement searchTarget,
                          @NotNull RequestResultProcessor processor) {
    searchWord(word, searchScope, searchContext, caseSensitive, getContainerName(searchTarget), processor);
  }

  private static String getContainerName(@NotNull final PsiElement target) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        PsiElement container = getContainer(target);
        return container instanceof PsiNamedElement ? ((PsiNamedElement)container).getName() : null;
      }
    });
  }

  private static PsiElement getContainer(@NotNull PsiElement refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      final PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  /** use {@link #searchWord(java.lang.String, com.intellij.psi.search.SearchScope, short, boolean, com.intellij.psi.PsiElement)}
   * instead
   */
  @Deprecated
  public void searchWord(@NotNull String word,
                         @NotNull SearchScope searchScope,
                         short searchContext,
                         boolean caseSensitive,
                         @NotNull RequestResultProcessor processor) {
    searchWord(word, searchScope, searchContext, caseSensitive, (String)null, processor);
  }

  private static boolean makesSenseToSearch(@NotNull String word, @NotNull SearchScope searchScope) {
    if (searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).getScope().length == 0) {
      return false;
    }
    if (searchScope == GlobalSearchScope.EMPTY_SCOPE) {
      return false;
    }
    return !StringUtil.isEmpty(word);
  }

  public void searchQuery(@NotNull QuerySearchRequest request) {
    assert request.collector != this;
    assert request.collector.getSearchSession() == mySession;
    synchronized (lock) {
      myQueryRequests.add(request);
    }
  }

  public void searchCustom(@NotNull Processor<Processor<PsiReference>> searchAction) {
    synchronized (lock) {
      myCustomSearchActions.add(searchAction);
    }
  }

  @NotNull
  public List<QuerySearchRequest> takeQueryRequests() {
    return takeRequests(myQueryRequests);
  }

  @NotNull
  private <T> List<T> takeRequests(@NotNull List<T> list) {
    synchronized (lock) {
      final List<T> requests = new ArrayList<>(list);
      list.clear();
      return requests;
    }
  }

  @NotNull
  public List<PsiSearchRequest> takeSearchRequests() {
    return takeRequests(myWordRequests);
  }

  @NotNull
  public List<Processor<Processor<PsiReference>>> takeCustomSearchActions() {
    return takeRequests(myCustomSearchActions);
  }

  @Override
  public String toString() {
    return myWordRequests.toString().replace(',', '\n') + ";" + myQueryRequests;
  }
}
