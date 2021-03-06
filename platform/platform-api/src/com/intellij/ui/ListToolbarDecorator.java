// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * @author Konstantin Bulenkov
 */
class ListToolbarDecorator<T> extends ToolbarDecorator {
  private final JList<T> myList;
  private final EditableModel myEditableModel;

  ListToolbarDecorator(@NotNull JList<T> list, @Nullable EditableModel editableModel) {
    myList = list;
    myEditableModel = editableModel;
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = true;
    createActions();
    myList.addListSelectionListener(__ -> updateButtons());
    ListDataListener modelListener = new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateButtons();
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        updateButtons();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        updateButtons();
      }
    };
    myList.getModel().addListDataListener(modelListener);
    myList.addPropertyChangeListener("model", evt -> {
      if (evt.getOldValue() != null) {
        ((ListModel<T>)evt.getOldValue()).removeListDataListener(modelListener);
      }
      if (evt.getNewValue() != null) {
        ((ListModel<T>)evt.getNewValue()).addListDataListener(modelListener);
      }
    });
    myList.addPropertyChangeListener("enabled", __ -> updateButtons());
  }

  private void createActions() {
    myRemoveAction = __ -> {
      ListUtil.removeSelectedItems(myList);
      updateButtons();
    };
    myUpAction = __ -> {
      ListUtil.moveSelectedItemsUp(myList);
      updateButtons();
    };
    myDownAction = __ -> {
      ListUtil.moveSelectedItemsDown(myList);
      updateButtons();
    };
  }

  @Override
  protected @NotNull JComponent getComponent() {
    return myList;
  }

  @Override
  protected void updateButtons() {
    final CommonActionsPanel p = getActionsPanel();
    if (p != null) {
      boolean someElementSelected;
      if (myList.isEnabled()) {
        final int index = myList.getSelectedIndex();
        someElementSelected = 0 <= index && index < myList.getModel().getSize();
        if (someElementSelected) {
          final boolean downEnable = myList.getMaxSelectionIndex() < myList.getModel().getSize() - 1;
          final boolean upEnable = myList.getMinSelectionIndex() > 0;
          final boolean editEnabled = myList.getSelectedIndices().length == 1;
          p.setEnabled(CommonActionsPanel.Buttons.EDIT, editEnabled);
          p.setEnabled(CommonActionsPanel.Buttons.UP, upEnable);
          p.setEnabled(CommonActionsPanel.Buttons.DOWN, downEnable);
        }
        else {
          p.setEnabled(CommonActionsPanel.Buttons.EDIT, false);
          p.setEnabled(CommonActionsPanel.Buttons.UP, false);
          p.setEnabled(CommonActionsPanel.Buttons.DOWN, false);
        }
        p.setEnabled(CommonActionsPanel.Buttons.ADD, true);
      }
      else {
        someElementSelected = false;
        p.setEnabled(CommonActionsPanel.Buttons.ADD, false);
        p.setEnabled(CommonActionsPanel.Buttons.UP, false);
        p.setEnabled(CommonActionsPanel.Buttons.DOWN, false);
      }

      p.setEnabled(CommonActionsPanel.Buttons.REMOVE, someElementSelected);
      updateExtraElementActions(someElementSelected);
    }
  }

  @Override
  public @NotNull ToolbarDecorator setVisibleRowCount(int rowCount) {
    myList.setVisibleRowCount(rowCount);
    return this;
  }

  @Override
  protected boolean isModelEditable() {
    return myEditableModel != null || myList.getModel() instanceof EditableModel;
  }

  @Override
  protected void installDnDSupport() {
    RowsDnDSupport.install(myList, myEditableModel != null ? myEditableModel : (EditableModel)myList.getModel());
  }
}
