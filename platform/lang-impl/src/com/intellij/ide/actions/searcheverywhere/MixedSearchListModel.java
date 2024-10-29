// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

final class MixedSearchListModel extends SearchListModel {

  private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors = new HashMap<>();

  private final AtomicReference<SearchEverywhereFoundElementInfo> myNotificationElement = new AtomicReference<>();

  private final SearchEverywhereReorderingService myReorderingService = SearchEverywhereReorderingService.getInstance();

  private Computable<String> tabIDProvider;

  private Comparator<? super SearchEverywhereFoundElementInfo> myElementsComparator = SearchEverywhereFoundElementInfo.COMPARATOR.reversed();

  // new elements cannot be added before this index when "more..." elements are loaded
  private int myMaxFrozenIndex = -1;

  public void setElementsComparator(Comparator<? super SearchEverywhereFoundElementInfo> elementsComparator) {
    myElementsComparator = elementsComparator;
  }

  public void setTabIDProvider(Computable<String> provider) {
    tabIDProvider = provider;
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

      addNotificationIfApplicable();
      listElements.addAll(items);
      if (!listElements.isEmpty()) fireIntervalAdded(this, 0, listElements.size() - 1);

      resultsExpired = false;
    }
    else {
      addNotificationIfApplicable();
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

    reorderItemsIfApplicable();
  }

  private void reorderItemsIfApplicable() {
    if (myReorderingService != null && myMaxFrozenIndex == -1 && tabIDProvider != null) {
      String tabID = tabIDProvider.compute();
      myReorderingService.reorder(tabID, listElements);
      fireContentsChanged(this, 0, listElements.size() - 1);
    }
  }

  private void addNotificationIfApplicable() {
    var notificationElement = myNotificationElement.getAndSet(null);
    if (notificationElement != null && AdvancedSettings.getBoolean("search.everywhere.show.results.notification")) {
      var lastItemIndex = listElements.size() - 1;
      listElements.removeIf(info -> info.getElement() instanceof ResultsNotificationElement);
      var newLastItemIndex = listElements.size() - 1;
      if (newLastItemIndex < lastItemIndex) {
        fireIntervalRemoved(this, 0, lastItemIndex - newLastItemIndex - 1);
      }

      listElements.add(notificationElement);
      newLastItemIndex++;
      fireIntervalAdded(this, newLastItemIndex, newLastItemIndex);
      if (myMaxFrozenIndex != -1) {
        myMaxFrozenIndex++;
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
      SearchEverywhereUsageTriggerCollector.MORE_ITEM_SHOWN.log(
        SearchEverywhereUsageTriggerCollector.ITEM_NUMBER_BEFORE_MORE.with(listElements.size()),
        SearchEverywhereUsageTriggerCollector.IS_ONLY_MORE.with(true)
      );
      listElements.add(new SearchEverywhereFoundElementInfo(MORE_ELEMENT, 0, null));
      lasItemIndex += 1;
      fireIntervalAdded(this, lasItemIndex, lasItemIndex);
    }
  }

  @Override
  public void addNotificationElement(@NotNull String label) {
    myNotificationElement.getAndSet(new SearchEverywhereFoundElementInfo(
      new ResultsNotificationElement(label), Integer.MAX_VALUE - 1, null));
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
    myNotificationElement.set(null);
    super.clear();
  }

  @Override
  public void expireResults() {
    super.expireResults();
    myNotificationElement.set(null);
    myMaxFrozenIndex = -1;
  }
}
