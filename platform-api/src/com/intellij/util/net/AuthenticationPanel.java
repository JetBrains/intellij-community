/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Sep 12, 2003
 * Time: 8:40:40 PM
 * To change this template use Options | File Templates.
 */
public class AuthenticationPanel extends JPanel {
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;
  private JTextField myLoginTextField;
  private JPasswordField myPasswordTextField;
  private JCheckBox rememberPasswordCheckBox;

  public AuthenticationPanel(String description, String login, String password, boolean rememberPassword) {
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
}
