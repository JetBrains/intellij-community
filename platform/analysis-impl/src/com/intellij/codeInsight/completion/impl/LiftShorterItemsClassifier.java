// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.*;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author peter
*/
public final class LiftShorterItemsClassifier extends Classifier<LookupElement> {
  private final TreeSet<String> mySortedStrings = new TreeSet<>();
  private final MultiMap<String, LookupElement> myElements = createMultiMap(false);
  private final MultiMap<LookupElement, LookupElement> myToLift = createMultiMap(true);
  private final MultiMap<LookupElement, LookupElement> myReversedToLift = createMultiMap(true);
  private final LiftingCondition myCondition;
  private final boolean myLiftBefore;
  private int myCount = 0;

  public LiftShorterItemsClassifier(String name, Classifier<LookupElement> next, LiftingCondition condition, boolean liftBefore) {
    super(next, name);
    myCondition = condition;
    myLiftBefore = liftBefore;
  }

  @Override
  public void addElement(@NotNull LookupElement added, @NotNull ProcessingContext context) {
    myCount++;

    for (String string : CompletionUtil.iterateLookupStrings(added)) {
      if (string.length() == 0) continue;

      myElements.putValue(string, added);
      mySortedStrings.add(string);
      final NavigableSet<String> after = mySortedStrings.tailSet(string, false);
      for (String s : after) {
        if (!s.startsWith(string)) {
          break;
        }
        for (LookupElement longer : myElements.get(s)) {
          updateLongerItem(added, longer);
        }
      }
    }
    super.addElement(added, context);

    calculateToLift(added);
  }

  private void updateLongerItem(LookupElement shorter, LookupElement longer) {
    if (myCondition.shouldLift(shorter, longer)) {
      myToLift.putValue(longer, shorter);
      myReversedToLift.putValue(shorter, longer);
    }
  }

  private void calculateToLift(LookupElement element) {
    for (String string : CompletionUtil.iterateLookupStrings(element)) {
      for (int len = 1; len < string.length(); len++) {
        String prefix = string.substring(0, len);
        for (LookupElement shorterElement : myElements.get(prefix)) {
          if (myCondition.shouldLift(shorterElement, element)) {
            myToLift.putValue(element, shorterElement);
            myReversedToLift.putValue(shorterElement, element);
          }
        }
      }
    }
  }

  @NotNull
  @Override
  public Iterable<LookupElement> classify(@NotNull Iterable<? extends LookupElement> source, @NotNull ProcessingContext context) {
    return liftShorterElements(source, null, context);
  }

  private Iterable<LookupElement> liftShorterElements(Iterable<? extends LookupElement> source,
                                                      @Nullable Set<? super LookupElement> lifted, final ProcessingContext context) {
    Set<LookupElement> srcSet = new ReferenceOpenHashSet<>(source instanceof Collection ? ((Collection<?>)source).size() : myCount);
    ContainerUtil.addAll(srcSet, source);
    if (srcSet.size() < 2) {
      return myNext.classify(source, context);
    }
    return new LiftingIterable(srcSet, context, source, lifted);
  }

  @NotNull
  @Override
  public List<Pair<LookupElement, Object>> getSortingWeights(@NotNull Iterable<? extends LookupElement> items, @NotNull ProcessingContext context) {
    Set<LookupElement> lifted = new ReferenceOpenHashSet<>();
    Iterable<LookupElement> iterable = liftShorterElements(ContainerUtil.newArrayList(items), lifted, context);
    return ContainerUtil.map(iterable, element -> new Pair<>(element, lifted.contains(element)));
  }

  @Override
  public void removeElement(@NotNull LookupElement element, @NotNull ProcessingContext context) {
    for (String s : CompletionUtil.iterateLookupStrings(element)) {
      myElements.remove(s, element);
      if (myElements.get(s).isEmpty()) {
        mySortedStrings.remove(s);
      }
    }

    removeFromMap(element, myToLift, myReversedToLift);
    removeFromMap(element, myReversedToLift, myToLift);

    super.removeElement(element, context);
  }

  private static void removeFromMap(LookupElement key,
                                    MultiMap<LookupElement, LookupElement> mainMap,
                                    MultiMap<LookupElement, LookupElement> inverseMap) {
    Collection<LookupElement> removed = mainMap.remove(key);
    if (removed == null) return;

    for (LookupElement reference : new ArrayList<>(removed)) {
      inverseMap.remove(reference, key);
    }
  }

  public static class LiftingCondition {
    public boolean shouldLift(LookupElement shorterElement, LookupElement longerElement) {
      return true;
    }
  }

  private class LiftingIterable implements Iterable<LookupElement> {
    private final Set<LookupElement> mySrcSet;
    private final ProcessingContext myContext;
    private final Iterable<? extends LookupElement> mySource;
    private final Set<? super LookupElement> myLifted;

    LiftingIterable(Set<LookupElement> srcSet,
                    ProcessingContext context,
                    Iterable<? extends LookupElement> source,
                    Set<? super LookupElement> lifted) {
      mySrcSet = srcSet;
      myContext = context;
      mySource = source;
      myLifted = lifted;
    }

    @Override
    public Iterator<LookupElement> iterator() {
      Set<LookupElement> processed = new ReferenceOpenHashSet<>(mySrcSet.size());
      Set<Collection<LookupElement>> arraysProcessed = new ReferenceOpenHashSet<>();

      final Iterable<LookupElement> next = myNext.classify(mySource, myContext);
      Iterator<LookupElement> base = FilteringIterator.create(next.iterator(), element -> processed.add(element));
      return new FlatteningIterator<LookupElement, LookupElement>(base) {
        @Override
        protected Iterator<LookupElement> createValueIterator(LookupElement element) {
          List<LookupElement> shorter = addShorterElements(myToLift.get(element));
          List<LookupElement> singleton = Collections.singletonList(element);
          if (shorter != null) {
            if (myLifted != null) {
              myLifted.addAll(shorter);
            }
            Iterable<LookupElement> lifted = myNext.classify(shorter, myContext);
            return (myLiftBefore ? ContainerUtil.concat(lifted, singleton) : ContainerUtil.concat(singleton, lifted)).iterator();
          }
          return singleton.iterator();
        }

        @Nullable
        private List<LookupElement> addShorterElements(@Nullable Collection<LookupElement> from) {
          List<LookupElement> toLift = null;
          if (from == null) {
            return null;
          }

          if (arraysProcessed.add(from)) {
            for (LookupElement shorterElement : from) {
              if (mySrcSet.contains(shorterElement) && processed.add(shorterElement)) {
                if (toLift == null) {
                  toLift = new ArrayList<>();
                }
                toLift.add(shorterElement);
              }
            }

          }
          return toLift;
        }

      };
    }
  }

  private static @NotNull <K, V> MultiMap<K, V> createMultiMap(boolean identityKeys) {
    return new MultiMap<K, V>(identityKeys ? new Reference2ObjectOpenHashMap<>() : CollectionFactory.createMap()) {
      @Override
      public boolean remove(K key, V value) {
        List<V> elements = (List<V>)get(key);
        int i = ContainerUtil.indexOfIdentity(elements, value);
        if (i >= 0) {
          elements.remove(i);
          if (elements.isEmpty()) {
            remove(key);
          }
          return true;
        }
        return false;
      }
    };
  }
}
