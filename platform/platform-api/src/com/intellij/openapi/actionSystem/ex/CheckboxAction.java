/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public abstract class CheckboxAction extends ToggleAction implements CustomComponentAction {
  private JCheckBox myCheckBox;

  protected CheckboxAction() {}

  protected CheckboxAction(final String text) {
    super(text);
  }

  public JCheckBox getCheckBox() {
    return myCheckBox;
  }

  protected CheckboxAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  public JComponent createCustomComponent(Presentation presentation) {
    myCheckBox = new JCheckBox(presentation.getText());
    myCheckBox.setToolTipText(presentation.getDescription());
    myCheckBox.setMnemonic(presentation.getMnemonic());
    myCheckBox.setDisplayedMnemonicIndex(presentation.getDisplayedMnemonicIndex());

    myCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        CheckboxAction.this.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(myCheckBox),
                                                              ActionPlaces.UNKNOWN, CheckboxAction.this.getTemplatePresentation(),
                                                              ActionManager.getInstance(), 0));
      }
    });

    return myCheckBox;
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    if (myCheckBox != null) {
      myCheckBox.setSelected(((Boolean)e.getPresentation().getClientProperty(SELECTED_PROPERTY)).booleanValue());
      myCheckBox.setEnabled(e.getPresentation().isEnabled());
      myCheckBox.setVisible(e.getPresentation().isVisible());
    }
  }
}
