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
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public class EnterPasswordComponent extends PasswordComponentBase {

  public EnterPasswordComponent(@NotNull MasterKeyPasswordSafe safe, @NotNull Class<?> requestor) {
    super(safe, "Enter");
    String requestorName = getRequestorTitle(requestor);
    myPromptLabel.setText("<html><br>Master password is required to unlock the password database.<br>" +
                          "The password database will be unlocked during this session<br>" +
                          "for all subsystems.<br>" +
                          "<br><b>Requested by</b>: " + requestorName + "</html>");
    UIUtil.setEnabled(myNewPasswordPanel, false, true);
    myNewPasswordPanel.setVisible(false);
  }

  @Override
  public ValidationInfo doValidate() {
    return null;
  }

  @Override
  public boolean apply() {
    String password = new String(myPasswordField.getPassword());
    return mySafe.changeMasterPassword(password, password, myEncryptCheckBox.isSelected());
  }

  @Override
  public String getHelpId() {
    return HelpID.ENTER_PASSWORD;
  }
}
