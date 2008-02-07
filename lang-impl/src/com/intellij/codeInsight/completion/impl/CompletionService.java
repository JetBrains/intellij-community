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
import com.intellij.patterns.MatchingContext;
import com.intellij.psi.PsiElement;
import com.intellij.util.PrioritizedQueryExecutor;
import com.intellij.util.PrioritizedQueryFactory;
import com.intellij.util.QueryResultSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionService {
  private final PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> myBasicCompletionProviders = new PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> mySmartCompletionProviders = new PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final PartiallyOrderedSet<String,CompletionPlaceImpl<LookupElement, CompletionParameters>> myClassNameCompletionProviders = new PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>>();

  public CompletionService() {
    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      contributor.registerCompletionProviders(new CompletionRegistrar() {
        public CompletionPlace<LookupElement, CompletionParameters> extendBasicCompletion(final ElementPattern<? extends PsiElement> place) {
          return new CompletionPlaceImpl<LookupElement, CompletionParameters>(place, myBasicCompletionProviders);
        }

        public CompletionPlace<LookupElement, CompletionParameters> extendSmartCompletion(final ElementPattern<? extends PsiElement> place) {
          return new CompletionPlaceImpl<LookupElement, CompletionParameters>(place, mySmartCompletionProviders);
        }

        public CompletionPlace<LookupElement, CompletionParameters> extendClassNameCompletion(final ElementPattern<? extends PsiElement> place) {
          return new CompletionPlaceImpl<LookupElement, CompletionParameters>(place, myClassNameCompletionProviders);
        }
      });
    }
  }

  public PrioritizedQueryFactory<LookupElement, CompletionParameters> getBasicCompletionQueryFactory(final CompletionParameters queryParameters) {
    return createFactory(myBasicCompletionProviders, queryParameters);
  }

  private static PrioritizedQueryFactory<LookupElement, CompletionParameters> createFactory(final PartiallyOrderedSet<String, CompletionPlaceImpl<LookupElement, CompletionParameters>> providers, final CompletionParameters parameters) {
    final ArrayList<PrioritizedQueryExecutor<LookupElement, CompletionParameters>> list =
      new ArrayList<PrioritizedQueryExecutor<LookupElement, CompletionParameters>>();
    for (final CompletionPlaceImpl<LookupElement, CompletionParameters> place : providers.getValues()) {
      final MatchingContext matchingContext = new MatchingContext();
      if (place.myPlace.accepts(parameters.getPosition(), matchingContext)) {
        final CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final PrefixMatcher matcher = new CamelHumpMatcher(CompletionData.findPrefixStatic(parameters.getPosition(), context.startOffset));

        list.add(new PrioritizedQueryExecutor<LookupElement, CompletionParameters>() {
          public void execute(final CompletionParameters queryParameters, final QueryResultSet<LookupElement> resultSet) {
            place.myProvider.addCompletions(queryParameters, matchingContext, new CompletionResultSetImpl(matcher, resultSet, context));
          }

          public String toString() {
            return place.myId != null ? place.myId : place.myProvider.toString();
          }
        });
      }
    }
    return new PrioritizedQueryFactory<LookupElement, CompletionParameters>(list);
  }

  public PrioritizedQueryFactory<LookupElement, CompletionParameters> getSmartCompletionQueryFactory(final CompletionParameters queryParameters) {
    return createFactory(mySmartCompletionProviders, queryParameters);
  }

  public PrioritizedQueryFactory<LookupElement, CompletionParameters> getClassNameCompletionQueryFactory(final CompletionParameters queryParameters) {
    return createFactory(myClassNameCompletionProviders, queryParameters);
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
