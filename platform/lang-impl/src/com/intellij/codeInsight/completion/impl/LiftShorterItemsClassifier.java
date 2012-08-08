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
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author peter
*/
public class LiftShorterItemsClassifier extends Classifier<LookupElement> {
  private final TreeSet<String> mySortedStrings = new TreeSet<String>();
  private final MultiMap<String, LookupElement> myElements = new MultiMap<String, LookupElement>();
  private final Map<LookupElement, Set<LookupElement>> myToLiftForSorting = new IdentityHashMap<LookupElement, Set<LookupElement>>();
  private final Map<LookupElement, Set<LookupElement>> myToLiftForPreselection = new IdentityHashMap<LookupElement, Set<LookupElement>>();
  private final MultiMap<String, String> myPrefixes = new MultiMap<String, String>();
  private final Classifier<LookupElement> myNext;
  private final LiftingCondition myCondition;

  public LiftShorterItemsClassifier(Classifier<LookupElement> next, LiftingCondition condition) {
    myNext = next;
    myCondition = condition;
  }

  @Override
  public void addElement(LookupElement element) {
    final Set<LookupElement> toUpdate = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
    toUpdate.add(element);

    final Set<String> strings = getAllLookupStrings(element);
    for (String string : strings) {
      if (string.length() == 0) continue;

      myElements.putValue(string, element);
      mySortedStrings.add(string);
      final NavigableSet<String> after = mySortedStrings.tailSet(string, false);
      for (String s : after) {
        if (!s.startsWith(string)) {
          break;
        }
        myPrefixes.putValue(s, string);
        toUpdate.addAll(myElements.get(s));
      }

      for (int len = 1; len < string.length(); len++) {
        String shorter = string.substring(0, len);
        if (myElements.containsKey(shorter)) {
          myPrefixes.putValue(string, shorter);
        }
      }
    }
    myNext.addElement(element);

    for (LookupElement lookupElement : toUpdate) {
      recalculateToLift(lookupElement);
    }
  }

  private void recalculateToLift(LookupElement element) {
    final Set<LookupElement> forPreselection = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
    final Set<LookupElement> forSorting = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
    final List<String> prefixes = new SmartList<String>();
    for (String string : getAllLookupStrings(element)) {
      prefixes.addAll(myPrefixes.get(string));
    }
    Collections.sort(prefixes);
    for (String prefix : prefixes) {
      for (LookupElement shorterElement : myElements.get(prefix)) {
        if (myCondition.shouldLift(shorterElement, element)) {
          forPreselection.add(shorterElement);
        } else {
          forSorting.add(shorterElement);
        }
      }
    }

    myToLiftForPreselection.remove(element);
    myToLiftForSorting.remove(element);

    if (!forPreselection.isEmpty()) {
      myToLiftForPreselection.put(element, forPreselection);
    }
    if (!forSorting.isEmpty()) {
      myToLiftForSorting.put(element, forSorting);
    }
  }

  @Override
  public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
    return liftShorterElements(source, new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY), context);
  }

  private List<LookupElement> liftShorterElements(Iterable<LookupElement> source, THashSet<LookupElement> lifted, ProcessingContext context) {
    final Set<LookupElement> srcSet = new THashSet<LookupElement>(TObjectHashingStrategy.IDENTITY);
    ContainerUtil.addAll(srcSet, source);
    final Set<LookupElement> processed = new THashSet<LookupElement>(srcSet.size(), TObjectHashingStrategy.IDENTITY);

    boolean forSorting = context.get(CompletionLookupArranger.PURE_RELEVANCE) != Boolean.TRUE;
    final List<LookupElement> result = new ArrayList<LookupElement>(srcSet.size());
    for (LookupElement element : myNext.classify(source, context)) {
      if (processed.add(element)) {
        List<LookupElement> shorter = addShorterElements(srcSet, processed, null, myToLiftForPreselection.get(element));
        if (forSorting) {
          shorter = addShorterElements(srcSet, processed, shorter, myToLiftForSorting.get(element));
        }
        if (shorter != null) {
          lifted.addAll(shorter);
          ContainerUtil.addAll(result, myNext.classify(shorter, context));
        }
        result.add(element);
      }
    }
    return result;
  }

  @Nullable
  private static List<LookupElement> addShorterElements(Set<LookupElement> srcSet,
                                                        Set<LookupElement> processed,
                                                        @Nullable List<LookupElement> toLift,
                                                        @Nullable Set<LookupElement> from) {
    if (from != null) {
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
    liftShorterElements(new ArrayList<LookupElement>(map.keySet()), lifted, context);
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
