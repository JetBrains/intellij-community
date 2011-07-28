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
package com.intellij.util.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class UpDownHandler {
  private UpDownHandler() {
  }

  public static void register(JComponent input, final JComponent affectedComponent) {
    final SelectionMover mover = new SelectionMover(affectedComponent);
    new AnAction("Up") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        mover.move(-1);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("UP"), input);
    new AnAction("Down") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        mover.move(1);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DOWN"), input);
  }

  private static class SelectionMover {
    private JComboBox myCombo;
    private JList myList;
    
    private SelectionMover(JComponent comp) {
        if (comp instanceof JComboBox) {
          myCombo = (JComboBox)comp;
        } else if (comp instanceof JList) {
          myList = (JList)comp;
        }      
    }

    void move(int direction) {
    int index = -1;
    int size = 0;
    if (myCombo != null) {
      index = myCombo.getSelectedIndex();
      size = myCombo.getModel().getSize();
    } else if (myList != null) {
      index = myList.getSelectedIndex();
      size = myList.getModel().getSize();
    }
    if (index == -1 || size == 0) return;
    index += direction;

    if (index == size) {
      index = 0;
    } else if (index == -1) {
      index = size - 1;
    }
    if (myCombo != null) {
      myCombo.setSelectedIndex(index);
    } else if (myList != null) {
      myList.setSelectedIndex(index);
    }
  }
}
                         
}
