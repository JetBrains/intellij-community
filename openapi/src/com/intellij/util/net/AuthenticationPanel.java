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
