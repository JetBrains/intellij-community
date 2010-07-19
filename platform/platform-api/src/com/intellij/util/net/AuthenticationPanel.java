/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.net;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Panel for authentication - contains text fields for login and password, and a checkbox to remember
 * password in the Password Safe.
 * 
 * @author stathik
 * @author Kirill Likhodedov
 */
public class AuthenticationPanel extends JPanel {
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;
  private JTextField myLoginTextField;
  private JPasswordField myPasswordTextField;
  private JCheckBox rememberPasswordCheckBox;

  /**
   * @param description       Description text above the text fields.
   * @param login             Initial login value.
   * @param password          Initial password value.
   * @param rememberPassword  Default value for the 'remember password' checkbox.
   */
  public AuthenticationPanel(@Nullable String description, @Nullable String login, @Nullable String password, boolean rememberPassword) {
    add(myMainPanel);
    myDescriptionLabel.setText(description);
    myLoginTextField.setText(login);
    myPasswordTextField.setText(password);
    rememberPasswordCheckBox.setSelected(rememberPassword);
  }

  public String getLogin () {
    return myLoginTextField.getText();
  }

  public String getPassword () {
    return new String (myPasswordTextField.getPassword());
  }

  public boolean isRememberPassword () {
    return rememberPasswordCheckBox.isSelected();
  }

  /**
   * @return the component which should be focused when the dialog appears on the screen. May be used in dialogs.
   * @see com.intellij.openapi.ui.DialogWrapper#getPreferredFocusedComponent()
   */
  public JComponent getPreferredFocusedComponent() {
    return getLogin().isEmpty() ? myLoginTextField : myPasswordTextField;
  }

}
