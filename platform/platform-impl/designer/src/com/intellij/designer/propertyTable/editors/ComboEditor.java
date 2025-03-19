/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.editors;

import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author Alexander Lobas
 */
public abstract class ComboEditor extends PropertyEditor {
  protected final ComboBox myCombo;
  protected final Border myComboBorder;

  public ComboEditor() {
    myCombo = new ComboBox(-1);
    myComboBorder = myCombo.getBorder();
    installListeners(myCombo, createComboListeners());
  }

  protected ComboEditorListener createComboListeners() {
    return new ComboEditorListener(this);
  }

  public static void installListeners(JComboBox myCombo, final ComboEditorListener listener) {
    myCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        listener.onValueChosen();
      }
    });

    myCombo.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        listener.onCancelled();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombo;
  }

  @Override
  public void activate() {
    super.activate();
    myCombo.showPopup();
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCombo);
    ListCellRenderer renderer = myCombo.getRenderer();
    if (renderer instanceof JComponent) {
      SwingUtilities.updateComponentTreeUI((JComponent)renderer);
    }
  }

  public static class ComboEditorListener {
    private final PropertyEditor myEditor;

    public ComboEditorListener(PropertyEditor editor) {
      myEditor = editor;
    }

    protected void onValueChosen() {
      myEditor.fireValueCommitted(true, true);
    }

    protected void onCancelled() {
      myEditor.fireEditingCancelled();
    }
  }
}