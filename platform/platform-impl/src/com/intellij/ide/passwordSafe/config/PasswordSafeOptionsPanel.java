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
package com.intellij.ide.passwordSafe.config;

import javax.swing.*;

/**
 * The option panel for password safe
 */
public class PasswordSafeOptionsPanel {
  /**
   * The password storage policy option
   */
  private JRadioButton myDoNotRememberPasswordsRadioButton;
  /**
   * The password storage policy option
   */
  private JRadioButton myRememberPasswordsUntilClosingRadioButton;
  /**
   * The password storage policy option
   */
  private JRadioButton mySaveOnDiskRadioButton;

  /**
   * The root panel
   */
  private JPanel myRoot;

  public PasswordSafeOptionsPanel() {
  }

  public void reset(PasswordSafeSettings settings) {
    PasswordSafeSettings.ProviderType t = settings.getProviderType();
    switch (t) {
      case DO_NOT_STORE:
        myDoNotRememberPasswordsRadioButton.setSelected(true);
        break;
      case MEMORY_ONLY:
        myRememberPasswordsUntilClosingRadioButton.setSelected(true);
        break;
      case MASTER_PASSWORD:
        mySaveOnDiskRadioButton.setSelected(true);
        break;
      default:
        throw new IllegalStateException("Unknown provider type: " + t);
    }
  }

  private PasswordSafeSettings.ProviderType getProviderType() {
    if (myDoNotRememberPasswordsRadioButton.isSelected()) {
      return PasswordSafeSettings.ProviderType.DO_NOT_STORE;
    }
    if (myRememberPasswordsUntilClosingRadioButton.isSelected()) {
      return PasswordSafeSettings.ProviderType.MEMORY_ONLY;
    }
    return PasswordSafeSettings.ProviderType.MASTER_PASSWORD;
  }

  /**
   * Check if the option panel modified the settings
   *
   * @param settings the settings to compare with
   * @return true, if values were modified
   */
  public boolean isModified(PasswordSafeSettings settings) {
    return getProviderType() != settings.getProviderType();
  }

  /**
   * Save UI state to the settings
   *
   * @param settings the settings to use
   */
  public void apply(PasswordSafeSettings settings) {
    settings.setProviderType(getProviderType());
  }

  /**
   * @return the root panel
   */
  public JComponent getRoot() {
    return myRoot;
  }
}
