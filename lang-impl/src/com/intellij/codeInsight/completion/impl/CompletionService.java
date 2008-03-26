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
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.AsyncConsumer;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  public void performAsyncCompletion(CompletionType type, final CompletionParameters queryParameters, AsyncConsumer<LookupElement> consumer) {
    final CompletionContext context = queryParameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
    final String prefix = CompletionData.findPrefixStatic(queryParameters.getPosition(), context.getStartOffset());
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
      myId = super.toString();
    }

    public CompletionPlace<Result, Params> withId(@NonNls @NotNull final String id) {
      myId = id;
      myProviders.addValue(myId, this);
      return this;
    }

    public CompletionPlace<Result, Params> dependingOn(@NonNls final String... dependentIds) {
      myProviders.addValue(myId, this);
      for (final String dependentId : dependentIds) {
        myProviders.addRelation(myId, dependentId);
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
