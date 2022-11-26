// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts.Label;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AddEditDeleteListPanel<T> extends AddDeleteListPanel<T> {
  public AddEditDeleteListPanel(@Label final String title, final List<T> initialList) {
    super(title, initialList);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        editSelectedItem();
        return true;
      }
    }.installOn(myList);
  }

  @Override
  protected void customizeDecorator(ToolbarDecorator decorator) {
    decorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        editSelectedItem();
      }
    });
  }

  @Nullable
  protected abstract T editSelectedItem(T item);

  private void editSelectedItem() {
    int index = myList.getSelectedIndex();
    if (index >= 0) {
      T newValue = editSelectedItem(myListModel.get(index));
      if (newValue != null) {
        myListModel.set(index, newValue);
      }
    }
  }


}
