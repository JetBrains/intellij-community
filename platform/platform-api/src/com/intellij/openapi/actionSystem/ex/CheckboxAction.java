// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JBCheckBox checkBox = new JBCheckBox();
    checkBox.setFocusable(false);
    updateCustomComponent(checkBox, presentation);
    return createCheckboxComponent(checkBox, this, place);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    JComponent property = presentation.getClientProperty(COMPONENT_KEY);
    if (property instanceof JCheckBox) {
      JCheckBox checkBox = (JCheckBox)property;

      updateCustomComponent(checkBox, presentation);
    }
  }

  protected void updateCustomComponent(JCheckBox checkBox, Presentation presentation) {
    updateCheckboxPresentation(checkBox, presentation);
    checkBox.setSelected(Boolean.TRUE.equals(presentation.getClientProperty(SELECTED_PROPERTY)));
  }

  static void updateCheckboxPresentation(JCheckBox checkBox, Presentation presentation) {
    checkBox.setText(presentation.getText());
    checkBox.setToolTipText(presentation.getDescription());
    checkBox.setMnemonic(presentation.getMnemonic());
    checkBox.setDisplayedMnemonicIndex(presentation.getDisplayedMnemonicIndex());
    checkBox.setEnabled(presentation.isEnabled());
    checkBox.setVisible(presentation.isVisible());
  }

  @NotNull
  static JComponent createCheckboxComponent(@NotNull JCheckBox checkBox, @NotNull AnAction action, @NotNull String place) {
    // this component cannot be stored right in AnAction because of action system architecture:
    // one action can be shown on multiple toolbars simultaneously
    checkBox.setOpaque(false);
    checkBox.setBorder(JBUI.Borders.emptyRight(9));

    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox checkBox = (JCheckBox)e.getSource();
        ActionToolbar actionToolbar =
          ComponentUtil.getParentOfType((Class<? extends ActionToolbar>)ActionToolbar.class, (Component)checkBox);
        DataContext dataContext =
          actionToolbar != null ? actionToolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext(checkBox);
        InputEvent inputEvent = new KeyEvent(checkBox, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        AnActionEvent event = AnActionEvent.createFromAnAction(action, inputEvent, place, dataContext);
        ActionUtil.performActionDumbAwareWithCallbacks(action, event, dataContext);
      }
    });

    return checkBox;
  }
}
