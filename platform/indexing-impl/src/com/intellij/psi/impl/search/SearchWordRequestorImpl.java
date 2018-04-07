// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.ModelElement;
import com.intellij.model.search.OccurenceSearchRequestor;
import com.intellij.model.search.SearchWordRequestor;
import com.intellij.model.search.TextOccurenceProcessorProvider;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.psi.search.UsageSearchContext.*;
import static com.intellij.util.ObjectUtils.notNull;

final class SearchWordRequestorImpl implements SearchWordRequestor {

  private final @NotNull SearchSessionImpl mySession;
  private final @NotNull String myWord;
  private final @NotNull SearchScope mySearchScope;
  private boolean myCaseSensitive = true;
  private Short mySearchContext;
  private ModelElement myTargetHint;

  SearchWordRequestorImpl(@NotNull SearchSessionImpl session, @NotNull String word, @NotNull SearchScope scope) {
    mySession = session;
    myWord = word;
    mySearchScope = scope;
  }

  @NotNull
  @Override
  public SearchWordRequestor setCaseSensitive(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor setSearchContext(short searchContext) {
    mySearchContext = searchContext;
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor setTargetHint(@NotNull ModelElement target) {
    myTargetHint = target;
    return this;
  }

  @Override
  public void searchRequests(@NotNull OccurenceSearchRequestor occurenceSearchRequestor) {
    searchRequests((element, offsetInElement) -> {
      occurenceSearchRequestor.collectRequests(mySession, element, offsetInElement);
      return true;
    });
  }

  // TODO pull up to interface ?
  public void searchRequests(@NotNull TextOccurenceProcessor processor) {
    mySession.searchWord(createRequests(this), processor);
  }

  @Override
  public void search(@NotNull ModelElement target) {
    setTargetHint(target);
    search(processor -> new SingleTargetOccurenceProcessor(target, processor));
  }

  // TODO pull up to interface ?
  public void search(@NotNull TextOccurenceProcessorProvider f) {
    mySession.searchWord(createRequests(this), f);
  }

  @NotNull
  private static Collection<SearchWordRequest> createRequests(@NotNull SearchWordRequestorImpl requestor) {
    String word = requestor.myWord;
    ModelElement targetHint = requestor.myTargetHint;
    SearchScope searchScope = requestor.mySearchScope;
    short searchContext = notNull(requestor.mySearchContext, requestor::getDefaultSearchContext);
    boolean caseSensitive = requestor.myCaseSensitive;

    if (targetHint != null && searchScope instanceof GlobalSearchScope && (searchContext & IN_CODE) != 0) {
      SearchScope restrictedCodeUsageSearchScope = null;
      // ReadAction.compute(() -> ScopeOptimizer.calculateOverallRestrictedUseScope(CODE_USAGE_SCOPE_OPTIMIZER_EP_NAME.getExtensions(), searchTarget));
      //noinspection ConstantConditions
      if (restrictedCodeUsageSearchScope != null) {
        short nonCodeSearchContext = searchContext == ANY ? IN_COMMENTS | IN_STRINGS | IN_FOREIGN_LANGUAGES | IN_PLAIN_TEXT
                                                          : (short)(searchContext ^ IN_CODE);
        SearchScope codeScope = searchScope.intersectWith(restrictedCodeUsageSearchScope);
        SearchWordRequest codeRequest = new SearchWordRequest(
          word, codeScope, caseSensitive, IN_CODE, null
        );
        SearchWordRequest nonCodeRequest = new SearchWordRequest(
          word, searchScope, caseSensitive, nonCodeSearchContext, null
        );

        return Arrays.asList(codeRequest, nonCodeRequest);
      }
    }
    return Collections.singleton(new SearchWordRequest(word, searchScope, caseSensitive, searchContext, null));
  }

  private short getDefaultSearchContext() {
    int context = IN_CODE | IN_FOREIGN_LANGUAGES | IN_COMMENTS;
    return (short)(context | (myTargetHint instanceof PsiFileSystemItem ? IN_STRINGS : 0));
  }
}
