// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Component;

public final class AuthenticationDialog extends DialogWrapper {
  private final AuthenticationPanel myPanel;

  /// @param description      Description text above the text fields.
  /// @param rememberPassword Default value for the 'remember password' checkbox.
  ///                         If `true`, the checkbox will be selected; if `false`, the checkbox won't be selected;
  ///                         if `null`, there will be no checkbox for remembering password.
  public AuthenticationDialog(
    @NotNull Component component,
    @NotNull @NlsContexts.DialogTitle String title,
    @Nullable @NlsContexts.DialogMessage String description,
    @Nullable String login,
    @Nullable String password,
    @Nullable Boolean rememberPassword
  ) {
    super(component, false);
    setTitle(title);
    myPanel = new AuthenticationPanel(description, login, password, rememberPassword);
    init();
  }

  /// @see #AuthenticationDialog(Component, String, String, String, String, Boolean)
  public AuthenticationDialog(
    @NotNull Project project,
    @NotNull @NlsContexts.DialogTitle String title,
    @Nullable @NlsContexts.DialogMessage String description,
    @Nullable String login,
    @Nullable String password,
    @Nullable Boolean rememberPassword
  ) {
    super(project, false);
    setTitle(title);
    myPanel = new AuthenticationPanel(description, login, password, rememberPassword);
    init();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  public @NotNull String getLogin() {
    return myPanel.getLogin();
  }

  public char @NotNull [] getPassword() {
    return myPanel.getPassword();
  }

  public boolean isRememberPassword() {
    return myPanel.isRememberPassword();
  }
}
