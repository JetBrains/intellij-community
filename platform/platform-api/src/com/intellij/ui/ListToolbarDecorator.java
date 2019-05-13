/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
