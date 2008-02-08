/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.PrioritizedQueryExecutor;
import com.intellij.util.PrioritizedQueryFactory;
import com.intellij.util.ProcessingContext;
import com.intellij.util.QueryResultSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionService implements CompletionRegistrar{
  private final PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> myBasicCompletionProviders = new PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> mySmartCompletionProviders = new PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> myClassNameCompletionProviders = new PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>>();

  public CompletionService() {
    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      contributor.registerCompletionProviders(this);
    }
  }

  public CompletionPlace<LookupElement, CompletionParameters> extend(final CompletionType type,
                                                                                   final ElementPattern<? extends PsiElement> place) {
    return new CompletionPlaceImpl<LookupElement,CompletionParameters>(place, getProviderSet(type));
  }

  public PrioritizedQueryFactory<LookupElement, CompletionParameters> getQueryFactory(CompletionType type, final CompletionParameters queryParameters) {
    final ArrayList<PrioritizedQueryExecutor<LookupElement, CompletionParameters>> list = new ArrayList<PrioritizedQueryExecutor<LookupElement, CompletionParameters>>();
    for (final CompletionPlaceImpl<LookupElement, CompletionParameters> place : getProviderSet(type).getValues()) {
      final ProcessingContext processingContext = new ProcessingContext();
      if (place.myPlace.accepts(queryParameters.getPosition(), processingContext)) {
        final CompletionContext context = queryParameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final PrefixMatcher matcher = new CamelHumpMatcher(CompletionData.findPrefixStatic(queryParameters.getPosition(), context.startOffset));

        list.add(new PrioritizedQueryExecutor<LookupElement, CompletionParameters>() {
          public void execute(final CompletionParameters parameters1, final QueryResultSet<LookupElement> resultSet) {
            place.myProvider.addCompletions(parameters1, processingContext, new CompletionResultSetImpl(matcher, resultSet, context));
          }

          public String toString() {
            return place.myId != null ? place.myId : place.myProvider.toString();
          }
        });
      }
    }
    return new PrioritizedQueryFactory<LookupElement, CompletionParameters>(list);
  }

  private PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> getProviderSet(CompletionType type) {
    switch (type) {
      case BASIC: return myBasicCompletionProviders;
      case SMART: return mySmartCompletionProviders;
      case CLASS_NAME: return myClassNameCompletionProviders;
    }
    throw new AssertionError(type);
  }

  public static CompletionService getCompletionService() {
    return ServiceManager.getService(CompletionService.class);
  }

  public static class CompletionPlaceImpl<Result, Params extends CompletionParameters> implements CompletionPlace<Result,Params> {
    private final ElementPattern myPlace;
    private final PartiallyOrderedSet<String, CompletionPlaceImpl<Result, Params>> myProviders;
    private String myId;
    private CompletionProvider<Result, Params> myProvider;

    protected CompletionPlaceImpl(final ElementPattern place, PartiallyOrderedSet<String, CompletionPlaceImpl<Result,Params>> providers) {
      myPlace = place;
      myProviders = providers;
    }

    public CompletionPlace<Result, Params> withId(@NonNls @NotNull final String id) {
      myId = id;
      myProviders.addValue(myId, this);
      return this;
    }

    public CompletionPlace<Result, Params> dependingOn(@NonNls final String... dependentIds) {
      myProviders.addValue(myId, this);
      for (final String dependentId : dependentIds) {
        myProviders.addRelation(this, dependentId);
      }
      return this;
    }

    public void withProvider(final CompletionProvider<Result, Params> provider) {
      myProviders.addValue(myId, this);
      myProvider = provider;
    }

    public String toString() {
      return String.valueOf((myId != null ? myId : myProvider));
    }
  }

}
