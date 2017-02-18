/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class ListUtil {
  public static final String SELECTED_BY_MOUSE_EVENT = "byMouseEvent";
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.ListUtil");

  public static MouseMotionListener installAutoSelectOnMouseMove(final JList list) {
    final MouseMotionAdapter listener = new MouseMotionAdapter() {
      boolean myIsEngaged = false;

      public void mouseMoved(MouseEvent e) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e) && !(focusOwner instanceof JRootPane)) {
          Point point = e.getPoint();
          int index = list.locationToIndex(point);
          list.putClientProperty(SELECTED_BY_MOUSE_EVENT, Boolean.TRUE);
          list.setSelectedIndex(index);
          list.putClientProperty(SELECTED_BY_MOUSE_EVENT, Boolean.FALSE);
        }
        else {
          myIsEngaged = true;
        }
      }
    };
    list.addMouseMotionListener(listener);
    return listener;
  }

  public abstract static class Updatable {
    private final JButton myButton;
    private boolean myEnabled = true;

    public Updatable(JButton button) {
      myButton = button;
    }

    public void enable(boolean enable) {
      myEnabled = enable;
      update();
    }

    protected void setButtonEnabled(boolean enabled) {
      myButton.setEnabled(enabled && myEnabled);
    }

    protected abstract void update();
  }

  public static List removeSelectedItems(JList list) {
    return removeSelectedItems(list, null);
  }

  public static List removeIndices(JList list, int[] indices) {
    return removeIndices(list, indices, null);
  }

  public static <T> List<T> removeSelectedItems(JList list, Condition<T> condition) {
    int[] idxs = list.getSelectedIndices();
    return removeIndices(list, idxs, condition);
  }

  private static <T> List<T> removeIndices(JList list, int[] idxs, Condition<T> condition) {
    if (idxs.length == 0) {
      return new ArrayList<>(0);
    }
    ListModel model = list.getModel();
    int firstSelectedIndex = idxs[0];
    ArrayList<T> removedItems = new ArrayList<>();
    int deletedCount = 0;
    for (int idx = 0; idx < idxs.length; idx++) {
      int index = idxs[idx] - deletedCount;
      if (index < 0 || index >= model.getSize()) continue;
      T obj = (T)get(model, index);
      if (condition == null || condition.value(obj)) {
        removedItems.add(obj);
        remove(model, index);
        deletedCount++;
      }
    }
    if (model.getSize() == 0) {
      list.clearSelection();
    }
    else if (list.getSelectedValue() == null) {
      // if nothing remains selected, set selected row
      if (firstSelectedIndex >= model.getSize()){
        list.setSelectedIndex(model.getSize() - 1);
      }
      else{
        list.setSelectedIndex(firstSelectedIndex);
      }
    }
    return removedItems;
  }

  public static boolean canRemoveSelectedItems(JList list){
    return canRemoveSelectedItems(list, null);
  }

  public static boolean canRemoveSelectedItems(JList list, Condition applyable){
    ListModel model = list.getModel();
    int[] idxs = list.getSelectedIndices();
    if (idxs.length == 0) {
      return false;
    }

    for (int idx = 0; idx < idxs.length; idx++) {
      int index = idxs[idx];
      if (index < 0 || index >= model.getSize()) continue;
      Object obj = getExtensions(model).get(model, index);
      if (applyable == null || applyable.value(obj)) {
        return true;
      }
    }

    return false;
  }

  public static int moveSelectedItemsUp(JList list) {
    DefaultListModel model = getModel(list);
    int[] indices = list.getSelectedIndices();
    if (!canMoveSelectedItemsUp(list)) return 0;
    for(int i = 0; i < indices.length; i++){
      int index = indices[i];
      Object temp = model.get(index);
      model.set(index, model.get(index - 1));
      model.set(index - 1, temp);
      list.removeSelectionInterval(index, index);
      list.addSelectionInterval(index - 1, index - 1);
    }
    Rectangle cellBounds = list.getCellBounds(indices[0] - 1, indices[indices.length - 1] - 1);
    if (cellBounds != null){
      list.scrollRectToVisible(cellBounds);
    }
    return indices.length;
  }

  public static boolean canMoveSelectedItemsUp(JList list) {
    int[] indices = list.getSelectedIndices();
    return indices.length > 0 && indices[0] > 0;
  }

  public static int moveSelectedItemsDown(JList list) {
    DefaultListModel model = getModel(list);
    int[] indices = list.getSelectedIndices();
    if (!canMoveSelectedItemsDown(list)) return 0;
    for(int i = indices.length - 1; i >= 0 ; i--){
      int index = indices[i];
      Object temp = model.get(index);
      model.set(index, model.get(index + 1));
      model.set(index + 1, temp);
      list.removeSelectionInterval(index, index);
      list.addSelectionInterval(index + 1, index + 1);
    }
    Rectangle cellBounds = list.getCellBounds(indices[0] + 1, indices[indices.length - 1] + 1);
    if (cellBounds != null){
      list.scrollRectToVisible(cellBounds);
    }
    return indices.length;
  }

  private static DefaultListModel getModel(JList list) {
    final ListModel model = list.getModel();
    if (model instanceof FilteringListModel) {
      return (DefaultListModel)((FilteringListModel)model).getOriginalModel();
    }
    if (model instanceof CollectionListModel) {
      return getWrapperModel(((CollectionListModel)model));
    }
    return (DefaultListModel)model;
  }

  private static DefaultListModel getWrapperModel(final CollectionListModel source) {
      DefaultListModel model = new DefaultListModel() {
        @Override
        public Object set(int index, Object element) {
          Object o = source.getElementAt(index);
          source.setElementAt(element, index);
          return o;
        }

        @Override
        public Object get(int index) {
          return source.getElementAt(index);
        }
      };
    return model;
  }

  public static boolean canMoveSelectedItemsDown(JList list) {
    ListModel model = list.getModel();
    int[] indices = list.getSelectedIndices();
    return indices.length > 0 && indices[indices.length - 1] < model.getSize() - 1;
  }

  public static Updatable addMoveUpListener(JButton button, final JList list) {
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        moveSelectedItemsUp(list);
        list.requestFocusInWindow();
      }
    });
    return disableWhenNoSelection(button, list);
  }


  public static Updatable addMoveDownListener(JButton button, final JList list) {
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        moveSelectedItemsDown(list);
        list.requestFocusInWindow();
      }
    });
    return disableWhenNoSelection(button, list);
  }

  public static Updatable addRemoveListener(final JButton button, final JList list) {
    return addRemoveListener(button, list, null);
  }

  public static Updatable addRemoveListener(final JButton button, final JList list, final RemoveNotification<String> notification) {
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<String> items = removeSelectedItems(list);
        if (notification != null)
          notification.itemsRemoved(items);
        list.requestFocusInWindow();
      }
    });
    class MyListSelectionListener extends Updatable implements ListSelectionListener {
      public MyListSelectionListener(JButton button) {
        super(button);
      }

      public void valueChanged(ListSelectionEvent e) {
        setButtonEnabled(canRemoveSelectedItems(list));
      }

      protected void update() {
        valueChanged(null);
      }
    }
    MyListSelectionListener listener = new MyListSelectionListener(button);
    list.getSelectionModel().addListSelectionListener(listener);
    listener.update();
    return listener;
  }

  private static Object get(ListModel model, int index) {
    return getExtensions(model).get(model, index);
  }

  private static void remove(ListModel model, int index) {
    getExtensions(model).remove(model, index);
  }

  public static Updatable disableWhenNoSelection(final JButton button, final JList list) {
    class MyListSelectionListener extends Updatable implements ListSelectionListener {
      public MyListSelectionListener(JButton button) {
        super(button);
      }

      public void valueChanged(ListSelectionEvent e) {
        setButtonEnabled((list.getSelectedIndex() != -1));
      }

      public void update() {
        valueChanged(null);
      }
    }
    MyListSelectionListener listener = new MyListSelectionListener(button);
    list.getSelectionModel().addListSelectionListener(listener);
    listener.update();
    return listener;
  }

  public static interface RemoveNotification<ItemType> {
    void itemsRemoved(List<ItemType> items);
  }

  private static ListModelExtension getExtensions(ListModel model) {
    if (model instanceof DefaultListModel) return DEFAULT_MODEL;
    if (model instanceof SortedListModel) return SORTED_MODEL;
    if (model instanceof FilteringListModel) return FILTERED_MODEL;
    if (model instanceof CollectionListModel) return COLLECTION_MODEL;

    if (model == null) LOG.assertTrue(false);
    else LOG.error("Unknown model class: " + model.getClass().getName());
    return null;
  }

  private static interface ListModelExtension<ModelType extends ListModel> {
    Object get(ModelType model, int index);
    void remove(ModelType model, int index);
  }

  private static final ListModelExtension DEFAULT_MODEL = new ListModelExtension<DefaultListModel>() {
    public Object get(DefaultListModel model, int index) {
      return model.get(index);
    }

    public void remove(DefaultListModel model, int index) {
      model.remove(index);
    }
  };

  private static final ListModelExtension COLLECTION_MODEL = new ListModelExtension<CollectionListModel>() {
    public Object get(CollectionListModel model, int index) {
      return model.getElementAt(index);
    }

    public void remove(CollectionListModel model, int index) {
      model.remove(index);
    }
  };

  private static final ListModelExtension SORTED_MODEL = new ListModelExtension<SortedListModel>() {
    public Object get(SortedListModel model, int index) {
      return model.get(index);
    }

    public void remove(SortedListModel model, int index) {
      model.remove(index);
    }
  };

  private static final ListModelExtension FILTERED_MODEL = new ListModelExtension<FilteringListModel>() {
    public Object get(FilteringListModel model, int index) {
      return model.getElementAt(index);
    }

    public void remove(FilteringListModel model, int index) {
      model.remove(index);
    }
  };
}
