/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.*;
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

  private boolean executeForMatchingProviders(CompletionType type, final CompletionParameters queryParameters, PairProcessor<ProcessingContext,CompletionPlaceImpl<LookupElement,CompletionParameters>> processor) {
    for (final CompletionPlaceImpl<LookupElement, CompletionParameters> place : getProviderSet(type).getValues()) {
      final ProcessingContext processingContext = new ProcessingContext();
      if (place.myPlace.accepts(queryParameters.getPosition(), processingContext)) {
        if (!processor.process(processingContext, place)) return false;
      }
    }
    return true;
  }

  public PrioritizedQueryFactory<LookupElement, CompletionParameters> getQueryFactory(CompletionType type, final CompletionParameters queryParameters) {
    final CompletionContext context = queryParameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
    final PrefixMatcher matcher = new CamelHumpMatcher(CompletionData.findPrefixStatic(queryParameters.getPosition(), context.getStartOffset()));

    final ArrayList<PrioritizedQueryExecutor<LookupElement, CompletionParameters>> list = new ArrayList<PrioritizedQueryExecutor<LookupElement, CompletionParameters>>();
    executeForMatchingProviders(type, queryParameters, new PairProcessor<ProcessingContext, CompletionPlaceImpl<LookupElement, CompletionParameters>>() {
      public boolean process(final ProcessingContext processingContext, final CompletionPlaceImpl<LookupElement, CompletionParameters> place) {
        list.add(new PrioritizedQueryExecutor<LookupElement, CompletionParameters>() {
          public void execute(final CompletionParameters parameters, final QueryResultSet<LookupElement> resultSet) {
            place.myProvider.addCompletions(parameters, processingContext, new CompletionResultSetImpl(matcher, resultSet, context));
          }

          public String toString() {
            return place.myId != null ? place.myId : place.myProvider.toString();
          }
        });
        return true;
      }
    });
    return new PrioritizedQueryFactory<LookupElement, CompletionParameters>(list);
  }

  public String getAdvertisementText(CompletionType type, final CompletionParameters queryParameters) {
    final CompletionContext context = queryParameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
    final PrefixMatcher matcher = new CamelHumpMatcher(CompletionData.findPrefixStatic(queryParameters.getPosition(), context.getStartOffset()));
    final Ref<String> result = Ref.create(null);
    executeForMatchingProviders(type, queryParameters, new PairProcessor<ProcessingContext, CompletionPlaceImpl<LookupElement, CompletionParameters>>() {
      public boolean process(final ProcessingContext processingContext, final CompletionPlaceImpl<LookupElement, CompletionParameters> place) {
        final String ad = place.myAdvertiser.advertise(queryParameters, processingContext, matcher);
        result.set(ad);
        return ad == null;
      }
    });
    return result.get();
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
    private CompletionProvider<Result, Params> myProvider = CompletionProvider.EMPTY_PROVIDER;
    private CompletionAdvertiser myAdvertiser = CompletionAdvertiser.EMPTY_ADVERTISER;

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

    public CompletionPlace<Result, Params> withProvider(final CompletionProvider<Result, Params> provider) {
      myProviders.addValue(myId, this);
      myProvider = provider;
      return this;
    }

    public CompletionPlace<Result, Params> withAdvertiser(final CompletionAdvertiser advertiser) {
      myProviders.addValue(myId, this);
      myAdvertiser = advertiser;
      return this;
    }

    public String toString() {
      return String.valueOf((myId != null ? myId : myProvider));
    }
  }

}
