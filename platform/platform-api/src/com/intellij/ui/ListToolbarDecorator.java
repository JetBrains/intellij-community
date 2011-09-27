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

  ListToolbarDecorator(JList list) {
    myList = list;
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
    final AddRemoveUpDownPanel p = getPanel();
    if (p != null) {
      if (myList.isEnabled()) {
        final int index = myList.getSelectedIndex();
        if (0 <= index && index < myList.getModel().getSize()) {
          final boolean downEnable = myList.getMaxSelectionIndex() < myList.getModel().getSize() - 1;
          final boolean upEnable = myList.getMinSelectionIndex() > 0;
          p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, true);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, upEnable);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, downEnable);
        }
        else {
          p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
        }
        p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, true);
      }
      else {
        p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
      }
    }
  }
}
