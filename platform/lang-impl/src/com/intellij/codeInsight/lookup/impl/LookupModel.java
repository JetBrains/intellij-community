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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author peter
 */
public class LookupModel {
  private final Object lock = new Object();
  @SuppressWarnings({"unchecked"}) private final Map<LookupElement, Collection<LookupElementAction>> myItemActions = new THashMap<LookupElement, Collection<LookupElementAction>>(TObjectHashingStrategy.IDENTITY);
  @SuppressWarnings({"unchecked"}) private final Map<LookupElement, String> myItemPresentations = new THashMap<LookupElement, String>(TObjectHashingStrategy.IDENTITY);
  private final List<LookupElement> myItems = new ArrayList<LookupElement>();
  @Nullable private List<LookupElement> mySortedItems;
  private final LookupImpl myLookup;

  public LookupModel(LookupImpl lookup) {
    myLookup = lookup;
  }

  @TestOnly
  public List<LookupElement> getItems() {
    return new ArrayList<LookupElement>(myItems);
  }

  public void clearItems() {
    synchronized (lock) {
      myItems.clear();
      mySortedItems = null;
    }
  }

  public void addItem(LookupElement item) {
    synchronized (lock) {
      myItems.add(item);
      mySortedItems = null;
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

  @NotNull
  public List<LookupElement> getSortedItems() {
    synchronized (lock) {
      List<LookupElement> sortedItems = mySortedItems;
      if (sortedItems == null) {
        myLookup.getArranger().sortItems(sortedItems = new ArrayList<LookupElement>(myItems));
        mySortedItems = sortedItems;
      }
      return sortedItems;
    }
  }


  public void collectGarbage() {
    synchronized (lock) {
      myItemActions.keySet().retainAll(myItems);
      myItemPresentations.keySet().retainAll(myItems);
    }
  }

  void retainMatchingItems(String newPrefix) {
    synchronized (lock) {
      for (Iterator<LookupElement> iterator = myItems.iterator(); iterator.hasNext();) {
        LookupElement item = iterator.next();
        if (!item.setPrefixMatcher(item.getPrefixMatcher().cloneWithPrefix(newPrefix))) {
          iterator.remove();
          mySortedItems = null;
        }
      }
    }
  }

}
