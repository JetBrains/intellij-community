/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.AsyncConsumer;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class CompletionService implements CompletionRegistrar{
  private final List<CompletionPlaceImpl<LookupElement, CompletionParameters>> myBasicCompletionProviders = new ArrayList<CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final List<CompletionPlaceImpl<LookupElement, CompletionParameters>> mySmartCompletionProviders = new ArrayList<CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final List<CompletionPlaceImpl<LookupElement, CompletionParameters>> myClassNameCompletionProviders = new ArrayList<CompletionPlaceImpl<LookupElement, CompletionParameters>>();
  private final List<Pair<ElementPattern, CompletionAdvertiser>> myAdvertisers = new ArrayList<Pair<ElementPattern, CompletionAdvertiser>>();

  public CompletionService() {
    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      contributor.registerCompletionProviders(this);
    }
  }

  public void extend(final CompletionType type, final ElementPattern<? extends PsiElement> place, final CompletionProvider<LookupElement, CompletionParameters> provider) {
    getProviderSet(type).add(new CompletionPlaceImpl<LookupElement, CompletionParameters>(place, provider));
  }

  public void extend(final ElementPattern<? extends PsiElement> place, final CompletionAdvertiser advertiser) {
    myAdvertisers.add(new Pair<ElementPattern, CompletionAdvertiser>(place, advertiser));
  }

  private boolean executeForMatchingProviders(CompletionType type, final CompletionParameters queryParameters, PairProcessor<ProcessingContext,CompletionPlaceImpl<LookupElement,CompletionParameters>> processor) {
    for (final CompletionPlaceImpl<LookupElement, CompletionParameters> place : getProviderSet(type)) {
      final ProcessingContext processingContext = new ProcessingContext();
      if (place.myPlace.accepts(queryParameters.getPosition(), processingContext)) {
        if (!processor.process(processingContext, place)) return false;
      }
    }
    return true;
  }

  public void performAsyncCompletion(CompletionType type, final CompletionParameters queryParameters, AsyncConsumer<LookupElement> consumer) {
    final CompletionContext context = queryParameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
    final String prefix = CompletionData.findPrefixStatic(queryParameters.getPosition(), queryParameters.getOffset());
    final PrefixMatcher matcher = new CamelHumpMatcher(prefix);
    context.setPrefix(prefix);

    final Stack<AsyncConsumer<LookupElement>> stack = new Stack<AsyncConsumer<LookupElement>>();
    stack.push(consumer);
    executeForMatchingProviders(type, queryParameters, new PairProcessor<ProcessingContext, CompletionPlaceImpl<LookupElement, CompletionParameters>>() {
      public boolean process(final ProcessingContext processingContext, final CompletionPlaceImpl<LookupElement, CompletionParameters> place) {
        final Ref<Boolean> toContinue = Ref.create(true);
        final AsyncConsumer<LookupElement> consumer = stack.peek();
        place.myProvider.addCompletions(queryParameters, processingContext, new CompletionResultSet<LookupElement>(matcher) {
          public void addElement(@NotNull final LookupElement lookupElement) {
            if (lookupElement.getUserData(CompletionUtil.PREFIX_MATCHER) == null) {
              final PrefixMatcher matcher = getPrefixMatcher();
              if (!matcher.prefixMatches(lookupElement)) return;

              lookupElement.putUserData(CompletionUtil.PREFIX_MATCHER, matcher);
            }
            consumer.consume(lookupElement);
          }

          public void setSuccessorFilter(final AsyncConsumer<LookupElement> consumer) {
            stack.push(consumer);
          }

          public void stopHere() {
            toContinue.set(false);
          }

          public void setPrefixMatcher(@NotNull final PrefixMatcher prefixMatcher) {
            super.setPrefixMatcher(prefixMatcher);
            context.setPrefix(prefixMatcher.getPrefix());
          }


          public void setPrefixMatcher(@NotNull final String prefix) {
            setPrefixMatcher(new CamelHumpMatcher(prefix));
          }
        });

        return toContinue.get();
      }
    });
    while (!stack.isEmpty()) {
      stack.pop().finished();
    }
  }

  @Nullable
  public String getAdvertisementText(final CompletionParameters queryParameters) {
    for (final Pair<ElementPattern, CompletionAdvertiser> advertiser : myAdvertisers) {
      final ProcessingContext processingContext = new ProcessingContext();
      if (advertiser.first.accepts(queryParameters.getPosition(), processingContext)) {
        final String s = advertiser.second.advertise(queryParameters, processingContext);
        if (s != null) return s;
      }
    }
    return null;
  }
  @Nullable
  public String getEmptyLookupText(final CompletionParameters queryParameters) {
    for (final Pair<ElementPattern, CompletionAdvertiser> advertiser : myAdvertisers) {
      final ProcessingContext processingContext = new ProcessingContext();
      if (advertiser.first.accepts(queryParameters.getPosition(), processingContext)) {
        final String s = advertiser.second.handleEmptyLookup(queryParameters, processingContext);
        if (s != null) return s;
      }
    }
    return null;
  }

  private List<CompletionPlaceImpl<LookupElement, CompletionParameters>> getProviderSet(CompletionType type) {
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

  public static class CompletionPlaceImpl<Result, Params extends CompletionParameters> {
    private final ElementPattern myPlace;
    private final CompletionProvider<Result, Params> myProvider;

    public CompletionPlaceImpl(final ElementPattern place, final CompletionProvider<Result, Params> provider) {
      myPlace = place;
      myProvider = provider;
    }
  }

}
