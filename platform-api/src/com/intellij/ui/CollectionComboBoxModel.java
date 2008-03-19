package com.intellij.ui;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
*/
public class CollectionComboBoxModel extends AbstractListModel implements ComboBoxModel {
  private List myItems;
  private Object mySelection;

  public CollectionComboBoxModel(final List items, final Object selection) {
    myItems = items;
    mySelection = selection;
  }

  public int getSize() {
    return myItems.size();
  }

  public Object getElementAt(final int index) {
    return myItems.get(index);
  }

  public void setSelectedItem(final Object anItem) {
    mySelection = anItem;
  }

  public Object getSelectedItem() {
    return mySelection;
  }
}
