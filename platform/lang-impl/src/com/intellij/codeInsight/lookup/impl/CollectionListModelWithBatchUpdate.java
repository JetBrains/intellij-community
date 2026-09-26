// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.ui.CollectionListModel;

import java.util.function.Consumer;

final class CollectionListModelWithBatchUpdate<T> extends CollectionListModel<T> {
  private boolean myListenersMuted = false;

  @Override
  protected void fireContentsChanged(Object source, int index0, int index1) {
    if (!myListenersMuted) super.fireContentsChanged(source, index0, index1);
  }

  @Override
  protected void fireIntervalAdded(Object source, int index0, int index1) {
    if (!myListenersMuted) super.fireIntervalAdded(source, index0, index1);
  }

  @Override
  protected void fireIntervalRemoved(Object source, int index0, int index1) {
    if (!myListenersMuted) super.fireIntervalRemoved(source, index0, index1);
  }

  /**
   * Perform batch update of list muting all the listeners when action is in progress. When action finishes,
   * listeners are notified that list content is changed completely.
   *
   * @param action that performs batch update; accepts this model as a parameter
   */
  public void performBatchUpdate(Consumer<? super CollectionListModel<T>> action) {
    try {
      myListenersMuted = true;
      action.accept(this);
    }
    finally {
      myListenersMuted = false;
    }
    allContentsChanged();
  }
}
