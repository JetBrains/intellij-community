package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SortedList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

/**
 * @author peter
 */
public class LookupModel extends AbstractListModel {
  private final List<LookupElement> myModelItems = new ArrayList<LookupElement>();
  private final ArrayList<LookupElement> myItems = new ArrayList<LookupElement>();
  @Nullable private List<LookupElement> mySortedItems;
  private String myAdditionalPrefix = "";
  private final LookupArranger myArranger;
  private int myPreferredItemsCount;
  private static final int MAX_PREFERRED_COUNT = 5;

  LookupModel(LookupArranger arranger) {
    myArranger = arranger;
  }

  public int getSize() {
    assertEDT();
    synchronized (myModelItems) {
      return myModelItems.size();
    }
  }

  public LookupElement getElementAt(int index) {
    assertEDT();
    synchronized (myModelItems) {
      return myModelItems.get(index);
    }
  }

  private static void assertEDT() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
  }

  public void addItem(LookupElement item) {
    synchronized (myItems) {
      myItems.add(item);
      mySortedItems = null;
    }
  }

  @NotNull
  private List<LookupElement> getSortedItems() {
    synchronized (myItems) {
      List<LookupElement> sortedItems = mySortedItems;
      if (sortedItems == null) {
        myArranger.sortItems(sortedItems = new ArrayList<LookupElement>(myItems));
        mySortedItems = sortedItems;
      }
      return sortedItems;
    }
  }

  @TestOnly
  void resort() {
    final ArrayList<LookupElement> items;
    synchronized (myItems) {
      items = new ArrayList<LookupElement>(myItems);
      myItems.clear();
      mySortedItems = null;
    }
    for (final LookupElement item : items) {
      addItem(item);
    }
  }

  int getPreferredItemsCount() {
    return myPreferredItemsCount;
  }

  String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  void setAdditionalPrefix(String additionalPrefix) {
    myAdditionalPrefix = additionalPrefix;
  }

  List<LookupElement> updateList(@Nullable LookupElement preselectedItem) {
    assertEDT();

    final List<LookupElement> items = getSortedItems();
    SortedMap<Comparable, List<LookupElement>> itemsMap = new TreeMap<Comparable, List<LookupElement>>();
    for (final LookupElement item : items) {
      final Comparable relevance = myArranger.getRelevance(item);
      List<LookupElement> list = itemsMap.get(relevance);
      if (list == null) {
        itemsMap.put(relevance, list = new ArrayList<LookupElement>());
      }
      list.add(item);
    }

    List<LookupElement> list = new ArrayList<LookupElement>();

    Set<LookupElement> firstItems = new THashSet<LookupElement>();

    addExactPrefixItems(firstItems, items, list);
    addMostRelevantItems(firstItems, itemsMap.values(), list);
    addPreselectedItem(firstItems, preselectedItem, list);
    myPreferredItemsCount = firstItems.size();

    addRemainingItemsLexicographically(firstItems, items, list);

    //sync is used mostly to clear processor caches here
    //and for getModelItems() to work from any thread
    final int oldSize;
    synchronized (myModelItems) {
      oldSize = myModelItems.size() - 1;
      myModelItems.clear();
    }
    fireIntervalRemoved(this, 0, oldSize);

    if (!list.isEmpty()) {
      synchronized (myModelItems) {
        myModelItems.addAll(list);
      }
      fireIntervalAdded(this, 0, list.size() - 1);
    }

    return list;
  }

  void addEmptyElement(LookupElement element) {
    assertEDT();
    synchronized (myModelItems) {
      myModelItems.add(element);
    }
    fireIntervalAdded(this, 0, 0);
  }

  void changePrefix(String prefix) {
    assertEDT();
    synchronized (myItems) {
      for (Iterator<LookupElement> iterator = myItems.iterator(); iterator.hasNext();) {
        LookupElement item = iterator.next();
        if (!item.setPrefixMatcher(item.getPrefixMatcher().cloneWithPrefix(prefix))) {
          iterator.remove();
          mySortedItems = null;
        }
      }
    }
    myAdditionalPrefix = "";
  }

  private boolean addExactPrefixItems(Set<LookupElement> firstItems, final List<LookupElement> elements,
                                      final List<LookupElement> modelItems) {
    List<LookupElement> sorted = new SortedList<LookupElement>(new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        //noinspection unchecked
        return myArranger.getRelevance(o1).compareTo(myArranger.getRelevance(o2));
      }
    });
    for (final LookupElement item : elements) {
      if (isExactPrefixItem(item)) {
        sorted.add(item);

      }
    }
    for (final LookupElement item : sorted) {
      modelItems.add(item);
      firstItems.add(item);
    }

    return !firstItems.isEmpty();
  }

  boolean isExactPrefixItem(LookupElement item) {
    return item.getAllLookupStrings().contains(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix);
  }

  private void addMostRelevantItems(Set<LookupElement> firstItems, final Collection<List<LookupElement>> sortedItems,
                                    final List<LookupElement> modelItems) {
    for (final List<LookupElement> elements : sortedItems) {
      final List<LookupElement> suitable = new SmartList<LookupElement>();
      for (final LookupElement item : elements) {
        if (!firstItems.contains(item) && prefixMatches(item)) {
          suitable.add(item);
        }
      }

      if (firstItems.size() + suitable.size() > MAX_PREFERRED_COUNT) break;
      for (final LookupElement item : suitable) {
        firstItems.add(item);
        modelItems.add(item);
      }
    }
  }

  private void addRemainingItemsLexicographically(Set<LookupElement> firstItems, List<LookupElement> myItems,
                                                  final List<LookupElement> modelItems) {
    for (LookupElement item : myItems) {
      if (!firstItems.contains(item) && prefixMatches(item)) {
        modelItems.add(item);
      }
    }
  }

  private static void addPreselectedItem(Set<LookupElement> firstItems, @Nullable final LookupElement preselectedItem,
                                  final List<LookupElement> modelItems) {
    if (preselectedItem != null && !firstItems.contains(preselectedItem)) {
      firstItems.add(preselectedItem);
      modelItems.add(preselectedItem);
    }
  }

  private boolean prefixMatches(final LookupElement item) {
    if (myAdditionalPrefix.length() == 0) return item.isPrefixMatched();

    return item.getPrefixMatcher().cloneWithPrefix(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix).prefixMatches(item);
  }


  public List<LookupElement> getModelItems() {
    synchronized (myModelItems) {
      return new ArrayList<LookupElement>(myModelItems);
    }
  }
}
