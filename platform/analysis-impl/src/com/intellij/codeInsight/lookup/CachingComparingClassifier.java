// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.ForceableComparable;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class CachingComparingClassifier extends ComparingClassifier<LookupElement> {
  private final Map<LookupElement, Comparable> myWeights = new IdentityHashMap<>();
  private final LookupElementWeigher myWeigher;
  private Ref<Comparable> myFirstWeight;
  private volatile boolean myPrimitive = true;
  private int myPrefixChanges = -1;

  public CachingComparingClassifier(Classifier<LookupElement> next, LookupElementWeigher weigher) {
    super(next, weigher.toString(), weigher.isNegated());
    myWeigher = weigher;
  }

  @Override
  public final @Nullable Comparable getWeight(LookupElement element, ProcessingContext context) {
    Comparable w = myWeights.get(element);
    if (w == null && myWeigher.isPrefixDependent()) {
      myWeights.put(element, w = myWeigher.weigh(element, context.get(CompletionLookupArranger.WEIGHING_CONTEXT)));
    }
    return w;
  }

  @Override
  public void removeElement(@NotNull LookupElement element, @NotNull ProcessingContext context) {
    synchronized (this) {
      myWeights.remove(element);
    }
    super.removeElement(element, context);
  }

  @Override
  public @NotNull Iterable<LookupElement> classify(@NotNull Iterable<? extends LookupElement> source, @NotNull ProcessingContext context) {
    if (!myWeigher.isPrefixDependent() && myPrimitive) {
      return myNext.classify(source, context);
    }
    checkPrefixChanged(context);

    return super.classify(source, context);
  }

  private synchronized void checkPrefixChanged(ProcessingContext context) {
    int actualPrefixChanges = context.get(CompletionLookupArranger.PREFIX_CHANGES);
    if (myWeigher.isPrefixDependent() && myPrefixChanges != actualPrefixChanges) {
      myPrefixChanges = actualPrefixChanges;
      myWeights.clear();
    }
  }

  @Override
  public @NotNull List<Pair<LookupElement, Object>> getSortingWeights(@NotNull Iterable<? extends LookupElement> items, @NotNull ProcessingContext context) {
    checkPrefixChanged(context);
    return super.getSortingWeights(items, context);
  }

  @Override
  public void addElement(@NotNull LookupElement t, @NotNull ProcessingContext context) {
    Comparable<?> weight = myWeigher.weigh(t, context.get(CompletionLookupArranger.WEIGHING_CONTEXT));
    if (weight instanceof ForceableComparable) {
      ((ForceableComparable)weight).force();
    }
    synchronized (this) {
      if (!myWeigher.isPrefixDependent() && myPrimitive) {
        if (myFirstWeight == null) {
          myFirstWeight = Ref.create(weight);
        } else if (!Comparing.equal(myFirstWeight.get(), weight)) {
          myPrimitive = false;
        }
      }
      myWeights.put(t, weight);
    }
    super.addElement(t, context);
  }

}
