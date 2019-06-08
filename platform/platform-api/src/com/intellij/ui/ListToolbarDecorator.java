// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
class ListToolbarDecorator extends ToolbarDecorator {
  private final JList myList;
  private final EditableModel myEditableModel;

  ListToolbarDecorator(JList list, @Nullable EditableModel editableModel) {
    myList = list;
    myEditableModel = editableModel;
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = true;
    createActions();
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
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
        ((ListModel)evt.getOldValue()).removeListDataListener(modelListener);
      }
      if (evt.getNewValue() != null) {
        ((ListModel)evt.getNewValue()).addListDataListener(modelListener);
      }
    });
    myList.addPropertyChangeListener("enabled", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateButtons();
      }
    });
  }

  private void createActions() {
    myRemoveAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.removeSelectedItems(myList);
        updateButtons();
      }
    };
    myUpAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.moveSelectedItemsUp(myList);
        updateButtons();
      }
    };
    myDownAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.moveSelectedItemsDown(myList);
        updateButtons();
      }
    };
  }

  @Override
  protected JComponent getComponent() {
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
  public ToolbarDecorator setVisibleRowCount(int rowCount) {
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
