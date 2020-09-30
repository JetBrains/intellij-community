// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RefactoringMessageDialog extends DialogWrapper {
  private final @NlsContexts.DialogMessage String myMessage;
  private final String myHelpTopic;
  private final Icon myIcon;
  private final boolean myIsCancelButtonVisible;

  public RefactoringMessageDialog(@NlsContexts.DialogTitle String title, @NlsContexts.DialogMessage String message,
                                  @NonNls String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    super(project, false);
    setTitle(title);
    myMessage = message;
    myHelpTopic = helpTopic;
    myIsCancelButtonVisible = showCancelButton;
    myIcon = UIManager.getIcon(iconId);
    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    actions.add(getOKAction());
    if (myIsCancelButtonVisible) {
      actions.add(getCancelAction());
    }
    if (myHelpTopic != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  @Override
  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(myMessage);
    label.setUI(new MultiLineLabelUI());

    JPanel panel = new JPanel(new BorderLayout(10, 0));
    if (myIcon != null) {
      panel.add(new JLabel(myIcon), BorderLayout.WEST);
      panel.add(label, BorderLayout.CENTER);
    }
    else {
      panel.add(label, BorderLayout.WEST);
    }
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return myHelpTopic;
  }
}