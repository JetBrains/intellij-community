package com.intellij.ui;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
*/
public class CollectionListModel extends AbstractListModel {
  private List myItems;

  public CollectionListModel(final List items) {
    myItems = items;
  }

  public int getSize() {
    return myItems.size();
  }

  public Object getElementAt(int index) {
    return myItems.get(index);
  }
}