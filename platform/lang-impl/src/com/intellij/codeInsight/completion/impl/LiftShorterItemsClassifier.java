/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.CollectionFactory.identityHashMap;
import static com.intellij.util.containers.CollectionFactory.identityTroveSet;

/**
* @author peter
*/
public class LiftShorterItemsClassifier extends Classifier<LookupElement> {
  private final TreeSet<String> mySortedStrings = new TreeSet<String>();
  private final MultiMap<String, LookupElement> myElements = new MultiMap<String, LookupElement>() {
    @Override
    protected Map<String, Collection<LookupElement>> createMap() {
      return new THashMap<String, Collection<LookupElement>>();
    }

    @Override
    protected Collection<LookupElement> createCollection() {
      return new ArrayList<LookupElement>(1);
    }
  };
  private final Map<LookupElement, FList<LookupElement>> myToLiftForSorting = identityHashMap();
  private final Map<LookupElement, FList<LookupElement>> myToLiftForPreselection = identityHashMap();
  private final IdentityHashMap<FList<LookupElement>, IdentityHashMap<LookupElement, FList<LookupElement>>> myPrepends = identityHashMap();
  private final Classifier<LookupElement> myNext;
  private final LiftingCondition myCondition;
  private int myCount = 0;

  public LiftShorterItemsClassifier(Classifier<LookupElement> next, LiftingCondition condition) {
    myNext = next;
    myCondition = condition;
  }

  @Override
  public void addElement(LookupElement added) {
    myCount++;

    final Set<String> strings = added.getAllLookupStrings();
    for (String string : strings) {
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
    myNext.addElement(added);

    calculateToLift(added);
  }

  private void updateLongerItem(LookupElement shorter, LookupElement longer) {
    boolean forPreselection = myCondition.shouldLift(shorter, longer);
    Map<LookupElement, FList<LookupElement>> map = forPreselection ? myToLiftForPreselection : myToLiftForSorting;
    FList<LookupElement> oldValue = ContainerUtil.getOrElse(map, longer, FList.<LookupElement>emptyList());
    map.put(longer, prependOrReuse(oldValue, shorter));
  }

  private FList<LookupElement> prependOrReuse(FList<LookupElement> tail, LookupElement head) {
    IdentityHashMap<LookupElement, FList<LookupElement>> cache = myPrepends.get(tail);
    if (cache == null) {
      myPrepends.put(tail, cache = identityHashMap());
    }
    FList<LookupElement> result = cache.get(head);
    if (result == null) {
      cache.put(head, result = tail.getHead() == head ? tail : tail.prepend(head));
    }
    return result;
  }

  private void calculateToLift(LookupElement element) {
    FList<LookupElement> forPreselection = FList.emptyList();
    FList<LookupElement> forSorting = FList.emptyList();

    for (String string : element.getAllLookupStrings()) {
      for (int len = 1; len < string.length(); len++) {
        String prefix = string.substring(0, len);
        for (LookupElement shorterElement : myElements.get(prefix)) {
          if (myCondition.shouldLift(shorterElement, element)) {
            forPreselection = prependOrReuse(forPreselection, shorterElement);
          } else {
            forSorting = prependOrReuse(forSorting, shorterElement);
          }
        }
      }
    }

    if (!forPreselection.isEmpty()) {
      myToLiftForPreselection.put(element, forPreselection);
    }
    if (!forSorting.isEmpty()) {
      myToLiftForSorting.put(element, forSorting);
    }
  }

  @Override
  public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
    return liftShorterElements(source, null, context);
  }

  private Iterable<LookupElement> liftShorterElements(final Iterable<LookupElement> source,
                                                      @Nullable final THashSet<LookupElement> lifted, final ProcessingContext context) {
    final Set<LookupElement> srcSet = identityTroveSet(source instanceof Collection ? ((Collection)source).size() : myCount);
    ContainerUtil.addAll(srcSet, source);

    if (srcSet.size() < 2) {
      return myNext.classify(source, context);
    }

    return new LiftingIterable(srcSet, context, source, lifted);
  }

  @Override
  public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map, ProcessingContext context) {
    final THashSet<LookupElement> lifted = identityTroveSet();
    CollectionFactory.arrayList(liftShorterElements(new ArrayList<LookupElement>(map.keySet()), lifted, context));
    if (!lifted.isEmpty()) {
      for (LookupElement element : map.keySet()) {
        final StringBuilder builder = map.get(element);
        if (builder.length() > 0) {
          builder.append(", ");
        }

        builder.append("liftShorter=").append(lifted.contains(element));
      }
    }
    myNext.describeItems(map, context);
  }

  public static class LiftingCondition {
    public boolean shouldLift(LookupElement shorterElement, LookupElement longerElement) {
      return false;
    }
  }

  private class LiftingIterable implements Iterable<LookupElement> {
    private final Set<LookupElement> mySrcSet;
    private final ProcessingContext myContext;
    private final Iterable<LookupElement> mySource;
    private final THashSet<LookupElement> myLifted;

    public LiftingIterable(Set<LookupElement> srcSet,
                           ProcessingContext context,
                           Iterable<LookupElement> source,
                           THashSet<LookupElement> lifted) {
      mySrcSet = srcSet;
      myContext = context;
      mySource = source;
      myLifted = lifted;
    }

    @Override
    public Iterator<LookupElement> iterator() {
      final Set<LookupElement> processed = identityTroveSet(mySrcSet.size());
      final Set<FList<LookupElement>> arraysProcessed = identityTroveSet();

      final boolean forSorting = myContext.get(CompletionLookupArranger.PURE_RELEVANCE) != Boolean.TRUE;
      final Iterable<LookupElement> next = myNext.classify(mySource, myContext);
      Iterator<LookupElement> base = FilteringIterator.create(next.iterator(), new Condition<LookupElement>() {
        @Override
        public boolean value(LookupElement element) {
          return processed.add(element);
        }
      });
      return new FlatteningIterator<LookupElement, LookupElement>(base) {
        @Override
        protected Iterator<LookupElement> createValueIterator(LookupElement element) {
          List<LookupElement> shorter = addShorterElements(null, myToLiftForPreselection.get(element));
          if (forSorting) {
            shorter = addShorterElements(shorter, myToLiftForSorting.get(element));
          }
          if (shorter != null) {
            if (myLifted != null) {
              myLifted.addAll(shorter);
            }
            return ContainerUtil.concat(myNext.classify(shorter, myContext), Collections.singletonList(element)).iterator();
          }
          return Collections.singletonList(element).iterator();
        }

        @Nullable
        private List<LookupElement> addShorterElements(@Nullable List<LookupElement> toLift,
                                                       @Nullable FList<LookupElement> from) {
          if (from == null) {
            return toLift;
          }

          FList<LookupElement> each = from;
          while (!each.isEmpty() && arraysProcessed.add(each)) {
            LookupElement shorterElement = each.getHead();
            if (mySrcSet.contains(shorterElement) && processed.add(shorterElement)) {
              if (toLift == null) {
                toLift = new ArrayList<LookupElement>();
              }
              toLift.add(shorterElement);
            }
            each = each.getTail();
          }
          return toLift;
        }

      };
    }
  }
}
