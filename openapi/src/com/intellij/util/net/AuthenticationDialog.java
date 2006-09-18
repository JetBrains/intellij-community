/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 7, 2003
 * Time: 3:56:25 PM
 * To change this template use Options | File Templates.
 */
public class AuthenticationDialog extends DialogWrapper {
  private AuthenticationPanel panel;

  public AuthenticationDialog(String title, String description) {
    super(JOptionPane.getRootFrame(), true);
    setTitle(title);

    new MnemonicHelper().register(getContentPane());

    panel = new AuthenticationPanel(description,
                                    HttpConfigurable.getInstance().PROXY_LOGIN,
                                    HttpConfigurable.getInstance().getPlainProxyPassword(),
                                    HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD);

    final Window window = getWindow();
    if (window instanceof JDialog) {
      ((JDialog) window).setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return panel;
  }

  protected Action[] createActions() {
    Action [] actions =
      new Action [] {
        new AbstractAction (CommonBundle.getOkButtonText()) {
          public void actionPerformed(ActionEvent e) {
            HttpConfigurable.getInstance().PROXY_LOGIN = panel.getLogin();
            HttpConfigurable.getInstance().setPlainProxyPassword(panel.getPassword());
            HttpConfigurable.getInstance().PROXY_AUTHENTICATION = true;
            HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD = panel.isRememberPassword();

            dispose();
          }
        },
        new AbstractAction(CommonBundle.getCancelButtonText()) {
          public void actionPerformed(ActionEvent e) {
            HttpConfigurable.getInstance().PROXY_AUTHENTICATION = false;
            dispose();
          }
        }
      };
    actions [0].putValue(Action.DEFAULT, Boolean.TRUE.toString());
    return actions;
  }
}
