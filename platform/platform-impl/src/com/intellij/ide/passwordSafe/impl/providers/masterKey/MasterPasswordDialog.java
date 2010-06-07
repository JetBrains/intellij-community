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
import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.intellij.ide.passwordSafe.PasswordSafeException;

import javax.swing.*;

/**
 * The dialog used to prompt for the master password to the password database
 */
public class MasterPasswordDialog extends DialogWrapper {
  /**
   * The number of time the password is asked
   */
  private final static int NUMBER_OF_RETRIES = 3;
  /**
   * The user code for reset action
   */
  private final static int RESET_USER_CODE = NEXT_USER_EXIT_CODE;
  /**
   * The user code for change action
   */
  private final static int CHANGE_USER_CODE = NEXT_USER_EXIT_CODE + 1;
  /**
   * The master password
   */
  private JPasswordField myMasterPasswordPasswordField;
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * If selected, the password will be remembered and not asked again
   */
  private JCheckBox myEncryptMasterPasswordWithCheckBox;

  /**
   * The constructor
   *
   * @param project the current project
   * @param safe
   */
  protected MasterPasswordDialog(Project project, MasterKeyPasswordSafe safe) {
    super(project, false);
    setTitle("Master Password");
    if (!safe.isOsProtectedPasswordSupported()) {
      myEncryptMasterPasswordWithCheckBox.setVisible(false);
    }
    init();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
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
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new DialogWrapperExitAction("C&hange Password", CHANGE_USER_CODE),
      new DialogWrapperExitAction("&Reset Password", RESET_USER_CODE), getCancelAction()};
  }

  /**
   * Ask password from user and set it to password safe instance
   *
   * @param project the current project
   * @param safe    the password safe
   * @throws PasswordSafeException if the master password is not provided.
   */
  public static void askPassword(Project project, MasterKeyPasswordSafe safe) throws PasswordSafeException {
    String error = null;
    retries:
    for (int count = 0; count < NUMBER_OF_RETRIES; count++) {
      MasterPasswordDialog d = new MasterPasswordDialog(project, safe);
      if (error != null) {
        d.setErrorText(error);
      }
      d.show();
      switch (d.getExitCode()) {
        case OK_EXIT_CODE:
          boolean rc;
          String pw = new String(d.myMasterPasswordPasswordField.getPassword());
          if (d.myEncryptMasterPasswordWithCheckBox.isSelected()) {
            rc = safe.changeMasterPassword(pw, pw, true);
          }
          else {
            rc = safe.setMasterPassword(pw);
          }
          if (rc) {
            return;
          }
          else {
            error = "Invalid master password, please retry or reset.";
            continue retries;
          }
        case CANCEL_EXIT_CODE:
          throw new MasterPasswordUnavailableException("The master password request were cancelled.");
        case CHANGE_USER_CODE:
          if (!ChangeMasterKeyDialog.changePassword(project, safe)) {
            throw new MasterPasswordUnavailableException("The master password request were cancelled.");
          }
          return;
        case RESET_USER_CODE:
          if (!ResetPasswordDialog.resetPassword(project, safe)) {
            throw new MasterPasswordUnavailableException("The master password request were cancelled.");
          }
          return;
      }
    }
  }
}
