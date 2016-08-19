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

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MasterPasswordDialog extends DialogWrapper {
  private final static int NUMBER_OF_RETRIES = 5;

  private final JPanel myRootPanel;
  private final PasswordComponentBase myComponent;
  private int myRetriesCount;

  public MasterPasswordDialog(@NotNull PasswordComponentBase component) {
    super(false);
    myComponent = component;

    setResizable(false);
    myRootPanel = component.getComponent();
    setTitle("Password Manager Database Updated");
    getOKAction().putValue(Action.NAME, "Convert");
    getCancelAction().putValue(Action.NAME, "Clear Passwords");
    init();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myComponent.getPreferredFocusedComponent();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return myComponent.getHelpId();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  public void doOKAction() {
    ValidationInfo info = myComponent.apply();
    if (info == null) {
      super.doOKAction();
    }
    else {
      setErrorText(info.message + " " + StringUtil.repeat(".", myRetriesCount));
      if (info.component != null) {
        info.component.requestFocus();
      }
      if (++myRetriesCount > NUMBER_OF_RETRIES) {
        super.doCancelAction();
      }
    }
  }
}
