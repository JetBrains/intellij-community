// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class OptionsMessageDialog extends OptionsDialog{
  private final @NlsContexts.Label String myMessage;
  private final Icon myIcon;

  protected OptionsMessageDialog(Project project,
                                 @NlsContexts.Label String message,
                                 @NlsContexts.DialogTitle String title,
                                 final Icon icon) {
    super(project);
    myMessage = message;
    myIcon = icon;
    setTitle(title);
  }

  @ActionText
  protected abstract String getOkActionName();
  @ActionText
  protected abstract String getCancelActionName();

  @Override
  protected Action @NotNull [] createActions() {
    final Action okAction = getOKAction();
    final Action cancelAction = getCancelAction();
    assignMnemonic(getOkActionName(), okAction);
    assignMnemonic(getCancelActionName(), cancelAction);
    return new Action[]{okAction,cancelAction};
  }

  protected static void assignMnemonic(@ActionText String option, Action action) {
    action.putValue(Action.NAME, option);

    int mnemoPos = option.indexOf("&");
    if (mnemoPos >= 0 && mnemoPos < option.length() - 2) {
      String mnemoChar = option.substring(mnemoPos + 1, mnemoPos + 2).trim();
      if (mnemoChar.length() == 1) {
        action.putValue(Action.MNEMONIC_KEY, new Integer(mnemoChar.charAt(0)));
      }
    }
  }

  @Override
  @NotNull
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

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

}
