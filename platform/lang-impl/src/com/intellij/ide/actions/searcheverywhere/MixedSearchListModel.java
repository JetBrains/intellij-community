// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.util.Conditions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MixedSearchListModel extends SearchListModel {

  private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors = new HashMap<>();

  private Comparator<? super SearchEverywhereFoundElementInfo> myElementsComparator = SearchEverywhereFoundElementInfo.COMPARATOR.reversed();

  // new elements cannot be added before this index when "more..." elements are loaded
  private int myMaxFrozenIndex = -1;

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
      int lastIndex = listElements.size() - 1;
      listElements.clear();
      if (lastIndex >= 0) fireIntervalRemoved(this, 0, lastIndex);
      listElements.addAll(items);
      if (!listElements.isEmpty()) fireIntervalAdded(this, 0, listElements.size() - 1);

      resultsExpired = false;
    }
    else {
      int startIndex = listElements.size();
      listElements.addAll(items);
      int endIndex = listElements.size() - 1;
      fireIntervalAdded(this, startIndex, endIndex);

      if (myMaxFrozenIndex >= startIndex) myMaxFrozenIndex = startIndex - 1;

      // there were items for this contributor before update
      if (startIndex > 0) {
        List<SearchEverywhereFoundElementInfo> lst = myMaxFrozenIndex >= 0
                                                     ? listElements.subList(myMaxFrozenIndex + 1, listElements.size())
                                                     : listElements;
        lst.sort(myElementsComparator);
        int begin = myMaxFrozenIndex >= 0 ? myMaxFrozenIndex + 1 : 0;
        fireContentsChanged(this, begin, endIndex);
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

  @Override
  public void removeElement(@NotNull Object item, SearchEverywhereContributor<?> contributor) {
    if (listElements.isEmpty()) return;

    for (int i = 0; i < listElements.size(); i++) {
      SearchEverywhereFoundElementInfo info = listElements.get(i);
      if (info.getContributor() == contributor && info.getElement().equals(item)) {
        listElements.remove(i);
        int newSize = getSize();
        if (myMaxFrozenIndex >= newSize) myMaxFrozenIndex = newSize - 1;
        fireIntervalRemoved(this, i, i);
        return;
      }
    }
  }

  @Override
  public void setHasMore(SearchEverywhereContributor<?> contributor, boolean contributorHasMore) {
    hasMoreContributors.put(contributor, contributorHasMore);

    int lasItemIndex = listElements.size() - 1;
    if (lasItemIndex < 0) {
      return;
    }

    boolean hasMore = ContainerUtil.exists(hasMoreContributors.values(), Conditions.is(true));
    boolean alreadyHas = isMoreElement(lasItemIndex);
    if (alreadyHas && !hasMore) {
      listElements.remove(lasItemIndex);
      fireIntervalRemoved(this, lasItemIndex, lasItemIndex);
    }

    if (!alreadyHas && hasMore) {
      listElements.add(new SearchEverywhereFoundElementInfo(MORE_ELEMENT, 0, null));
      lasItemIndex += 1;
      fireIntervalAdded(this, lasItemIndex, lasItemIndex);
    }
  }

  @Override
  public void freezeElements() {
    if (listElements.isEmpty()) return;
    myMaxFrozenIndex = listElements.size() - 1;
    if (listElements.get(myMaxFrozenIndex) == MORE_ELEMENT) myMaxFrozenIndex--;
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
