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
package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Processor;

import javax.swing.*;

/**
 * The dialog that allows resetting the password
 */
public class ResetPasswordDialog extends DialogWrapper {
  /**
   * The new password
   */
  private JPasswordField myNewPasswordPasswordField;
  /**
   * The confirmed new password
   */
  private JPasswordField myConfirmNewPasswordPasswordField;
  /**
   * The panel
   */
  private JPanel myPanel;
  /**
   * The prompt text
   */
  private JLabel myPrompt;
  /**
   * The checkbox that specifies if the master password should be encrypted with OS-specific mechanism
   */
  private JCheckBox myEncryptMasterPasswordWithCheckBox;

  /**
   * The constructor
   *
   * @param project   the project instance
   * @param safe
   * @param firstTime true, if the password is specified for the first time
   */
  protected ResetPasswordDialog(Project project, MasterKeyPasswordSafe safe, boolean firstTime) {
    super(project, true);
    setTitle(firstTime ? "Master Password" : "Reset Master Password");
    setOKButtonText(firstTime ? "Set Password" : "Reset Password");
    if (safe.isOsProtectedPasswordSupported()) {
      myEncryptMasterPasswordWithCheckBox.setSelected(false);
    }
    else {
      myEncryptMasterPasswordWithCheckBox.setSelected(false);
      myEncryptMasterPasswordWithCheckBox.setVisible(false);
    }
    if (firstTime) {
      myPrompt.setText("Specify Master Password for the password database.");
    }
    MasterKeyUtils.matchPasswords(myConfirmNewPasswordPasswordField, myNewPasswordPasswordField, new Processor<String>() {
      public boolean process(String s) {
        setErrorText(s);
        setOKActionEnabled(s == null);
        return false;
      }
    });
    init();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * Ask new password for the password safe
   *
   * @param project the project where password is asked
   * @param safe    the provider instance to modify
   * @return true if the operation was not cancelled
   */
  public static boolean newPassword(Project project, MasterKeyPasswordSafe safe) {
    return resetPassword(project, safe, true);
  }

  /**
   * Reset password
   *
   * @param project the project
   * @param safe    the safe
   * @return true if the operation was not cancelled
   */
  public static boolean resetPassword(Project project, MasterKeyPasswordSafe safe) {
    return resetPassword(project, safe, false);
  }

  /**
   * Ask new password for the password safe or reset password
   *
   * @param project   the project where password is asked
   * @param safe      the provider instance to modify
   * @param firstTime true, if reset is called for the first time
   * @return true if the operation was not cancelled
   */
  private static boolean resetPassword(Project project, MasterKeyPasswordSafe safe, final boolean firstTime) {
    ResetPasswordDialog d = new ResetPasswordDialog(project, safe, firstTime);
    d.show();
    if (d.isOK()) {
      safe.resetMasterPassword(new String(d.myNewPasswordPasswordField.getPassword()), d.myEncryptMasterPasswordWithCheckBox.isSelected());
      return true;
    }
    return false;
  }
}
