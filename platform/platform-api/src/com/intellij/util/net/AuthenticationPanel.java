// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.UIBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Dimension;

import static java.util.Objects.requireNonNullElse;

@ApiStatus.Internal
public final class AuthenticationPanel extends JPanel {
  private final JTextField myLoginTextField;
  private final JPasswordField myPasswordTextField;
  private final JCheckBox rememberPasswordCheckBox;

  public AuthenticationPanel(
    @Nullable @NlsContexts.Label String description,
    @Nullable String login,
    @Nullable String password,
    @Nullable Boolean rememberPassword
  ) {
    setLayout(new GridLayoutManager(4, 2, JBUI.insets(2), -1, -1));

    var descriptionLabel = new JLabel();
    descriptionLabel.setText(description);
    add(descriptionLabel, new GridConstraints(
      0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false
    ));

    var loginLabel = new JLabel(UIBundle.message("auth.login.label"));
    add(loginLabel, new GridConstraints(
      1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false
    ));

    myLoginTextField = new JTextField();
    myLoginTextField.setText(login);
    add(myLoginTextField, new GridConstraints(
      1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false
    ));
    loginLabel.setLabelFor(myLoginTextField);

    var passwordLabel = new JLabel(UIBundle.message("auth.password.label"));
    add(passwordLabel, new GridConstraints(
      2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false
    ));

    myPasswordTextField = new JPasswordField();
    myPasswordTextField.setText(password);
    add(myPasswordTextField, new GridConstraints(
      2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false
    ));
    passwordLabel.setLabelFor(myPasswordTextField);

    rememberPasswordCheckBox = new JCheckBox(UIBundle.message("auth.remember.cb"));
    add(rememberPasswordCheckBox, new GridConstraints(
      3, 1, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false
    ));
    if (rememberPassword == null) {
      rememberPasswordCheckBox.setVisible(false);
    }
    else {
      rememberPasswordCheckBox.setSelected(rememberPassword);
    }
  }

  public @NotNull String getLogin() {
    return requireNonNullElse(myLoginTextField.getText(), "");
  }

  public char @NotNull [] getPassword() {
    return requireNonNullElse(myPasswordTextField.getPassword(), ArrayUtil.EMPTY_CHAR_ARRAY);
  }

  public boolean isRememberPassword() {
    return rememberPasswordCheckBox.isSelected();
  }

  public JComponent getPreferredFocusedComponent() {
    return getLogin().isEmpty() ? myLoginTextField : myPasswordTextField;
  }
}
