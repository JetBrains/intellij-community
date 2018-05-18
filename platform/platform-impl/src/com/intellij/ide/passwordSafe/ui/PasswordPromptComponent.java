// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe.ui;

import com.intellij.credentialStore.RememberCheckBoxState;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class PasswordPromptComponent {
  private JPanel myRootPanel;
  private JPanel myMessagePanel;
  private JPanel myUserPanel;
  private JPasswordField myPasswordField;
  private JCheckBox myRememberCheckBox;
  private JLabel myPasswordLabel;
  private JTextField myUserTextField;
  private JLabel myIconLabel;

  public PasswordPromptComponent(boolean memoryOnly, String message) {
    this(memoryOnly, message, true, null);
  }

  public PasswordPromptComponent(boolean memoryOnly, String message, boolean showUserName, @Nullable String passwordFieldLabel) {
    myIconLabel.setText("");
    myIconLabel.setIcon(Messages.getWarningIcon());
    JTextPane messageField = Messages.configureMessagePaneUi(new JTextPane(), message, UIUtil.FontSize.SMALL);
    myMessagePanel.add(Messages.wrapToScrollPaneIfNeeded(messageField, 80, 4), BorderLayout.CENTER);

    if (memoryOnly) {
      myRememberCheckBox.setVisible(false);
      myRememberCheckBox.setEnabled(false);
      myRememberCheckBox.setSelected(false);
    }
    else {
      myRememberCheckBox.setSelected(PasswordSafe.getInstance().isRememberPasswordByDefault());
      myRememberCheckBox.setToolTipText("The password will be stored between application sessions.");
    }

    setUserInputVisible(showUserName);
    if (passwordFieldLabel != null) {
      myPasswordLabel.setText(passwordFieldLabel);
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

  public void updateRememberState() {
    if (myRememberCheckBox.isEnabled()) {
      RememberCheckBoxState.update(myRememberCheckBox);
    }
  }
}
