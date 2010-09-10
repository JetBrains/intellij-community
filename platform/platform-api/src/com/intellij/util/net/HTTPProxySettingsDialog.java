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

import com.intellij.CommonBundle;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 21, 2003
 * Time: 4:35:44 PM
 * To change this template use Options | File Templates.
 */
public class HTTPProxySettingsDialog extends DialogWrapper {
  private final HTTPProxySettingsPanel panel;
  private final Action okAction;
  private final Action cancelAction;

  public HTTPProxySettingsDialog() {
    super(false);
    setTitle(CommonBundle.message("title.http.proxy.settings"));
    panel = new HTTPProxySettingsPanel(HttpConfigurable.getInstance());
    panel.reset();

    okAction = new AbstractAction(CommonBundle.getOkButtonText()) {
      public void actionPerformed(ActionEvent e) {
        panel.apply();
        close(OK_EXIT_CODE);
      }
    };
    okAction.putValue(DEFAULT_ACTION, Boolean.TRUE.toString());
    cancelAction = new AbstractAction(CommonBundle.getCancelButtonText()) {
      public void actionPerformed(ActionEvent e) {
        close(CANCEL_EXIT_CODE);
      }
    };
    init();
  }

  protected JComponent createCenterPanel() {
    return panel.createComponent();
  }

  protected Action[] createActions() {
    return new Action[]{okAction, cancelAction};
  }
}
