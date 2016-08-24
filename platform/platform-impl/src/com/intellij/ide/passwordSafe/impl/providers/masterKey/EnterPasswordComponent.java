/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author gregsh
 */
public class EnterPasswordComponent extends PasswordComponentBase {
  @NotNull
  private final Function<String, Boolean> myPasswordConsumer;

  public EnterPasswordComponent(@NotNull Function<String, Boolean> passwordConsumer) {
    myPasswordConsumer = passwordConsumer;

    String note;
    if (SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard) {
      note = "The passwords will be stored in the system MacOS keychain.";
    }
    else if (SystemInfo.isLinux) {
      note = "The passwords will be stored in the system keychain using Secret Service API.";
    }
    else if (SystemInfo.isWindows) {
      note = "The passwords will be stored in the KeePass database protected by your Windows account.";
    }
    else {
      String subNote = SystemInfo.isMacIntel64 ? "at least OS X 10.5 is required" : "your OS is not supported, please file issue";
      note = "The passwords will be stored in IDE configuration files with weak protection (" + subNote + ").";
    }

    myPromptLabel.setText("<html>Master password is required to convert saved passwords.<br><br>" + note + "</html>");

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myPasswordField.setText("pass");
    }
  }

  @Override
  public ValidationInfo apply() {
    // enter password — only and only old key, so, we use EncryptionUtil.genPasswordKey
    String password = new String(myPasswordField.getPassword());
    if (!myPasswordConsumer.apply(password)) {
      return new ValidationInfo("Password is incorrect", myPasswordField);
    }
    return null;
  }

  @Override
  public String getHelpId() {
    return "settings_passwords_master_password_enter";
  }
}
