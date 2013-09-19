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
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public abstract class CheckboxAction extends ToggleAction implements CustomComponentAction {

  protected CheckboxAction() {}

  protected CheckboxAction(final String text) {
    super(text);
  }

  protected CheckboxAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    // this component cannot be stored right here because of action system architecture:
    // one action can be shown on multiple toolbars simultaneously
    JCheckBox checkBox = new JCheckBox(presentation.getText());
    checkBox.setOpaque(false);
    checkBox.setToolTipText(presentation.getDescription());
    checkBox.setMnemonic(presentation.getMnemonic());
    checkBox.setDisplayedMnemonicIndex(presentation.getDisplayedMnemonicIndex());
    checkBox.setSelected(Boolean.TRUE.equals(presentation.getClientProperty(SELECTED_PROPERTY)));

    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox checkBox = (JCheckBox)e.getSource();
        ActionToolbar actionToolbar = UIUtil.getParentOfType(ActionToolbar.class, checkBox);
        DataContext dataContext =
          actionToolbar != null ? actionToolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext(checkBox);
        CheckboxAction.this.actionPerformed(new AnActionEvent(null, dataContext,
                                                              ActionPlaces.UNKNOWN, CheckboxAction.this.getTemplatePresentation(),
                                                              ActionManager.getInstance(), 0));
      }
    });

    return checkBox;
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    Object property = e.getPresentation().getClientProperty(CUSTOM_COMPONENT_PROPERTY);
    if (property instanceof JCheckBox) {
      JCheckBox checkBox = (JCheckBox)property;

      checkBox.setSelected(Boolean.TRUE.equals(e.getPresentation().getClientProperty(SELECTED_PROPERTY)));
      checkBox.setEnabled(e.getPresentation().isEnabled());
      checkBox.setVisible(e.getPresentation().isVisible());
    }
  }
}
