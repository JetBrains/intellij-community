// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class GroupedSearchListModel extends SearchListModel {

  private static final Logger LOG = Logger.getInstance(GroupedSearchListModel.class);

  @Override
  public boolean hasMoreElements(SearchEverywhereContributor contributor) {
    return listElements.stream()
      .anyMatch(info -> info.getElement() == MORE_ELEMENT && info.getContributor() == contributor);
  }

  @Override
  public void addElements(List<? extends SearchEverywhereFoundElementInfo> items) {
    if (items.isEmpty()) {
      return;
    }
    Map<SearchEverywhereContributor<?>, List<SearchEverywhereFoundElementInfo>> itemsMap = new HashMap<>();
    items.forEach(info -> {
      List<SearchEverywhereFoundElementInfo> list = itemsMap.computeIfAbsent(info.getContributor(), contributor -> new ArrayList<>());
      list.add(info);
    });
    itemsMap.forEach((contributor, list) -> list.sort(Comparator.comparingInt(SearchEverywhereFoundElementInfo::getPriority).reversed()));

    if (resultsExpired) {
      retainContributors(itemsMap.keySet());
      clearMoreItems();

      itemsMap.forEach((contributor, list) -> {
        Object[] oldItems = ArrayUtil.toObjectArray(getFoundItems(contributor));
        Object[] newItems = list.stream()
          .map(SearchEverywhereFoundElementInfo::getElement)
          .toArray();
        try {
          Diff.Change change = Diff.buildChanges(oldItems, newItems);
          applyChange(change, contributor, list);
        }
        catch (FilesTooBigForDiffException e) {
          LOG.error("Cannot calculate diff for updated search results");
        }
      });
      resultsExpired = false;
    }
    else {
      itemsMap.forEach((contributor, list) -> {
        int startIndex = contributors().indexOf(contributor);
        int insertionIndex = getInsertionPoint(contributor);
        int endIndex = insertionIndex + list.size() - 1;
        listElements.addAll(insertionIndex, list);
        fireIntervalAdded(this, insertionIndex, endIndex);

        // there were items for this contributor before update
        if (startIndex >= 0) {
          listElements.subList(startIndex, endIndex + 1)
            .sort(Comparator.comparingInt(SearchEverywhereFoundElementInfo::getPriority).reversed());
          fireContentsChanged(this, startIndex, endIndex);
        }
      });
    }
  }

  private void retainContributors(Collection<SearchEverywhereContributor<?>> retainContributors) {
    Iterator<SearchEverywhereFoundElementInfo> iterator = listElements.iterator();
    int startInterval = 0;
    int endInterval = -1;
    while (iterator.hasNext()) {
      SearchEverywhereFoundElementInfo item = iterator.next();
      if (retainContributors.contains(item.getContributor())) {
        if (startInterval <= endInterval) {
          fireIntervalRemoved(this, startInterval, endInterval);
          startInterval = endInterval + 2;
        }
        else {
          startInterval++;
        }
      }
      else {
        iterator.remove();
      }
      endInterval++;
    }

    if (startInterval <= endInterval) {
      fireIntervalRemoved(this, startInterval, endInterval);
    }
  }

  @Override
  public void clearMoreItems() {
    ListIterator<SearchEverywhereFoundElementInfo> iterator = listElements.listIterator();
    while (iterator.hasNext()) {
      int index = iterator.nextIndex();
      if (iterator.next().getElement() == MORE_ELEMENT) {
        iterator.remove();
        fireContentsChanged(this, index, index);
      }
    }
  }

  private void applyChange(Diff.Change change,
                           SearchEverywhereContributor<?> contributor,
                           List<SearchEverywhereFoundElementInfo> newItems) {
    int firstItemIndex = contributors().indexOf(contributor);
    if (firstItemIndex < 0) {
      firstItemIndex = getInsertionPoint(contributor);
    }

    for (Diff.Change ch : toRevertedList(change)) {
      if (ch.deleted > 0) {
        for (int i = ch.deleted - 1; i >= 0; i--) {
          int index = firstItemIndex + ch.line0 + i;
          listElements.remove(index);
        }
        fireIntervalRemoved(this, firstItemIndex + ch.line0, firstItemIndex + ch.line0 + ch.deleted - 1);
      }

      if (ch.inserted > 0) {
        List<SearchEverywhereFoundElementInfo> addedItems = newItems.subList(ch.line1, ch.line1 + ch.inserted);
        listElements.addAll(firstItemIndex + ch.line0, addedItems);
        fireIntervalAdded(this, firstItemIndex + ch.line0, firstItemIndex + ch.line0 + ch.inserted - 1);
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
    int index = contributors().indexOf(contributor);
    if (index < 0) {
      return;
    }

    while (index < getSize() && listElements.get(index).getContributor() == contributor) {
      if (item.equals(getElementAt(index))) {
        listElements.remove(index);
        fireIntervalRemoved(this, index, index);
        return;
      }
      index++;
    }
  }

  @Override
  public void setHasMore(SearchEverywhereContributor<?> contributor, boolean newVal) {
    int index = contributors().lastIndexOf(contributor);
    if (index < 0) {
      return;
    }

    boolean alreadyHas = isMoreElement(index);
    if (alreadyHas && !newVal) {
      listElements.remove(index);
      fireIntervalRemoved(this, index, index);
    }

    if (!alreadyHas && newVal) {
      index += 1;
      listElements.add(index, new SearchEverywhereFoundElementInfo(MORE_ELEMENT, 0, contributor));
      fireIntervalAdded(this, index, index);
    }
  }

  public boolean isGroupFirstItem(int index) {
    return index == 0 || listElements.get(index).getContributor() != listElements.get(index - 1).getContributor();
  }

  @Override
  public int getIndexToScroll(int currentIndex, boolean scrollDown) {
    int index = currentIndex;
    do {
      index += scrollDown ? 1 : -1;
    }
    while (index >= 0 && index < getSize() && !isGroupFirstItem(index) && !isMoreElement(index));

    return Integer.max(Integer.min(index, getSize() - 1), 0);
  }

  public int getItemsForContributor(SearchEverywhereContributor<?> contributor) {
    List<SearchEverywhereContributor> contributorsList = contributors();
    int first = contributorsList.indexOf(contributor);
    int last = contributorsList.lastIndexOf(contributor);
    if (isMoreElement(last)) {
      last -= 1;
    }
    return last - first + 1;
  }

  private int getInsertionPoint(SearchEverywhereContributor contributor) {
    if (listElements.isEmpty()) {
      return 0;
    }

    List<SearchEverywhereContributor> list = contributors();
    int index = list.lastIndexOf(contributor);
    if (index >= 0) {
      return isMoreElement(index) ? index : index + 1;
    }

    index = Collections.binarySearch(list, contributor, Comparator.comparingInt(SearchEverywhereContributor::getSortWeight));
    return -index - 1;
  }
}
