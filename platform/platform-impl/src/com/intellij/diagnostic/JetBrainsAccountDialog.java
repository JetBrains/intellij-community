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
package com.intellij.diagnostic;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClickListener;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class JetBrainsAccountDialog extends DialogWrapper {
  private JTextField myItnLoginTextField;
  private JPasswordField myPasswordText;
  private JCheckBox myRememberCheckBox;

  public JetBrainsAccountDialog(Component parent) throws HeadlessException {
    super(parent, false);
    init();
  }

  public JetBrainsAccountDialog(Project project) throws HeadlessException {
    super(project, false);
    init();
  }

  protected JPanel myMainPanel;
  protected JLabel mySendingSettingsLabel;
  private JLabel myCreateAccountLabel;

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.diagnostic.AbstractSendErrorDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myItnLoginTextField;
  }

  @Override
  protected void init() {
    setTitle(ReportMessages.ERROR_REPORT);
    getContentPane().add(myMainPanel);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        HttpConfigurable.editConfigurable(myMainPanel);
        return true;
      }
    }.installOn(mySendingSettingsLabel);

    mySendingSettingsLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

    Credentials credentials = ErrorReportConfigurable.getCredentials();
    String userName = credentials == null ? null : credentials.getUserName();
    myItnLoginTextField.setText(userName);
    String password = credentials == null ? null : credentials.getPasswordAsString();
    myPasswordText.setText(password);
    // if no user name - never stored and so, defaults to remember. if user name set, but no password, so, previously was stored without password
    myRememberCheckBox.setSelected(StringUtil.isEmpty(userName) || !StringUtil.isEmpty(password));

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        BrowserUtil.browse("http://account.jetbrains.com");
        return true;
      }
    }.installOn(myCreateAccountLabel);
    myCreateAccountLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

    super.init();
  }

  @Override
  protected void doOKAction() {
    String userName = myItnLoginTextField.getText();
    if (!StringUtil.isEmpty(userName)) {
      PasswordSafe.getInstance().set(new CredentialAttributes(ErrorReportConfigurable.SERVICE_NAME, userName),
                                     new Credentials(userName, myRememberCheckBox.isSelected() ? myPasswordText.getPassword() : null));
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
