// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.ui.CollectionListModel;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.List;

final class SmartPointerListModel<T> extends AbstractListModel<T> implements ModelDiff.Model<T> {
  private final CollectionListModel<Object> myDelegate = new CollectionListModel<>();

  SmartPointerListModel() {
    myDelegate.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        fireIntervalAdded(e.getSource(), e.getIndex0(), e.getIndex1());
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        fireIntervalRemoved(e.getSource(), e.getIndex0(), e.getIndex1());
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        fireContentsChanged(e.getSource(), e.getIndex0(), e.getIndex1());
      }
    });
  }

  @Override
  public int getSize() {
    return myDelegate.getSize();
  }

  @Override
  public T getElementAt(int index) {
    ThreadingAssertions.assertEventDispatchThread();
    return unwrap(myDelegate.getElementAt(index));
  }

  private Object wrap(T element) {
    return TreeAnchorizer.getService().createAnchor(element);
  }

  private T unwrap(Object at) {
    //noinspection unchecked
    return (T)TreeAnchorizer.getService().retrieveElement(at);
  }

  @Override
  public void addToModel(int idx, T element) {
    ThreadingAssertions.assertEventDispatchThread();
    myDelegate.add(Math.min(idx, getSize()), wrap(element));
  }

  @Override
  public void addAllToModel(int index, List<? extends T> elements) {
    ThreadingAssertions.assertEventDispatchThread();
    myDelegate.addAll(Math.min(index, getSize()), ContainerUtil.map(elements, this::wrap));
  }

  @Override
  public void removeRangeFromModel(int start, int end) {
    ThreadingAssertions.assertEventDispatchThread();
    if (start < getSize() && !isEmpty()) {
      myDelegate.removeRange(start, Math.min(end, getSize() - 1));
    }
  }

  boolean isEmpty() {
    return getSize() == 0;
  }

  void removeAll() {
    myDelegate.removeAll();
  }

  boolean contains(T elem) {
    return getItems().contains(elem);
  }

  @Unmodifiable
  List<T> getItems() {
    return ContainerUtil.map(myDelegate.getItems(), this::unwrap);
  }
}
