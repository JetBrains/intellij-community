/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class LookupModel {
  private static final Comparator<LookupElement> COMMUNISM = new Comparator<LookupElement>() {
    @SuppressWarnings({"ComparatorMethodParameterNotUsed"})
    @Override
    public int compare(LookupElement o1, LookupElement o2) {
      return 0;
    }
  };
  private final Object lock = new Object();
  @SuppressWarnings({"unchecked"}) private final Map<LookupElement, Collection<LookupElementAction>> myItemActions = new THashMap<LookupElement, Collection<LookupElementAction>>(TObjectHashingStrategy.IDENTITY);
  @SuppressWarnings({"unchecked"}) private final Map<LookupElement, String> myItemPresentations = new THashMap<LookupElement, String>(TObjectHashingStrategy.IDENTITY);
  private final List<LookupElement> myItems = new ArrayList<LookupElement>();
  private SortedList<LookupElement> mySortedItems;
  private LookupArranger myArranger;
  private Classifier<LookupElement> myRelevanceClassifier;

  public List<LookupElement> getItems() {
    synchronized (lock) {
      return new ArrayList<LookupElement>(myItems);
    }
  }

  public void clearItems() {
    synchronized (lock) {
      myItems.clear();
      mySortedItems.clear();
      myRelevanceClassifier = myArranger.createRelevanceClassifier();
    }
  }

  public void addItem(LookupElement item) {
    synchronized (lock) {
      myRelevanceClassifier.addElement(item);
      mySortedItems.add(item); // ProcessCanceledException may occur in these two lines, then this element is considered not added

      myItems.add(item);
    }
  }

  public void setItemPresentation(LookupElement item, LookupElementPresentation presentation) {
    final String invariant = presentation.getItemText() + "###" + presentation.getTailText() + "###" + presentation.getTypeText();
    synchronized (lock) {
      myItemPresentations.put(item, invariant);
    }
  }

  public String getItemPresentationInvariant(LookupElement element) {
    synchronized (lock) {
      return myItemPresentations.get(element);
    }
  }


  public void setItemActions(LookupElement item, Collection<LookupElementAction> actions) {
    synchronized (lock) {
      myItemActions.put(item, actions);
    }
  }

  public Collection<LookupElementAction> getActionsFor(LookupElement element) {
    synchronized (lock) {
      final Collection<LookupElementAction> collection = myItemActions.get(element);
      return collection == null ? Collections.<LookupElementAction>emptyList() : collection;
    }
  }

  public Pair<List<LookupElement>, Iterable<List<LookupElement>>> getModelSnapshot() {
    synchronized (lock) {
      final List<LookupElement> sorted = new ArrayList<LookupElement>(mySortedItems);
      final Iterable<List<LookupElement>> groups = myRelevanceClassifier.classify(sorted);
      return Pair.create(sorted, groups);
    }
  }

  public void collectGarbage() {
    synchronized (lock) {
      Set<LookupElement> itemSet = new THashSet<LookupElement>(myItems, TObjectHashingStrategy.IDENTITY);
      myItemActions.keySet().retainAll(itemSet);
      myItemPresentations.keySet().retainAll(itemSet);
    }
  }

  @Nullable
  Map<LookupElement, PrefixMatcher> retainMatchingItems(final String newPrefix, final LookupImpl lookup) {
    synchronized (lock) {
      LinkedHashMap<LookupElement, PrefixMatcher> map = new LinkedHashMap<LookupElement, PrefixMatcher>();
      for (LookupElement item : myItems) {
        if (item.isValid()) {
          PrefixMatcher matcher = lookup.itemMatcher(item).cloneWithPrefix(newPrefix);
          if (matcher.prefixMatches(item)) {
            map.put(item, matcher);
          }
        }
      }

      if (map.size() != myItems.size()) {
        clearItems();
        for (LookupElement newItem : map.keySet()) {
          addItem(newItem);
        }
      }

      return map;
    }
  }

  public void setArranger(final LookupArranger arranger) {
    synchronized (lock) {
      myArranger = arranger;

      final Comparator<LookupElement> comparator = arranger.getItemComparator();
      mySortedItems = new SortedList<LookupElement>(comparator == null ? COMMUNISM : comparator);
      myRelevanceClassifier = myArranger.createRelevanceClassifier();
    }
  }

  LinkedHashMap<LookupElement,StringBuilder> getRelevanceStrings() {
    final LinkedHashMap<LookupElement,StringBuilder> map = new LinkedHashMap<LookupElement, StringBuilder>();
    for (LookupElement item : myItems) {
      map.put(item, new StringBuilder());
    }
    myRelevanceClassifier.describeItems(map);
    return map;
  }

  public List<LookupElement> classifyByRelevance(List<LookupElement> list) {
    synchronized (lock) {
      final Classifier<LookupElement> classifier = myArranger.createRelevanceClassifier();
      for (LookupElement element : list) {
        classifier.addElement(element);
      }
      return ContainerUtil.flatten(classifier.classify(list));
    }
  }
}
