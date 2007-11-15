/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.ui.popup.util.ElementFilter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ListPopupModel extends AbstractListModel {

  private List myOriginalList;
  private List<Object> myFilteredList = new ArrayList<Object>();

  private ElementFilter myFilter;
  private ListPopupStep myStep;

  private int myFullMatchIndex = -1;
  private int myStartsWithIndex = -1;

  public ListPopupModel(ElementFilter filter, ListPopupStep step) {
    myFilter = filter;
    myStep = step;
    myOriginalList = Collections.unmodifiableList(step.getValues());
    rebuildLists();
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
    String filterString = myFilter.getSpeedSearch().getFilter().toUpperCase();
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
      myFilter.getSpeedSearch().backspace();
      refilter();
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
