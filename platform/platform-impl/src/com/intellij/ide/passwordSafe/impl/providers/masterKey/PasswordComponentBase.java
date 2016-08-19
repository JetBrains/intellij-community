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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 */
public abstract class PasswordComponentBase {
  private JPanel myRootPanel;
  private JLabel myIconLabel;
  protected JLabel myPromptLabel;

  protected JPanel myPasswordPanel;
  protected JPasswordField myPasswordField;
  protected JLabel myPasswordLabel;

  public PasswordComponentBase() {
    myIconLabel.setText("");
    myIconLabel.setIcon(AllIcons.General.PasswordLock);
    myIconLabel.setDisabledIcon(AllIcons.General.PasswordLock);
    //myPromptLabel.setUI(new MultiLineLabelUI());
    myPromptLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
  }

  public JPanel getComponent() {
    return myRootPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }

  @Nullable
  public abstract ValidationInfo apply();

  public String getHelpId() {
    return null;
  }
}
