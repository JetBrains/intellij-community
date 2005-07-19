/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;

public class ListScrollingUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.ListScrollingUtil");

  public static void selectItem(JList list, int index) {
    LOG.assertTrue(index >= 0);
    LOG.assertTrue(index < list.getModel().getSize());
    ensureIndexIsVisible(list, index, 0);
    list.setSelectedIndex(index);
  }

  public static void ensureSelectionExists(JList list) {
    int size = list.getModel().getSize();
    if (size == 0) {
      list.clearSelection();
      return;
    }
    int selectedIndex = list.getSelectedIndex();
    if (selectedIndex < 0 || selectedIndex >= size) { // fit index to [0, size-1] range
      selectedIndex = 0;
    }
    selectItem(list, selectedIndex);
  }

  public static boolean selectItem(JList list, Object item) {
    ListModel model = list.getModel();
    for(int i = 0; i < model.getSize(); i++){
      Object anItem = model.getElementAt(i);
      if (item.equals(anItem)){
        selectItem(list, i);
        return true;
      }
    }
    return false;
  }

  public static void movePageUp(JList list) {
    int visible = getVisibleRowCount(list);
    if (visible <= 0){
      moveHome(list);
      return;
    }
    int size = list.getModel().getSize();
    int decrement = visible - 1;
    int index = Math.max(list.getSelectedIndex() - decrement, 0);
    int top = list.getFirstVisibleIndex() - decrement;
    if (top < 0){
      top = 0;
    }
    int bottom = top + visible - 1;
    if (bottom >= size){
      bottom = size - 1;
    }
    //list.clearSelection();
    Rectangle cellBounds = list.getCellBounds(top, bottom);
    if (cellBounds == null) {
      moveHome(list);
      return;
    }
    list.scrollRectToVisible(cellBounds);
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public static void movePageDown(JList list) {
    int visible = getVisibleRowCount(list);
    if (visible <= 0){
      moveEnd(list);
      return;
    }
    int size = list.getModel().getSize();
    int increment = visible - 1;
    int index = Math.min(list.getSelectedIndex() + increment, size - 1);
    int top = list.getFirstVisibleIndex() + increment;
    int bottom = top + visible - 1;
    if (bottom >= size){
      bottom = size - 1;
    }
    //list.clearSelection();
    Rectangle cellBounds = list.getCellBounds(top, bottom);
    if (cellBounds == null) {
      moveEnd(list);
      return;
    }
    list.scrollRectToVisible(cellBounds);
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public static void moveHome(JList list) {
    list.setSelectedIndex(0);
    list.ensureIndexIsVisible(0);
  }

  public static void moveEnd(JList list) {
    int index = list.getModel().getSize() - 1;
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public static void ensureIndexIsVisible(JList list, int index, int moveDirection) {
    int visible = getVisibleRowCount(list);
    int size = list.getModel().getSize();
    int top, bottom;
    if (moveDirection == 0){
      top = index - (visible - 1)/ 2;
      bottom = top + visible - 1;
    }
    else if (moveDirection < 0){
      top = index - 2;
      bottom = index;
    }
    else{
      top = index;
      bottom = index + 2;
    }
    if (top < 0){
      top = 0;
    }
    if (bottom >= size){
      bottom = size - 1;
    }
    Rectangle cellBounds = list.getCellBounds(top, bottom);
    if (cellBounds != null){
      list.scrollRectToVisible(cellBounds);
    }
  }

  private static int getVisibleRowCount(JList list){
    return list.getLastVisibleIndex() - list.getFirstVisibleIndex() + 1;
  }
}
