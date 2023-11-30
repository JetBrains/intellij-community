// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AuthenticationDialog extends DialogWrapper {
  private final AuthenticationPanel panel;

  public AuthenticationDialog(@NotNull Component component,
                              @NlsContexts.DialogTitle String title,
                              @NlsContexts.DialogMessage String description,
                              final String login,
                              final String password,
                              final boolean rememberPassword) {
    super(component, true);
    setTitle(title);

    MnemonicHelper.init(getContentPane());
    panel = new AuthenticationPanel(description, login, password, rememberPassword);

    final Window window = getWindow();
    if (window instanceof JDialog) {
      ((JDialog) window).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    init();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return panel.getPreferredFocusedComponent();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return panel;
  }

  public AuthenticationPanel getPanel() {
    return panel;
  }
}
