// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

public abstract class CheckboxAction extends ToggleAction implements CustomComponentAction {

  protected CheckboxAction() {}

  protected CheckboxAction(@NlsContexts.Checkbox String text) {
    super(text);
  }

  protected CheckboxAction(@NotNull Supplier<String> dynamicText) {
    super(dynamicText);
  }

  protected CheckboxAction(@NlsContexts.Checkbox String text,
                           @NlsContexts.Tooltip String description,
                           final Icon icon) {
    super(text, description, icon);
  }

  protected CheckboxAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, final Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JBCheckBox checkBox = new JBCheckBox();
    checkBox.setFocusable(false);
    return createCheckboxComponent(checkBox, this, place);
  }

  @Override
  public void updateCustomComponent(@NotNull JComponent component,
                                    @NotNull Presentation presentation) {
    if (component instanceof JCheckBox checkBox) {
      updateCustomComponent(checkBox, presentation);
    }
  }

  protected void updateCustomComponent(JCheckBox checkBox, Presentation presentation) {
    updateCheckboxPresentation(checkBox, presentation);
    checkBox.setSelected(Toggleable.isSelected(presentation));
  }

  static void updateCheckboxPresentation(JCheckBox checkBox, Presentation presentation) {
    checkBox.setText(presentation.getText(true));
    checkBox.setToolTipText(presentation.getDescription());
    checkBox.setMnemonic(presentation.getMnemonic());
    checkBox.setDisplayedMnemonicIndex(presentation.getDisplayedMnemonicIndex());
    checkBox.setEnabled(presentation.isEnabled());
    checkBox.setVisible(presentation.isVisible());
  }

  static @NotNull JComponent createCheckboxComponent(@NotNull JCheckBox checkBox, @NotNull AnAction action, @NotNull String place) {
    // this component cannot be stored right in AnAction because of action system architecture:
    // one action can be shown on multiple toolbars simultaneously
    checkBox.setOpaque(false);
    checkBox.setBorder(JBUI.Borders.emptyRight(9));

    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox checkBox = (JCheckBox)e.getSource();
        DataContext dataContext = ActionToolbar.getDataContextFor(checkBox);
        InputEvent inputEvent = new KeyEvent(checkBox, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        AnActionEvent event = AnActionEvent.createFromAnAction(action, inputEvent, place, dataContext);
        ActionUtil.performAction(action, event);
      }
    });

    return checkBox;
  }
}
