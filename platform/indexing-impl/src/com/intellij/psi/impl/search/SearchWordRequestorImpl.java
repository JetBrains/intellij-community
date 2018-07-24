// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.Symbol;
import com.intellij.model.search.OccurrenceSearchRequestor;
import com.intellij.model.search.SearchContext;
import com.intellij.model.search.SearchWordRequestor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.model.search.SearchContext.*;
import static com.intellij.model.search.SearchScopeOptimizer.CODE_USE_SCOPE_EP;

final class SearchWordRequestorImpl implements SearchWordRequestor {

  private final @NotNull SearchRequestCollectorImpl myCollector;
  private final @NotNull String myWord;

  private SearchScope mySearchScope;
  private FileType[] myFileTypes;
  private boolean myCaseSensitive = true;
  private Set<SearchContext> mySearchContexts;
  private Symbol myTargetHint;

  SearchWordRequestorImpl(@NotNull SearchRequestCollectorImpl collector, @NotNull String word) {
    myCollector = collector;
    myWord = word;
  }

  @NotNull
  private SearchScope getSearchScope() {
    SearchScope scope = mySearchScope != null ? mySearchScope : myCollector.getParameters().getEffectiveSearchScope();
    if (myFileTypes != null && myFileTypes.length > 0) {
      return PsiSearchScopeUtil.restrictScopeTo(scope, myFileTypes);
    }
    else {
      return scope;
    }
  }

  @NotNull
  @Override
  public SearchWordRequestor inScope(@NotNull SearchScope searchScope) {
    mySearchScope = searchScope;
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor restrictScopeTo(@NotNull FileType... fileTypes) {
    myFileTypes = fileTypes;
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor caseInsensitive() {
    myCaseSensitive = false;
    return this;
  }

  private Set<SearchContext> getSearchContexts() {
    if (mySearchContexts != null) {
      return mySearchContexts;
    }
    else {
      EnumSet<SearchContext> result = EnumSet.of(IN_CODE, IN_FOREIGN_LANGUAGES, IN_COMMENTS);
      if (myTargetHint instanceof PsiFileSystemItem) {
        result.add(IN_STRINGS);
      }
      return result;
    }
  }

  @NotNull
  @Override
  public SearchWordRequestor inContexts(@NotNull SearchContext context, @NotNull SearchContext... otherContexts) {
    mySearchContexts = EnumSet.of(context, otherContexts);
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor inAllContexts() {
    mySearchContexts = EnumSet.allOf(SearchContext.class);
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor withTargetHint(@NotNull Symbol target) {
    myTargetHint = target;
    return this;
  }

  @Override
  public void searchRequests(@NotNull OccurrenceSearchRequestor occurrenceSearchRequestor) {
    searchRequests((element, offsetInElement) -> {
      occurrenceSearchRequestor.collectRequests(myCollector, element, offsetInElement);
      return true;
    });
  }

  public void searchRequests(@NotNull TextOccurenceProcessor processor) {
    myCollector.searchWord(createRequests(this), processor);
  }

  @Override
  public void search(@NotNull Symbol target) {
    withTargetHint(target);
    search(processor -> new SingleTargetOccurrenceProcessor(target, processor));
  }

  public void search(@NotNull TextOccurenceProcessorProvider f) {
    myCollector.searchWord(createRequests(this), f);
  }

  @NotNull
  private Collection<SearchWordRequest> createRequests(@NotNull SearchWordRequestorImpl requestor) {
    SearchScope searchScope = requestor.getSearchScope();
    if (!makesSenseToSearch(searchScope)) {
      return Collections.emptyList();
    }

    String word = requestor.myWord;
    Symbol targetHint = requestor.myTargetHint;
    Set<SearchContext> contexts = requestor.getSearchContexts();
    short contextMask = mask(contexts);
    boolean caseSensitive = requestor.myCaseSensitive;

    if (targetHint != null && searchScope instanceof GlobalSearchScope && contexts.contains(IN_CODE)) {
      Project project = myCollector.getParameters().getProject();
      SearchScope restrictedCodeUsageSearchScope = getRestrictedScope(project, targetHint);
      if (restrictedCodeUsageSearchScope != null) {
        short nonCodeContextMask = (short)(contextMask ^ IN_CODE.mask);
        SearchScope codeScope = searchScope.intersectWith(restrictedCodeUsageSearchScope);
        SearchWordRequest codeRequest = new SearchWordRequest(word, codeScope, caseSensitive, IN_CODE.mask, null);
        SearchWordRequest nonCodeRequest = new SearchWordRequest(word, searchScope, caseSensitive, nonCodeContextMask, null);
        return Arrays.asList(codeRequest, nonCodeRequest);
      }
    }
    return Collections.singleton(new SearchWordRequest(word, searchScope, caseSensitive, contextMask, null));
  }

  private static boolean makesSenseToSearch(@NotNull SearchScope searchScope) {
    if (searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).getScope().length == 0) {
      return false;
    }
    else {
      return searchScope != GlobalSearchScope.EMPTY_SCOPE;
    }
  }

  private static short mask(@NotNull Set<SearchContext> contexts) {
    return (short)contexts.stream().mapToInt(context -> context.mask).reduce(0, (a, b) -> a | b);
  }

  @Nullable
  private static SearchScope getRestrictedScope(@NotNull Project project, @NotNull Symbol symbol) {
    return ReadAction.compute(() -> SymbolSearchHelperImpl.getRestrictedScope(CODE_USE_SCOPE_EP.getExtensions(), project, symbol));
  }
}
