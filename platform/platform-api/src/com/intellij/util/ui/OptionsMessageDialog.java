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
package com.intellij.util.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class OptionsMessageDialog extends OptionsDialog{
  private final String myMessage;
  private final Icon myIcon;

  protected OptionsMessageDialog(Project project,
                                 final String message,
                                 String title,
                                 final Icon icon) {
    super(project);
    myMessage = message;
    myIcon = icon;
    setTitle(title);
    setButtonsAlignment(SwingUtilities.CENTER);
  }

  protected abstract String getOkActionName();
  protected abstract String getCancelActionName();

  @NotNull
  protected Action[] createActions() {
    final Action okAction = getOKAction();
    final Action cancelAction = getCancelAction();
    assignMnemonic(getOkActionName(), okAction);
    assignMnemonic(getCancelActionName(), cancelAction);
    return new Action[]{okAction,cancelAction};
  }

  protected static void assignMnemonic(String option, Action action) {
    action.putValue(Action.NAME, option);

    int mnemoPos = option.indexOf("&");
    if (mnemoPos >= 0 && mnemoPos < option.length() - 2) {
      String mnemoChar = option.substring(mnemoPos + 1, mnemoPos + 2).trim();
      if (mnemoChar.length() == 1) {
        action.putValue(Action.MNEMONIC_KEY, new Integer(mnemoChar.charAt(0)));
      }
    }
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 0));
    if (myIcon != null) {
      JLabel iconLabel = new JLabel(myIcon);
      Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(iconLabel, BorderLayout.NORTH);
      panel.add(container, BorderLayout.WEST);
    }

    if (myMessage != null) {
      JLabel textLabel = new JLabel(myMessage);
      textLabel.setUI(new MultiLineLabelUI());
      panel.add(textLabel, BorderLayout.CENTER);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

}
