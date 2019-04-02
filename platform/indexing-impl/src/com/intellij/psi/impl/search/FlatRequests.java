// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.model.search.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.TransformingQuery;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.model.search.SearchContext.IN_CODE;
import static com.intellij.model.search.SearchScopeOptimizer.CODE_USE_SCOPE_EP;

//public final class FlatRequests<T> {
//
//  Collection<Query<? extends T>> myQueries = new ArrayList<>();
//  Collection<QueryRequest<?, T>> myQueryRequests = new ArrayList<>();
//  Collection<SymbolReferenceSearchParameters> myParams = new ArrayList<>();
//  Collection<SearchWordRequest> myWords = new ArrayList<>();
//  Map<SearchWordRequest, ? extends Function<? super TextOccurrence, ? extends Collection<? extends T>>> myWordRequestsMap =
//    new LinkedHashMap<>();
//
//  <R> FlatRequests<R> apply(Function<? super T, ? extends Collection<? extends R>> transform) {
//    Collection<QueryRequest<?, R>> queryRequests = ContainerUtil.map(myQueries, it -> new QueryRequest<>(it, transform));
//    Collection<QueryRequest<?, R>> a = ContainerUtil.map(myQueryRequests, it -> it.apply(transform));
//  }

  //static <T> FlatRequests<T> flatten(Query<T> query) {
  //  if (query instanceof TransformingQuery) {
  //    return flatten((TransformingQuery<?, T>)query);
  //  }
  //  else if (query instanceof SymbolReferenceQuery) {
  //    // java cannot infer that T is SymbolReference
  //    // noinspection unchecked
  //    return (FlatRequests<T>)flatten((SymbolReferenceQuery)query);
  //  }
  //  else if (query instanceof SearchWordQuery) {
  //    return flatten((SearchWordQuery)query);
  //  }
  //  else {
  //    FlatRequests<T> requests = new FlatRequests<>();
  //    requests.myQueries.add(query);
  //    return requests;
  //  }
  //}
  //
  //@NotNull
  //private static <B, R> FlatRequests<R> flatten(@NotNull TransformingQuery<B, R> query) {
  //  return flatten(query.getBaseQuery()).apply(query.getTransform());
  //}
  //
  //@NotNull
  //private static FlatRequests<SymbolReference> flatten(@NotNull SymbolReferenceQuery query) {
  //  FlatRequests<SymbolReference> requests = new FlatRequests<>();
  //  requests.myQueries.add(query);
  //  requests.myParams.add(query.getParameters());
  //  return requests;
  //}
  //
  //@NotNull
  //private static <T> FlatRequests<T> flatten(@NotNull SearchWordQuery query) {
  //  FlatRequests<T> requests = new FlatRequests<>();
  //  requests.myWords.addAll(createRequests(query.getParameters()));
  //  return requests;
  //}
  //
  //@NotNull
  //private static Collection<SearchWordRequest> createRequests(@NotNull SearchWordParameters parameters) {
  //  SearchScope searchScope = parameters.getSearchScope();
  //  if (!makesSenseToSearch(searchScope)) {
  //    return Collections.emptyList();
  //  }
  //
  //  String word = parameters.getWord();
  //  Symbol targetHint = parameters.getTargetHint();
  //  Set<SearchContext> contexts = parameters.getSearchContexts();
  //  short contextMask = mask(contexts);
  //  boolean caseSensitive = parameters.isCaseSensitive();
  //
  //  if (targetHint != null && searchScope instanceof GlobalSearchScope && contexts.contains(IN_CODE)) {
  //    Project project = parameters.getProject();
  //    SearchScope restrictedCodeUsageSearchScope = getRestrictedScope(project, targetHint);
  //    if (restrictedCodeUsageSearchScope != null) {
  //      short nonCodeContextMask = (short)(contextMask ^ IN_CODE.mask);
  //      SearchScope codeScope = searchScope.intersectWith(restrictedCodeUsageSearchScope);
  //      SearchWordRequest codeRequest = new SearchWordRequest(word, codeScope, caseSensitive, IN_CODE.mask, null);
  //      SearchWordRequest nonCodeRequest = new SearchWordRequest(word, searchScope, caseSensitive, nonCodeContextMask, null);
  //      return Arrays.asList(codeRequest, nonCodeRequest);
  //    }
  //  }
  //  return Collections.singleton(new SearchWordRequest(word, searchScope, caseSensitive, contextMask, null));
  //}
  //
  //private static boolean makesSenseToSearch(@NotNull SearchScope searchScope) {
  //  if (searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).getScope().length == 0) {
  //    return false;
  //  }
  //  else {
  //    return searchScope != GlobalSearchScope.EMPTY_SCOPE;
  //  }
  //}
  //
  //private static short mask(@NotNull Set<SearchContext> contexts) {
  //  return (short)contexts.stream().mapToInt(context -> context.mask).reduce(0, (a, b) -> a | b);
  //}
  //
  //@Nullable
  //private static SearchScope getRestrictedScope(@NotNull Project project, @NotNull Symbol symbol) {
  //  return ReadAction.compute(() -> SymbolSearchHelperImplKt.getRestrictedScope(CODE_USE_SCOPE_EP.getExtensions(), project, symbol));
  //}
//}
//