// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ListUtil {
  public static final String SELECTED_BY_MOUSE_EVENT = "byMouseEvent";

  public static <T> MouseMotionListener installAutoSelectOnMouseMove(@NotNull JList<T> list) {
    final MouseMotionAdapter listener = new MouseMotionAdapter() {
      boolean myIsEngaged = false;

      @Override
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

  @NotNull
  public static <T> List<T> removeSelectedItems(@NotNull JList<T> list) {
    return removeSelectedItems(list, null);
  }

  @NotNull
  public static <T> List<T> removeIndices(@NotNull JList<T> list, int[] indices) {
    return removeIndices(list, indices, null);
  }

  @NotNull
  public static <T> List<T> removeSelectedItems(@NotNull JList<T> list, @Nullable Condition<? super T> condition) {
    int[] indices = list.getSelectedIndices();
    return removeIndices(list, indices, condition);
  }

  public static <T> void removeItem(@NotNull ListModel<T> model, int index) {
    getExtension(model).remove(model, index);
  }

  public static <T> void removeAllItems(@NotNull ListModel<T> model) {
    getExtension(model).removeAll(model);
  }

  public static <T> void addAllItems(@NotNull ListModel<T> model, @NotNull List<? extends T> items) {
    getExtension(model).addAll(model, items);
  }

  private static <T> List<T> removeIndices(@NotNull JList<T> list, int @NotNull [] indices, @Nullable Condition<? super T> condition) {
    if (indices.length == 0) {
      return new ArrayList<>(0);
    }
    ListModel<T> model = list.getModel();
    ListModelExtension<T, ListModel<T>> extension = getExtension(model);
    int firstSelectedIndex = indices[0];
    ArrayList<T> removedItems = new ArrayList<>();
    int deletedCount = 0;
    for (int idx1 : indices) {
      int index = idx1 - deletedCount;
      if (index < 0 || index >= model.getSize()) continue;
      T obj = extension.get(model, index);
      if (condition == null || condition.value(obj)) {
        removedItems.add(obj);
        extension.remove(model, index);
        deletedCount++;
      }
    }
    if (model.getSize() == 0) {
      list.clearSelection();
    }
    else if (list.getSelectedValue() == null) {
      // if nothing remains selected, set selected row
      if (firstSelectedIndex >= model.getSize()) {
        list.setSelectedIndex(model.getSize() - 1);
      }
      else {
        list.setSelectedIndex(firstSelectedIndex);
      }
    }
    return removedItems;
  }

  public static <T> boolean canRemoveSelectedItems(@NotNull JList<T> list) {
    return canRemoveSelectedItems(list, null);
  }

  public static <T> boolean canRemoveSelectedItems(@NotNull JList<T> list, @Nullable Condition<? super T> condition) {
    int[] indices = list.getSelectedIndices();
    if (indices.length == 0) {
      return false;
    }
    ListModel<T> model = list.getModel();
    ListModelExtension<T, ListModel<T>> extension = getExtension(model);
    for (int index : indices) {
      if (index < 0 || index >= model.getSize()) continue;
      T obj = extension.get(model, index);
      if (condition == null || condition.value(obj)) {
        return true;
      }
    }

    return false;
  }

  public static <T> int moveSelectedItemsUp(@NotNull JList<T> list) {
    ListModel<T> model = list.getModel();
    ListModelExtension<T, ListModel<T>> extension = getExtension(model);
    int[] indices = list.getSelectedIndices();
    if (!canMoveSelectedItemsUp(list)) return 0;
    for (int index : indices) {
      T temp = extension.get(model, index);
      extension.set(model, index, extension.get(model, index - 1));
      extension.set(model, index - 1, temp);
      list.removeSelectionInterval(index, index);
      list.addSelectionInterval(index - 1, index - 1);
    }
    Rectangle cellBounds = list.getCellBounds(indices[0] - 1, indices[indices.length - 1] - 1);
    if (cellBounds != null) {
      list.scrollRectToVisible(cellBounds);
    }
    return indices.length;
  }

  public static <T> boolean canMoveSelectedItemsUp(@NotNull JList<T> list) {
    int[] indices = list.getSelectedIndices();
    return indices.length > 0 && indices[0] > 0;
  }

  public static <T> int moveSelectedItemsDown(@NotNull JList<T> list) {
    ListModel<T> model = list.getModel();
    ListModelExtension<T, ListModel<T>> extension = getExtension(model);
    int[] indices = list.getSelectedIndices();
    if (!canMoveSelectedItemsDown(list)) return 0;
    for (int i = indices.length - 1; i >= 0; i--) {
      int index = indices[i];
      T temp = extension.get(model, index);
      extension.set(model, index, extension.get(model, index + 1));
      extension.set(model, index + 1, temp);
      list.removeSelectionInterval(index, index);
      list.addSelectionInterval(index + 1, index + 1);
    }
    Rectangle cellBounds = list.getCellBounds(indices[0] + 1, indices[indices.length - 1] + 1);
    if (cellBounds != null) {
      list.scrollRectToVisible(cellBounds);
    }
    return indices.length;
  }

  public static <T> boolean isPointOnSelection(@NotNull JList<T> list, int x, int y) {
    int row = list.locationToIndex(new Point(x, y));
    if (row < 0) return false;
    return list.isSelectedIndex(row);
  }

  @Nullable
  public static <E> Component getDeepestRendererChildComponentAt(@NotNull JList<E> list, @NotNull Point point) {
    int idx = list.locationToIndex(point);
    if (idx < 0) return null;

    Rectangle cellBounds = list.getCellBounds(idx, idx);
    if (!cellBounds.contains(point)) return null;

    E value = list.getModel().getElementAt(idx);
    if (value == null) return null;

    Component rendererComponent = list.getCellRenderer().getListCellRendererComponent(list, value, idx, true, true);
    rendererComponent.setBounds(cellBounds.x, cellBounds.y, cellBounds.width, cellBounds.height);
    UIUtil.layoutRecursively(rendererComponent);

    int rendererRelativeX = point.x - cellBounds.x;
    int rendererRelativeY = point.y - cellBounds.y;
    return UIUtil.getDeepestComponentAt(rendererComponent, rendererRelativeX, rendererRelativeY);
  }

  public static <T> boolean canMoveSelectedItemsDown(@NotNull JList<T> list) {
    ListModel model = list.getModel();
    int[] indices = list.getSelectedIndices();
    return indices.length > 0 && indices[indices.length - 1] < model.getSize() - 1;
  }

  public static <T> Updatable addMoveUpListener(@NotNull JButton button, @NotNull JList<T> list) {
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        moveSelectedItemsUp(list);
        list.requestFocusInWindow();
      }
    });
    return disableWhenNoSelection(button, list);
  }


  public static <T> Updatable addMoveDownListener(@NotNull JButton button, @NotNull JList<T> list) {
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        moveSelectedItemsDown(list);
        list.requestFocusInWindow();
      }
    });
    return disableWhenNoSelection(button, list);
  }

  public static <T> Updatable addRemoveListener(@NotNull JButton button, @NotNull JList<T> list) {
    return addRemoveListener(button, list, null);
  }

  public static <T> Updatable addRemoveListener(@NotNull JButton button,
                                                @NotNull JList<T> list,
                                                @Nullable RemoveNotification<T> notification) {
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<T> items = removeSelectedItems(list);
        if (notification != null) {
          notification.itemsRemoved(items);
        }
        list.requestFocusInWindow();
      }
    });
    class MyListSelectionListener extends Updatable implements ListSelectionListener {
      MyListSelectionListener(JButton button) {
        super(button);
      }

      @Override
      public void valueChanged(ListSelectionEvent e) {
        setButtonEnabled(canRemoveSelectedItems(list));
      }

      @Override
      protected void update() {
        valueChanged(null);
      }
    }
    MyListSelectionListener listener = new MyListSelectionListener(button);
    list.getSelectionModel().addListSelectionListener(listener);
    listener.update();
    return listener;
  }

  public static <T> Updatable disableWhenNoSelection(@NotNull JButton button, @NotNull JList<T> list) {
    class MyListSelectionListener extends Updatable implements ListSelectionListener {
      MyListSelectionListener(JButton button) {
        super(button);
      }

      @Override
      public void valueChanged(ListSelectionEvent e) {
        setButtonEnabled((list.getSelectedIndex() != -1));
      }

      @Override
      public void update() {
        valueChanged(null);
      }
    }
    MyListSelectionListener listener = new MyListSelectionListener(button);
    list.getSelectionModel().addListSelectionListener(listener);
    listener.update();
    return listener;
  }

  public interface RemoveNotification<ItemType> {
    void itemsRemoved(List<ItemType> items);
  }

  /**
   * @noinspection unchecked
   */
  @NotNull
  private static <T, ModelType extends ListModel<T>> ListModelExtension<T, ModelType> getExtension(@NotNull ModelType model) {
    if (model instanceof DefaultListModel) return DEFAULT_MODEL;
    if (model instanceof SortedListModel) return SORTED_MODEL;
    if (model instanceof FilteringListModel) return FILTERED_MODEL;
    if (model instanceof CollectionListModel) return COLLECTION_MODEL;
    throw new AssertionError("Unknown model class: " + model.getClass().getName());
  }

  //@formatter:off
  private interface ListModelExtension<T, ModelType extends ListModel<T>> {
    T get(ModelType model, int index);
    void set(ModelType model, int index, T item);
    void remove(ModelType model, int index);
    void removeAll(ModelType model);
    void addAll(ModelType model, List<? extends T> item);
  }

  private static final ListModelExtension DEFAULT_MODEL = new ListModelExtension<Object, DefaultListModel<Object>>() {
    @Override public Object get(DefaultListModel<Object> model, int index) { return model.get(index);}
    @Override public void set(DefaultListModel<Object> model, int index, Object item) { model.set(index, item);}
    @Override public void remove(DefaultListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(DefaultListModel<Object> model) { model.removeAllElements();}
    @Override public void addAll(DefaultListModel<Object> model, List<?> item) { model.addElement(item);}
  };

  private static final ListModelExtension COLLECTION_MODEL = new ListModelExtension<Object, CollectionListModel<Object>>() {
    @Override public Object get(CollectionListModel<Object> model, int index) { return model.getElementAt(index);}
    @Override public void set(CollectionListModel<Object> model, int index, Object item) { model.setElementAt(item, index);}
    @Override public void remove(CollectionListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(CollectionListModel<Object> model) { model.removeAll();}
    @Override public void addAll(CollectionListModel<Object> model, List<?> items) { model.addAll(model.getSize(), items);}
  };

  private static final ListModelExtension SORTED_MODEL = new ListModelExtension<Object, SortedListModel<Object>>() {
    @Override public Object get(SortedListModel<Object> model, int index) { return model.get(index);}
    @Override public void set(SortedListModel<Object> model, int index, Object item) { model.remove(index); model.add(item);}
    @Override public void remove(SortedListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(SortedListModel<Object> model) { model.clear();}
    @Override public void addAll(SortedListModel<Object> model, List<?> items) { model.addAll(items);}
  };

  private static final ListModelExtension FILTERED_MODEL = new ListModelExtension<Object, FilteringListModel<Object>>() {
    @Override public Object get(FilteringListModel<Object> model, int index) { return model.getElementAt(index);}
    @Override public void set(FilteringListModel<Object> model, int index, Object item) { getExtension(model.getOriginalModel()).set(model.getOriginalModel(), index, item);}
    @Override public void remove(FilteringListModel<Object> model, int index) { model.remove(index);}
    @Override public void removeAll(FilteringListModel<Object> model) { model.replaceAll(Collections.emptyList());}
    @Override public void addAll(FilteringListModel<Object> model, List<?> items) { model.addAll(items);}
  };
  //@formatter:on
}
