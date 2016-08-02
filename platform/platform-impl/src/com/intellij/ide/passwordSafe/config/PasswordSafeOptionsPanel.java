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

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class PasswordSafeOptionsPanel {
  private JRadioButton myRememberPasswordsUntilClosingRadioButton;
  private JRadioButton mySaveOnDiskRadioButton;

  private JPanel myRoot;

  public void reset(@NotNull PasswordSafeSettings settings) {
    PasswordSafeSettings.ProviderType t = settings.getProviderType();
    switch (t) {
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

  @NotNull
  private PasswordSafeSettings.ProviderType getProviderType() {
    if (myRememberPasswordsUntilClosingRadioButton.isSelected()) {
      return PasswordSafeSettings.ProviderType.MEMORY_ONLY;
    }
    else {
      return PasswordSafeSettings.ProviderType.MASTER_PASSWORD;
    }
  }

  public boolean isModified(PasswordSafeSettings settings) {
    return getProviderType() != settings.getProviderType();
  }

  public void apply(PasswordSafeSettings settings) {
    settings.setProviderType(getProviderType());
  }

  public JComponent getRoot() {
    return myRoot;
  }
}
