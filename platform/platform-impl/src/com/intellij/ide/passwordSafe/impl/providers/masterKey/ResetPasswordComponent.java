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
package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.ide.passwordSafe.HelpID;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;

/**
 * @author gregsh
 */
public class ResetPasswordComponent extends PasswordComponentBase {

  private final boolean myFirstTime;

  public ResetPasswordComponent(MasterKeyPasswordSafe safe, boolean firstTime) {
    super(safe, "Reset");
    myFirstTime = firstTime;
    UIUtil.setEnabled(myPasswordPanel, false, true);
    myPasswordPanel.setVisible(false);
    if (firstTime) {
      myPromptLabel.setText("<html><br>Specify the password for the password database.<br>" +
                            "Leave blank to disable the master password.</html>");
    }
    else {
      myPromptLabel.setText("<html><br>The password for the password database will be reset.<br>" +
                            "<b>All previously stored passwords will be removed!</b></html>");
    }
  }

  @Override
  public String getHelpId() {
    return HelpID.RESET_PASSWORD;
  }

  @Override
  public boolean apply() {
    if (myFirstTime ||
        Messages.showYesNoDialog((Project)null, "All stored passwords will be removed! Are you sure you want to proceed?",
                                 "Confirm Master Password Reset", Messages.getWarningIcon()) == Messages.YES) {
      mySafe.resetMasterPassword(new String(myNewPasswordField.getPassword()), myEncryptCheckBox.isSelected());
      return true;
    }
    return false;
  }
}
