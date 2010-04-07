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
 * This dialog is used to change master password
 */
public class ChangeMasterKeyDialog extends DialogWrapper {
  /**
   * The old password
   */
  private JPasswordField myOldPasswordPasswordField;
  /**
   * The new password
   */
  private JPasswordField myNewPasswordPasswordField;
  /**
   * The field used to confirm new password
   */
  private JPasswordField myConfirmNewPasswordPasswordField;
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * Encrypt master password with user credentials
   */
  private JCheckBox myEncryptMasterPasswordWithCheckBox;
  /**
   * The constant for reset password action
   */
  private static final int RESET_PASSWORD_CODE = NEXT_USER_EXIT_CODE;

  /**
   * Change master password
   *
   * @param project           the project to use for this dialog
   * @param safe              the master safe to modify
   * @param passwordEncrypted if the password was encrypted in the database
   * @param error             if not null, show as error
   */
  protected ChangeMasterKeyDialog(Project project, MasterKeyPasswordSafe safe, boolean passwordEncrypted, String error) {
    super(project, true);
    setTitle("Change Master Password");
    setOKButtonText("Change Password");
    if (!safe.isOsProtectedPasswordSupported()) {
      myEncryptMasterPasswordWithCheckBox.setSelected(false);
      myEncryptMasterPasswordWithCheckBox.setVisible(false);
    }
    else {
      myEncryptMasterPasswordWithCheckBox.setSelected(passwordEncrypted);
    }
    MasterKeyUtils.matchPasswords(myConfirmNewPasswordPasswordField, myNewPasswordPasswordField, new Processor<String>() {
      public boolean process(String s) {
        setErrorText(s);
        setOKActionEnabled(s == null);
        return false;
      }
    });
    setErrorText(error);
    init();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new DialogWrapperExitAction("Reset Password", RESET_PASSWORD_CODE), getCancelAction()};
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
   * This method is called to show a dialog that allows changing a master password.
   * The method should be called from the event dispatch thread.
   *
   * @param project the context project
   * @param safe    the password safe provider
   * @return true, if the password was changed or reset
   */
  public static boolean changePassword(Project project, MasterKeyPasswordSafe safe) {
    String error = null;
    boolean encrypt = safe.isPasswordEncrypted();
    while (true) {
      ChangeMasterKeyDialog d = new ChangeMasterKeyDialog(project, safe, encrypt, error);
      d.show();
      encrypt = d.myEncryptMasterPasswordWithCheckBox.isSelected();
      switch (d.getExitCode()) {
        case OK_EXIT_CODE:
          String o = new String(d.myOldPasswordPasswordField.getPassword());
          String n = new String(d.myNewPasswordPasswordField.getPassword());
          if (safe.changeMasterPassword(o, n, encrypt)) {
            return true;
          }
          else {
            error = "Invalid old password was specified, please retry";
          }
          break;
        case CANCEL_EXIT_CODE:
          return false;
        case RESET_PASSWORD_CODE:
          return ResetPasswordDialog.resetPassword(project, safe);
      }
    }
  }
}
