// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.NlsContexts;

import javax.swing.*;

public class NonFocusableCheckBox extends JCheckBox {
  public NonFocusableCheckBox(@NlsContexts.Checkbox String text) {
    super(text);
    initFocusability();
  }

  public NonFocusableCheckBox() {
    initFocusability();
  }

  private void initFocusability() {
    // Or that won't be keyboard accessible at all
    if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred() ||
        !UISettings.getInstance().getDisableMnemonicsInControls()) {
      setFocusable(false);
    }
  }
}
