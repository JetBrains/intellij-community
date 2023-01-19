// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SearchRequestCollector {
  private final Object lock = new Object();
  private final List<PsiSearchRequest> myWordRequests = new ArrayList<>();
  private final List<QuerySearchRequest> myQueryRequests = new ArrayList<>();
  private final List<Processor<? super Processor<? super PsiReference>>> myCustomSearchActions = new ArrayList<>();
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
                                | ((searchTarget instanceof PsiFileSystemItem || searchTarget instanceof PsiDirectoryContainer) ? UsageSearchContext.IN_STRINGS : 0));
    searchWord(word, searchScope, searchContext, caseSensitive, searchTarget);
  }

  public void searchWord(@NotNull String word,
                         @NotNull SearchScope searchScope,
                         short searchContext,
                         boolean caseSensitive,
                         @NotNull PsiElement searchTarget) {
    searchWord(word, searchScope, searchContext, caseSensitive, getContainerName(searchTarget), searchTarget,
               new SingleTargetRequestResultProcessor(searchTarget));
  }

  private void searchWord(@NotNull String word,
                          @NotNull SearchScope searchScope,
                          short searchContext,
                          boolean caseSensitive,
                          String containerName,
                          PsiElement searchTarget,
                          @NotNull RequestResultProcessor processor) {
    if (!makesSenseToSearch(word, searchScope)) return;

    if (searchTarget != null &&
        searchScope instanceof GlobalSearchScope &&
        ((searchContext & UsageSearchContext.IN_CODE) != 0 || searchContext == UsageSearchContext.ANY)) {

      SearchScope restrictedCodeUsageSearchScope = ReadAction.compute(() -> ScopeOptimizer.calculateOverallRestrictedUseScope(
        PsiSearchHelper.CODE_USAGE_SCOPE_OPTIMIZER_EP_NAME.getExtensions(), searchTarget));
      if (restrictedCodeUsageSearchScope != null) {
        short exceptCodeSearchContext = searchContext == UsageSearchContext.ANY
                                        ? UsageSearchContext.IN_COMMENTS |
                                          UsageSearchContext.IN_STRINGS |
                                          UsageSearchContext.IN_FOREIGN_LANGUAGES |
                                          UsageSearchContext.IN_PLAIN_TEXT
                                        : (short)(searchContext ^ UsageSearchContext.IN_CODE);
        SearchScope searchCodeUsageEffectiveScope = searchScope.intersectWith(restrictedCodeUsageSearchScope);

        PsiSearchRequest inCode =
          new PsiSearchRequest(searchCodeUsageEffectiveScope, word, UsageSearchContext.IN_CODE, caseSensitive, containerName,
                               getSearchSession(), processor);
        PsiSearchRequest outsideCode =
          new PsiSearchRequest(searchScope, word, exceptCodeSearchContext, caseSensitive, containerName, getSearchSession(), processor);
        synchronized (lock) {
          myWordRequests.add(inCode);
          myWordRequests.add(outsideCode);
        }
        return;
      }
    }
    PsiSearchRequest request = new PsiSearchRequest(searchScope, word, searchContext, caseSensitive, containerName, getSearchSession(), processor);
    synchronized (lock) {
      myWordRequests.add(request);
    }
  }
  public void searchWord(@NotNull String word,
                          @NotNull SearchScope searchScope,
                          short searchContext,
                          boolean caseSensitive,
                          @NotNull PsiElement searchTarget,
                          @NotNull RequestResultProcessor processor) {
    searchWord(word, searchScope, searchContext, caseSensitive, getContainerName(searchTarget), searchTarget, processor);
  }

  private static String getContainerName(@NotNull final PsiElement target) {
    return ReadAction.compute(() -> {
      PsiElement container = getContainer(target);
      return container instanceof PsiNamedElement ? ((PsiNamedElement)container).getName() : null;
    });
  }

  private static PsiElement getContainer(@NotNull PsiElement refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      final PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    // it's assumed that in the general case of unknown language the .getParent() will lead to reparse,
    // (all these Javascript stubbed methods under non-stubbed block statements under stubbed classes - meh)
    // so just return null instead of refElement.getParent() here to avoid making things worse.
    return null;
  }

  /**
   * @deprecated use {@link #searchWord(String, SearchScope, short, boolean, PsiElement)}
   */
  @Deprecated
  public void searchWord(@NotNull String word,
                         @NotNull SearchScope searchScope,
                         short searchContext,
                         boolean caseSensitive,
                         @NotNull RequestResultProcessor processor) {
    searchWord(word, searchScope, searchContext, caseSensitive, null, null, processor);
  }

  private static boolean makesSenseToSearch(@NotNull String word, @NotNull SearchScope searchScope) {
    if (searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).getScope().length == 0) {
      return false;
    }
    return !SearchScope.isEmptyScope(searchScope) && !StringUtil.isEmpty(word);
  }

  public void searchQuery(@NotNull QuerySearchRequest request) {
    assert request.collector != this;
    assert request.collector.getSearchSession() == mySession;
    synchronized (lock) {
      myQueryRequests.add(request);
    }
  }

  public void searchCustom(@NotNull Processor<? super Processor<? super PsiReference>> searchAction) {
    synchronized (lock) {
      myCustomSearchActions.add(searchAction);
    }
  }

  @NotNull
  public List<QuerySearchRequest> takeQueryRequests() {
    return takeRequests(myQueryRequests);
  }

  @NotNull
  private <T> List<T> takeRequests(@NotNull List<? extends T> list) {
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
  public List<Processor<? super Processor<? super PsiReference>>> takeCustomSearchActions() {
    return takeRequests(myCustomSearchActions);
  }

  @Override
  public String toString() {
    return myWordRequests.toString().replace(',', '\n') + ";" + myQueryRequests;
  }
}
