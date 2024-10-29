// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class GroupedSearchListModel extends SearchListModel {

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
        Range<Integer> range = getRangeForContributor(contributor);
        if (range != null) {
          listElements.subList(range.getFrom(), range.getTo() + 1).clear();
          fireIntervalRemoved(this, range.getFrom(), range.getTo());
        }
        int insertionPoint = range != null ? range.getFrom() : getInsertionPoint(contributor);
        listElements.addAll(insertionPoint, list);
        fireIntervalAdded(this, insertionPoint, insertionPoint + list.size() - 1);
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
      int elementsCount = index - contributors().indexOf(contributor);
      SearchEverywhereUsageTriggerCollector.MORE_ITEM_SHOWN.log(
        SearchEverywhereUsageTriggerCollector.ITEM_NUMBER_BEFORE_MORE.with(elementsCount),
        SearchEverywhereUsageTriggerCollector.IS_ONLY_MORE.with(false)
      );
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
    Range<Integer> range = getRangeForContributor(contributor);
    return range == null ? 0 : range.getTo() - range.getFrom() + 1;
  }

  private @Nullable Range<Integer> getRangeForContributor(SearchEverywhereContributor<?> contributor) {
    List<SearchEverywhereContributor> contributorsList = contributors();
    int first = contributorsList.indexOf(contributor);
    if (first < 0) return null;
    int last = contributorsList.lastIndexOf(contributor);
    if (isMoreElement(last)) {
      last -= 1;
    }

    return new Range<>(first, last);
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
