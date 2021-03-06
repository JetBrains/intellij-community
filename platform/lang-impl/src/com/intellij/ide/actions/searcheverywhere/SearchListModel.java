// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class SearchListModel extends AbstractListModel<Object> {

  static final Object MORE_ELEMENT = new Object();

  protected final List<SearchEverywhereFoundElementInfo> listElements = new ArrayList<>();
  protected boolean resultsExpired = false;

  public boolean isResultsExpired() {
    return resultsExpired;
  }

  public void expireResults() {
    resultsExpired = true;
  }

  @Override
  public int getSize() {
    return listElements.size();
  }

  @Override
  public Object getElementAt(int index) {
    return listElements.get(index).getElement();
  }

  public int getWeightAt(int index) {
    return listElements.get(index).getPriority();
  }

  public List<Object> getItems() {
    return new ArrayList<>(values());
  }

  public Collection<Object> getFoundItems(SearchEverywhereContributor contributor) {
    return listElements.stream()
      .filter(info -> info.getContributor() == contributor && info.getElement() != MORE_ELEMENT)
      .map(info -> info.getElement())
      .collect(Collectors.toList());
  }

  @NotNull
  protected List<SearchEverywhereContributor> contributors() {
    return Lists.transform(listElements, info -> info.getContributor());
  }

  @NotNull
  protected List<Object> values() {
    return Lists.transform(listElements, info -> info.getElement());
  }

  public abstract boolean hasMoreElements(SearchEverywhereContributor<?> contributor);

  public abstract void setHasMore(SearchEverywhereContributor<?> contributor, boolean contributorHasMore);

  public abstract void addElements(List<? extends SearchEverywhereFoundElementInfo> items);

  public abstract void removeElement(@NotNull Object item, SearchEverywhereContributor<?> contributor);

  public abstract void clearMoreItems();

  public abstract int getIndexToScroll(int currentIndex, boolean scrollDown);

  public void clear() {
    int index = getSize() - 1;
    listElements.clear();
    if (index >= 0) {
      fireIntervalRemoved(this, 0, index);
    }
  }

  public boolean contains(Object val) {
    return values().contains(val);
  }

  public boolean isMoreElement(int index) {
    return getElementAt(index) == MORE_ELEMENT;
  }

  @Nullable
  public <Item> SearchEverywhereContributor<Item> getContributorForIndex(int index) {
    //noinspection unchecked
    return (SearchEverywhereContributor<Item>)listElements.get(index).getContributor();
  }

  @SuppressWarnings("unchecked")
  public @Nullable ListCellRenderer<? super Object> getRendererForIndex(int index) {
    return (ListCellRenderer<? super Object>)listElements.get(index).getRenderer();
  }

  @NotNull
  public List<SearchEverywhereFoundElementInfo> getFoundElementsInfo() {
    return ContainerUtil.filter(listElements, info -> info.element != MORE_ELEMENT);
  }

  public Map<SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>> getFoundElementsMap() {
    return listElements.stream()
      .filter(info -> info.element != MORE_ELEMENT)
      .collect(Collectors.groupingBy(o -> o.getContributor(), Collectors.toCollection(ArrayList::new)));
  }
}
