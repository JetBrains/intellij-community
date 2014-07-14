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

/**
 * @author gregsh
 */
public class ChangePasswordComponent extends PasswordComponentBase {


  public ChangePasswordComponent(MasterKeyPasswordSafe safe) {
    super(safe, "Change");
    myPromptLabel.setText("<html>Enter the current password and the new password<br>" +
                          "in order to change the master password.</html>");
  }

  @Override
  public String getHelpId() {
    return HelpID.CHANGE_PASSWORD;
  }

  @Override
  public boolean apply() {
    String o = new String(myPasswordField.getPassword());
    String n = new String(myNewPasswordField.getPassword());
    return mySafe.changeMasterPassword(o, n, myEncryptCheckBox.isSelected());
  }
}
