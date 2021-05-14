// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

class MixedSearchListModel extends SearchListModel {

  private static final Logger LOG = Logger.getInstance(MixedSearchListModel.class);

  private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors = new HashMap<>();

  private Comparator<? super SearchEverywhereFoundElementInfo> myElementsComparator = SearchEverywhereFoundElementInfo.COMPARATOR.reversed();

  // new elements cannot be added before this index when "more..." elements are loaded
  private int myMaxFrozenIndex;

  public void setElementsComparator(Comparator<? super SearchEverywhereFoundElementInfo> elementsComparator) {
    myElementsComparator = elementsComparator;
  }

  @Override
  public boolean hasMoreElements(SearchEverywhereContributor<?> contributor) {
    return Boolean.TRUE.equals(hasMoreContributors.get(contributor));
  }

  @Override
  public int getIndexToScroll(int currentIndex, boolean scrollDown) {
    return scrollDown ? getSize() - 1 : 0;
  }

  @Override
  public void addElements(List<? extends SearchEverywhereFoundElementInfo> items) {
    if (items.isEmpty()) {
      return;
    }

    items = items.stream()
      .sorted(myElementsComparator)
      .collect(Collectors.toList());

    if (resultsExpired) {
      clearMoreItems();

      Object[] oldItems = ArrayUtil.toObjectArray(getItems());
      Object[] newItems = items.stream()
        .map(SearchEverywhereFoundElementInfo::getElement)
        .toArray();
      try {
        Diff.Change change = Diff.buildChanges(oldItems, newItems);
        applyChange(change, items);
      }
      catch (FilesTooBigForDiffException e) {
        LOG.error("Cannot calculate diff for updated search results");
      }

      resultsExpired = false;
    }
    else {
      int startIndex = listElements.size();
      listElements.addAll(items);
      int endIndex = listElements.size() - 1;
      fireIntervalAdded(this, startIndex, endIndex);

      // there were items for this contributor before update
      if (startIndex > 0) {
        List<SearchEverywhereFoundElementInfo> lst = myMaxFrozenIndex >= 0
                                                     ? listElements.subList(myMaxFrozenIndex + 1, listElements.size())
                                                     : listElements;
        lst.sort(myElementsComparator);
        fireContentsChanged(this, 0, endIndex);
      }
    }
  }

  @Override
  public void clearMoreItems() {
    if (listElements.isEmpty()) return;

    int lastItemIndex = listElements.size() - 1;
    SearchEverywhereFoundElementInfo lastItem = listElements.get(lastItemIndex);
    if (lastItem.getElement() == MORE_ELEMENT) {
      listElements.remove(lastItemIndex);
      fireIntervalRemoved(this, lastItemIndex, lastItemIndex);
    }
  }

  private void applyChange(Diff.Change change, List<? extends SearchEverywhereFoundElementInfo> newItems) {
    for (Diff.Change ch : toRevertedList(change)) {
      if (ch.deleted > 0) {
        listElements.subList(ch.line0, ch.line0 + ch.deleted).clear();
        fireIntervalRemoved(this, ch.line0, ch.line0 + ch.deleted - 1);
      }

      if (ch.inserted > 0) {
        List<? extends SearchEverywhereFoundElementInfo> addedItems = newItems.subList(ch.line1, ch.line1 + ch.inserted);
        listElements.addAll(ch.line0, addedItems);
        fireIntervalAdded(this, ch.line0, ch.line0 + ch.inserted - 1);
      }
    }
  }

  private static List<Diff.Change> toRevertedList(Diff.Change change) {
    List<Diff.Change> res = new ArrayList<>();
    while (change != null) {
      res.add(0, change);
      change = change.link;
    }
    return res;
  }

  @Override
  public void removeElement(@NotNull Object item, SearchEverywhereContributor<?> contributor) {
    if (listElements.isEmpty()) return;

    for (int i = 0; i < listElements.size(); i++) {
      SearchEverywhereFoundElementInfo info = listElements.get(i);
      if (info.getContributor() == contributor && info.getElement().equals(item)) {
        listElements.remove(i);
        fireIntervalRemoved(this, i, i);
        return;
      }
    }

    if (myMaxFrozenIndex >= getSize()) myMaxFrozenIndex = getSize() - 1;
  }

  @Override
  public void setHasMore(SearchEverywhereContributor<?> contributor, boolean contributorHasMore) {
    hasMoreContributors.put(contributor, contributorHasMore);

    int lasItemIndex = listElements.size() - 1;
    if (lasItemIndex < 0) {
      return;
    }

    boolean hasMore = hasMoreContributors.values().stream().anyMatch(val -> val);
    boolean alreadyHas = isMoreElement(lasItemIndex);
    if (alreadyHas && !hasMore) {
      listElements.remove(lasItemIndex);
      fireIntervalRemoved(this, lasItemIndex, lasItemIndex);
    }

    if (!alreadyHas && hasMore) {
      myMaxFrozenIndex = lasItemIndex;
      listElements.add(new SearchEverywhereFoundElementInfo(MORE_ELEMENT, 0, null));
      lasItemIndex += 1;
      fireIntervalAdded(this, lasItemIndex, lasItemIndex);
    }
  }

  @Override
  public void clear() {
    hasMoreContributors.clear();
    myMaxFrozenIndex = -1;
    super.clear();
  }

  @Override
  public void expireResults() {
    super.expireResults();
    myMaxFrozenIndex = -1;
  }
}
