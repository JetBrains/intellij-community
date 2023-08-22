// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.messages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class MessageDialog extends DialogWrapper {
  protected @NlsContexts.DialogMessage @Nullable String myMessage;
  protected String[] myOptions;
  protected int myDefaultOptionIndex;
  protected int myFocusedOptionIndex;
  protected Icon myIcon;
  private @NonNls @Nullable String myHelpId;

  public MessageDialog(@Nullable Project project,
                       @NlsContexts.DialogMessage @Nullable String message,
                       @NlsContexts.DialogTitle String title,
                       String @NotNull [] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon,
                       boolean canBeParent) {
    this(project, null, message, title, options, defaultOptionIndex, -1, icon, null, canBeParent);
  }


  public MessageDialog(@Nullable Project project,
                       @Nullable Component parentComponent,
                       @NlsContexts.DialogMessage @Nullable String message,
                       @NlsContexts.DialogTitle String title,
                       String @NotNull [] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       @Nullable com.intellij.openapi.ui.DoNotAskOption doNotAskOption,
                       boolean canBeParent) {
    this(project, parentComponent, message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption, canBeParent, null);
  }

  public MessageDialog(@Nullable Project project,
                       @Nullable Component parentComponent,
                       @NlsContexts.DialogMessage @Nullable String message,
                       @NlsContexts.DialogTitle String title,
                       String @NotNull [] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       @Nullable com.intellij.openapi.ui.DoNotAskOption doNotAskOption,
                       boolean canBeParent,
                       @Nullable String helpId) {
    super(project, parentComponent, canBeParent, IdeModalityType.IDE);
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption, helpId);
  }

  public MessageDialog(@NlsContexts.DialogMessage @Nullable String message,
                       @NlsContexts.DialogTitle String title,
                       String @NotNull [] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon) {
    this(null, null, message, title, options, defaultOptionIndex, -1, icon, null, false);
  }

  protected MessageDialog() {
    super(false);
  }

  protected MessageDialog(Project project) {
    super(project, false);
  }

  public MessageDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
  }

  protected void _init(@NlsContexts.DialogTitle String title,
                       @NlsContexts.DialogMessage @Nullable String message,
                       String @NotNull [] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       @Nullable com.intellij.openapi.ui.DoNotAskOption doNotAskOption,
                       @Nullable String helpId) {
    setTitle(title);
    myMessage = message;
    myOptions = options;
    myDefaultOptionIndex = defaultOptionIndex;
    myFocusedOptionIndex = focusedOptionIndex;
    myIcon = icon;
    myHelpId = helpId;
    setDoNotAskOption(doNotAskOption);
    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    for (int i = 0; i < myOptions.length; i++) {
      String option = myOptions[i];
      final int exitCode = i;
      Action action = new AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
        @Override
        public void actionPerformed(ActionEvent e) {
          close(exitCode, true);
        }
      };

      if (i == myDefaultOptionIndex) {
        action.putValue(DEFAULT_ACTION, Boolean.TRUE);
      }

      if (i == myFocusedOptionIndex) {
        action.putValue(FOCUSED_ACTION, Boolean.TRUE);
      }

      UIUtil.assignMnemonic(option, action);
      actions.add(action);
    }

    if (getHelpId() != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  @Override
  public void doCancelAction() {
    close(-1);
  }

  @Override
  protected JComponent createCenterPanel() {
    return doCreateCenterPanel();
  }

  protected JComponent doCreateCenterPanel() {
    JPanel panel = createIconPanel();
    if (myMessage != null) {
      JTextPane messageComponent = createMessageComponent(myMessage);
      panel.add(Messages.wrapToScrollPaneIfNeeded(messageComponent, 100, 15), BorderLayout.CENTER);
    }
    return panel;
  }

  @NotNull
  protected JPanel createIconPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 0));
    if (myIcon != null) {
      JLabel iconLabel = new JLabel(myIcon);
      Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(iconLabel, BorderLayout.NORTH);
      panel.add(container, BorderLayout.WEST);
    }
    return panel;
  }

  @NotNull
  protected JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JLabel textLabel = new JLabel(myMessage);
      textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      textLabel.setUI(new MultiLineLabelUI());
      messagePanel.add(textLabel, BorderLayout.NORTH);
    }
    return messagePanel;
  }

  protected JTextPane createMessageComponent(final @NlsContexts.DialogMessage String message) {
    final JTextPane messageComponent = new JTextPane();
    return Messages.configureMessagePaneUi(messageComponent, message);
  }

  @Override
  protected @Nullable String getHelpId() {
    return myHelpId;
  }
}
