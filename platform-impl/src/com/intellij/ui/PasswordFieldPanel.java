package com.intellij.ui;

import javax.swing.*;

public class PasswordFieldPanel extends FieldPanel {
  public PasswordFieldPanel() {
    super(new JPasswordField(30));
  }
}
