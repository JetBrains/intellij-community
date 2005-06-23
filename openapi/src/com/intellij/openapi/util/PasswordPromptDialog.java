package com.intellij.openapi.util;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

public class PasswordPromptDialog extends DialogWrapper {
  private JPasswordField myPasswordField;
  private String myPrompt;
  private final String myDefaultValue;

  public PasswordPromptDialog(String prompt, final String title, @Nullable final String defaultValue) {
    super(true);
    myDefaultValue = defaultValue;
    setTitle(title);
    myPrompt = prompt;
    init();
  }

  protected JComponent createNorthPanel() {
    return new JLabel(myPrompt);
  }

  protected JComponent createCenterPanel() {
    myPasswordField = new JPasswordField();

    if (myDefaultValue != null) {
      myPasswordField.setText(myDefaultValue);
    }

    return myPasswordField;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }

  public String getPassword() {
    return new String(myPasswordField.getPassword());
  }
}
