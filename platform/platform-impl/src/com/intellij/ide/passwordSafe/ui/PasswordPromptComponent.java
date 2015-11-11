/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe.ui;

import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class PasswordPromptComponent {
  private JPanel myRootPanel;
  private JPanel myMessagePanel;
  private JPanel myUserPanel;
  private JPanel myPasswordPanel;
  private JPasswordField myPasswordField;
  private JCheckBox myRememberCheckBox;
  private JLabel myPasswordLabel;
  private JLabel myUserLabel;
  private JTextField myUserTextField;
  private JLabel myIconLabel;

  public PasswordPromptComponent(PasswordSafeSettings.ProviderType type,
                                 String message,
                                 boolean showUserName,
                                 String passwordFieldLabel,
                                 String rememberCheckBoxLabel) {
    myIconLabel.setText("");
    myIconLabel.setIcon(Messages.getWarningIcon());
    JTextPane messageField = Messages.configureMessagePaneUi(new JTextPane(), message, UIUtil.FontSize.SMALL);
    myMessagePanel.add(Messages.wrapToScrollPaneIfNeeded(messageField, 0, 4), BorderLayout.CENTER);
    setTargetProviderType(type);
    setUserInputVisible(showUserName);
    if (passwordFieldLabel != null) myPasswordLabel.setText(passwordFieldLabel);
    if (rememberCheckBoxLabel != null) {
      myRememberCheckBox.setText(rememberCheckBoxLabel);
      DialogUtil.registerMnemonic(myRememberCheckBox);
    }
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myUserTextField.isEnabled() && StringUtil.isEmpty(myUserTextField.getText()) ? myUserTextField : myPasswordField;
  }

  private void setUserInputVisible(boolean visible) {
    UIUtil.setEnabled(myUserPanel, visible, true);
    myUserPanel.setVisible(visible);
  }

  private void setTargetProviderType(PasswordSafeSettings.ProviderType type) {
    switch (type) {
      case MASTER_PASSWORD:
        myRememberCheckBox.setEnabled(true);
        myRememberCheckBox.setSelected(true);
        myRememberCheckBox.setToolTipText("The password will be stored between application sessions.");
        break;
      case MEMORY_ONLY:
        myRememberCheckBox.setEnabled(true);
        myRememberCheckBox.setSelected(true);
        myRememberCheckBox.setToolTipText("The password will be stored only during this application session.");
        break;
      case DO_NOT_STORE:
        myRememberCheckBox.setVisible(false);
        myRememberCheckBox.setEnabled(false);
        myRememberCheckBox.setSelected(false);
        myRememberCheckBox.setToolTipText("The password storing is disabled.");
        break;
      default:
        throw new AssertionError("Unknown policy type: " + type);
    }
  }

  public String getUserName() {
    return myUserTextField.getText();
  }

  public void setUserName(String text) {
    myUserTextField.setText(text);
  }

  public char[] getPassword() {
    return myPasswordField.getPassword();
  }

  public void setPassword(String text) {
    myPasswordField.setText(text);
  }

  public boolean isRememberSelected() {
    return myRememberCheckBox.isSelected();
  }

  public void setRememberSelected(boolean selected) {
    myRememberCheckBox.setSelected(selected);
  }
}
