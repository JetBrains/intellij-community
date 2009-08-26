/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ListPopupModel extends AbstractListModel {

  private final List<Object> myOriginalList;
  private final List<Object> myFilteredList = new ArrayList<Object>();

  private final ElementFilter myFilter;
  private final ListPopupStep myStep;

  private int myFullMatchIndex = -1;
  private int myStartsWithIndex = -1;
  private final SpeedSearch mySpeedSearch;

  public ListPopupModel(ElementFilter filter, SpeedSearch speedSearch, ListPopupStep step) {
    myFilter = filter;
    myStep = step;
    mySpeedSearch = speedSearch;
    myOriginalList = new ArrayList<Object>(step.getValues());
    rebuildLists();
  }

  public void deleteItem(final Object item) {
    final int i = myOriginalList.indexOf(item);
    if (i >= 0) {
      myOriginalList.remove(i);
      rebuildLists();
      fireContentsChanged(this, 0, myFilteredList.size());
    }
  }

  private void rebuildLists() {
    myFilteredList.clear();
    myFullMatchIndex = -1;
    myStartsWithIndex = -1;

    for (Object each : myOriginalList) {
      if (myFilter.shouldBeShowing(each)) {
        addToFiltered(each);
      }
    }
  }

  private void addToFiltered(Object each) {
    myFilteredList.add(each);
    String filterString = mySpeedSearch.getFilter().toUpperCase();
    String candidateString = myStep.getTextFor(each).toUpperCase();
    int index = myFilteredList.size() - 1;

    if (myFullMatchIndex == -1 && filterString.equals(candidateString)) {
      myFullMatchIndex = index;
    }

    if (myStartsWithIndex == -1 && candidateString.startsWith(filterString)) {
      myStartsWithIndex = index;
    }
  }

  public int getSize() {
    return myFilteredList.size();
  }

  public Object getElementAt(int index) {
    if (index >= myFilteredList.size()) {
      return null;
    }
    return myFilteredList.get(index);
  }

  public boolean isSeparatorAboveOf(Object aValue) {
    return getSeparatorAbove(aValue) != null;
  }

  public String getCaptionAboveOf(Object value) {
    ListSeparator separator = getSeparatorAbove(value);
    if (separator != null) {
      return separator.getText();
    }
    return "";
  }

  private ListSeparator getSeparatorAbove(Object value) {
    return myStep.getSeparatorAbove(value);
  }

  public void refilter() {
    rebuildLists();
    if (myFilteredList.isEmpty() && !myOriginalList.isEmpty()) {
      mySpeedSearch.noHits();
    }
    else {
      fireContentsChanged(this, 0, myFilteredList.size());
    }
  }

  public boolean isVisible(Object object) {
    return myFilteredList.contains(object);
  }

  public int getClosestMatchIndex() {
    return myFullMatchIndex != -1 ? myFullMatchIndex : myStartsWithIndex;
  }

}
