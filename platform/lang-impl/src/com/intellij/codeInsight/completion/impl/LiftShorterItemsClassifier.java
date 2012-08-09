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
import com.intellij.util.SmartList;
import com.intellij.util.containers.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  private final Map<LookupElement, LookupElement[]> myToLiftForSorting = new IdentityHashMap<LookupElement, LookupElement[]>();
  private final Map<LookupElement, LookupElement[]> myToLiftForPreselection = new IdentityHashMap<LookupElement, LookupElement[]>();
  private final Map<THashSet<LookupElement>, LookupElement[]> myInterned = new THashMap<THashSet<LookupElement>, LookupElement[]>();
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

    final Set<String> strings = getAllLookupStrings(added);
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
          addShorterItem(added, longer);
        }
      }
    }
    myNext.addElement(added);

    calculateToLift(added);
  }

  private void addShorterItem(LookupElement shorter, LookupElement longer) {
    Map<LookupElement, LookupElement[]> map = myCondition.shouldLift(shorter, longer) ? myToLiftForPreselection : myToLiftForSorting;
    THashSet<LookupElement> toLift = loadItems(longer, map);
    toLift.add(shorter);
    saveItems(longer, toLift, map);
  }

  private void calculateToLift(LookupElement element) {
    final THashSet<LookupElement> forPreselection = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
    final THashSet<LookupElement> forSorting = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
    for (String string : getAllLookupStrings(element)) {
      for (int len = 1; len < string.length(); len++) {
        String prefix = string.substring(0, len);
        for (LookupElement shorterElement : myElements.get(prefix)) {
          if (myCondition.shouldLift(shorterElement, element)) {
            forPreselection.add(shorterElement);
          } else {
            forSorting.add(shorterElement);
          }
        }
      }
    }

    saveItems(element, forPreselection, myToLiftForPreselection);
    saveItems(element, forSorting, myToLiftForSorting);
  }

  private static THashSet<LookupElement> loadItems(LookupElement key, final Map<LookupElement, LookupElement[]> map) {
    LookupElement[] items = map.get(key);
    final THashSet<LookupElement> forPreselection = new THashSet<LookupElement>(items == null ? 2 : items.length * 2, TObjectHashingStrategy.IDENTITY);
    if (items != null) {
      Collections.addAll(forPreselection, items);
    }
    return forPreselection;
  }

  private void saveItems(LookupElement key, THashSet<LookupElement> items, final Map<LookupElement, LookupElement[]> map) {
    if (!items.isEmpty()) {
      map.put(key, internItems(items));
    }
  }

  private LookupElement[] internItems(THashSet<LookupElement> items) {
    LookupElement[] array = myInterned.get(items);
    if (array == null) {
      array = items.toArray(new LookupElement[items.size()]);
      myInterned.put(items, array);
    }
    return array;
  }

  @Override
  public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
    return liftShorterElements(source, null, context);
  }

  private Iterable<LookupElement> liftShorterElements(final Iterable<LookupElement> source,
                                                      @Nullable final THashSet<LookupElement> lifted, final ProcessingContext context) {
    final Set<LookupElement> srcSet = new THashSet<LookupElement>(source instanceof Collection ? ((Collection)source).size() : myCount, TObjectHashingStrategy.IDENTITY);
    ContainerUtil.addAll(srcSet, source);
    final Set<LookupElement> processed = new THashSet<LookupElement>(srcSet.size(), TObjectHashingStrategy.IDENTITY);

    final Set<LookupElement[]> arraysProcessed = new THashSet<LookupElement[]>(myInterned.size(), TObjectHashingStrategy.IDENTITY);

    final boolean forSorting = context.get(CompletionLookupArranger.PURE_RELEVANCE) != Boolean.TRUE;
    final Iterable<LookupElement> next = myNext.classify(source, context);
    return new Iterable<LookupElement>() {
      @Override
      public Iterator<LookupElement> iterator() {
        Iterator<LookupElement> base = FilteringIterator.create(next.iterator(), new Condition<LookupElement>() {
          @Override
          public boolean value(LookupElement element) {
            return processed.add(element);
          }
        });
        return new FlatteningIterator<LookupElement, LookupElement>(base) {
          @Override
          protected Iterator<LookupElement> createValueIterator(LookupElement element) {
            List<LookupElement> shorter = addShorterElements(srcSet, processed, arraysProcessed, null, myToLiftForPreselection.get(element));
            if (forSorting) {
              shorter = addShorterElements(srcSet, processed, arraysProcessed, shorter, myToLiftForSorting.get(element));
            }
            if (shorter != null) {
              if (lifted != null) {
                lifted.addAll(shorter);
              }
              return ContainerUtil.concat(myNext.classify(shorter, context), Collections.singletonList(element)).iterator();
            }
            return Collections.singletonList(element).iterator();
          }
        };
      }
    };
  }

  @Nullable
  private static List<LookupElement> addShorterElements(Set<LookupElement> srcSet,
                                                        Set<LookupElement> processed,
                                                        Set<LookupElement[]> arraysProcessed,
                                                        @Nullable List<LookupElement> toLift,
                                                        @Nullable LookupElement[] from) {
    if (from != null && arraysProcessed.add(from)) {
      for (LookupElement shorterElement : from) {
        if (srcSet.contains(shorterElement) && processed.add(shorterElement)) {
          if (toLift == null) toLift = new SmartList<LookupElement>();
          toLift.add(shorterElement);
        }
      }
    }
    return toLift;
  }

  private static Set<String> getAllLookupStrings(LookupElement element) {
    return element.getAllLookupStrings();
  }

  @Override
  public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map, ProcessingContext context) {
    final THashSet<LookupElement> lifted = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
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
}
