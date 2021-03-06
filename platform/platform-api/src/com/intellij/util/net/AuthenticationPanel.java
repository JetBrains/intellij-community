// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
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
   * If true, the checkbox will be selected; if false, the checkbox won't be selected; if null, there will be no checkbox for remembering
   * password.
   */
  public AuthenticationPanel(@Nullable @NlsContexts.Label String description, @Nullable String login, @Nullable String password, @Nullable Boolean rememberPassword) {
    add(myMainPanel);
    myDescriptionLabel.setText(description);
    myLoginTextField.setText(login);
    myPasswordTextField.setText(password);
    if (rememberPassword == null) {
      rememberPasswordCheckBox.setVisible(false);
    } else {
      rememberPasswordCheckBox.setSelected(rememberPassword);
    }
  }

  @NotNull
  public String getLogin() {
    return StringUtil.notNullize(myLoginTextField.getText());
  }

  public char @NotNull [] getPassword() {
    return ObjectUtils.notNull(myPasswordTextField.getPassword(), ArrayUtilRt.EMPTY_CHAR_ARRAY);
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
