// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.ComponentsKt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Function;

/**
 * @author gregsh
 */
class EnterPasswordComponent {
  private JPanel myRootPanel;
  protected JEditorPane myPromptLabel;

  protected JPanel myPasswordPanel;
  protected JPasswordField myPasswordField;
  protected JLabel myPasswordLabel;

  public JPanel getComponent() {
    return myRootPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }

  @NotNull
  private final Function<? super String, Boolean> myPasswordConsumer;

  EnterPasswordComponent(@NotNull Function<? super String, Boolean> passwordConsumer) {
    myPasswordConsumer = passwordConsumer;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myPasswordField.setText("pass");
    }
  }

  public ValidationInfo apply() {
    String password = new String(myPasswordField.getPassword());
    if (!myPasswordConsumer.apply(password)) {
      return new ValidationInfo("Password is incorrect", myPasswordField);
    }
    return null;
  }

  public String getHelpId() {
    return "settings_passwords_master_password_enter";
  }

  private void createUIComponents() {
    String note;
    if (MacOsKeychainLibraryKt.isMacOsCredentialStoreSupported()) {
      note = "The passwords will be stored in the system MacOS keychain.";
    }
    else if (SystemInfo.isLinux) {
      note = "The passwords will be stored in the system keychain using Secret Service API.";
    }
    else if (SystemInfo.isWindows) {
      note = "The passwords will be stored in the KeePass database protected by your Windows account.";
    }
    else {
      String subNote;
      if (SystemInfo.isMacIntel64) {
        subNote = "at least OS X 10.5 is required<br>to store in the system MacOS keychain";
      }
      else {
        subNote =
          "your OS is not supported, please <a href=\"https://youtrack.jetbrains.com/newIssue?project=IDEA&clearDraft=true&c=Assignee+develar&c=Subsystem+Password+Safe\">file issue</a>";
      }
      note = "The passwords will be stored in IDE configuration files with weak protection<br>(" + subNote + ").";
    }

    myPromptLabel = ComponentsKt
      .htmlComponent("Master password is required to convert saved passwords.<br>" + note, UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
  }
}
