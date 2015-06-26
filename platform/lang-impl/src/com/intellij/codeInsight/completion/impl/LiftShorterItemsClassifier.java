/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newIdentityHashMap;
import static com.intellij.util.containers.ContainerUtil.newIdentityTroveSet;

/**
* @author peter
*/
public class LiftShorterItemsClassifier extends Classifier<LookupElement> {
  private final TreeSet<String> mySortedStrings = new TreeSet<String>();
  private final MultiMap<String, LookupElement> myElements = MultiMap.createSmart();
  private final MultiMap<LookupElement, LookupElement> myToLift = new MultiMap<LookupElement, LookupElement>() {
    @NotNull
    @Override
    protected Map<LookupElement, Collection<LookupElement>> createMap() {
      return newIdentityHashMap();
    }
  };
  private final WeakInterner<Collection<LookupElement>> myListInterner = new WeakInterner<Collection<LookupElement>>();
  private final String myName;
  private final Classifier<LookupElement> myNext;
  private final LiftingCondition myCondition;
  private final boolean myLiftBefore;
  private int myCount = 0;

  public LiftShorterItemsClassifier(String name, Classifier<LookupElement> next, LiftingCondition condition, boolean liftBefore) {
    myName = name;
    myNext = next;
    myCondition = condition;
    myLiftBefore = liftBefore;
  }

  @Override
  public void addElement(LookupElement added, ProcessingContext context) {
    myCount++;

    for (String string : CompletionUtil.getImmutableLookupStrings(added)) {
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
    myNext.addElement(added, context);

    calculateToLift(added);
  }

  private void updateLongerItem(LookupElement shorter, LookupElement longer) {
    if (myCondition.shouldLift(shorter, longer)) {
      myToLift.putValue(longer, shorter);
      internListToLift(longer);
    }
  }

  private void internListToLift(LookupElement longer) {
    final Collection<LookupElement> elements = myToLift.get(longer);
    if (elements.size() > 10) return;
    
    myToLift.put(longer, myListInterner.intern(elements));
  }

  private void calculateToLift(LookupElement element) {
    boolean hasChanges = false;
    for (String string : CompletionUtil.getImmutableLookupStrings(element)) {
      for (int len = 1; len < string.length(); len++) {
        String prefix = string.substring(0, len);
        for (LookupElement shorterElement : myElements.get(prefix)) {
          if (myCondition.shouldLift(shorterElement, element)) {
            hasChanges = true;
            myToLift.putValue(element, shorterElement);
          }
        }
      }
    }
    if (hasChanges) {
      internListToLift(element);
    }
  }

  @Override
  public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
    return liftShorterElements(source, null, context);
  }

  private Iterable<LookupElement> liftShorterElements(final Iterable<LookupElement> source,
                                                      @Nullable final THashSet<LookupElement> lifted, final ProcessingContext context) {
    final Set<LookupElement> srcSet = newIdentityTroveSet(source instanceof Collection ? ((Collection)source).size() : myCount);
    ContainerUtil.addAll(srcSet, source);

    if (srcSet.size() < 2) {
      return myNext.classify(source, context);
    }

    return new LiftingIterable(srcSet, context, source, lifted);
  }

  @Override
  public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map, ProcessingContext context) {
    final THashSet<LookupElement> lifted = newIdentityTroveSet();
    liftShorterElements(new ArrayList<LookupElement>(map.keySet()), lifted, context);
    if (!lifted.isEmpty()) {
      for (LookupElement element : map.keySet()) {
        final StringBuilder builder = map.get(element);
        if (builder.length() > 0) {
          builder.append(", ");
        }

        builder.append(myName).append("=").append(lifted.contains(element));
      }
    }
    myNext.describeItems(map, context);
  }

  public static class LiftingCondition {
    public boolean shouldLift(LookupElement shorterElement, LookupElement longerElement) {
      return true;
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
      final Set<LookupElement> processed = newIdentityTroveSet(mySrcSet.size());
      final Set<Collection<LookupElement>> arraysProcessed = newIdentityTroveSet();

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
                  toLift = new ArrayList<LookupElement>();
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
}
