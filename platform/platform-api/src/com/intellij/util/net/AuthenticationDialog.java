/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.net;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 7, 2003
 * Time: 3:56:25 PM
 * To change this template use Options | File Templates.
 */
public class AuthenticationDialog extends DialogWrapper {
  private final AuthenticationPanel panel;

  public AuthenticationDialog(@NotNull Component component, String title, String description, final String login, final String password, final boolean rememberPassword) {
    super(component, true);
    setTitle(title);

    new MnemonicHelper().register(getContentPane());
    panel = new AuthenticationPanel(description, login, password, rememberPassword);

    final Window window = getWindow();
    if (window instanceof JDialog) {
      ((JDialog) window).setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    init();
  }

  public AuthenticationDialog(String title, String description, final String login, final String password, final boolean rememberPassword) {
    super(JOptionPane.getRootFrame(), true);
    setTitle(title);

    new MnemonicHelper().register(getContentPane());
    panel = new AuthenticationPanel(description, login, password, rememberPassword);

    final Window window = getWindow();
    if (window instanceof JDialog) {
      ((JDialog) window).setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    init();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return panel.getPreferredFocusedComponent();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return panel;
  }

  public AuthenticationPanel getPanel() {
    return panel;
  }
}
