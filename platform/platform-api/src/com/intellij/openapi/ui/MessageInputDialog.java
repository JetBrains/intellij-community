// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;

public class MessageInputDialog extends MessageDialog {
  public static final int INPUT_DIALOG_COLUMNS = 30;
  protected JTextComponent myField;
  private final InputValidator myValidator;
  private final @NlsContexts.DetailedDescription String myComment;

  public MessageInputDialog(@Nullable Project project,
                            @NlsContexts.DialogMessage String message,
                            @NlsContexts.DialogTitle String title,
                            @Nullable Icon icon,
                            @Nullable @NonNls String initialValue,
                            @Nullable InputValidator validator,
                            String @NotNull @NlsContexts.Button [] options,
                            int defaultOption,
                            @Nullable @NlsContexts.DetailedDescription String comment) {
    super(project, true);
    myComment = comment;
    myValidator = validator;
    _init(title, message, options, defaultOption, -1, icon, null, null);
    myField.setText(initialValue);
    enableOkAction();
  }

  public MessageInputDialog(@Nullable Project project,
                            @NlsContexts.DialogMessage String message,
                            @NlsContexts.DialogTitle String title,
                            @Nullable Icon icon,
                            @Nullable @NonNls String initialValue,
                            @Nullable InputValidator validator,
                            String @NotNull @NlsContexts.Button [] options,
                            int defaultOption) {
    this(project, message, title, icon, initialValue, validator, options, defaultOption, null);
  }

  public MessageInputDialog(@Nullable Project project,
                            @NlsContexts.DialogMessage String message,
                            @NlsContexts.DialogTitle String title,
                            @Nullable Icon icon,
                            @Nullable @NonNls String initialValue,
                            @Nullable InputValidator validator) {
    this(project, message, title, icon, initialValue, validator, new String[]{Messages.getOkButton(), Messages.getCancelButton()}, 0);
  }

  public MessageInputDialog(@NotNull Component parent,
                            @NlsContexts.DialogMessage String message,
                            @NlsContexts.DialogTitle String title,
                            @Nullable Icon icon,
                            @Nullable String initialValue,
                            @Nullable InputValidator validator) {
    super(null, parent, message, title, new String[]{Messages.getOkButton(), Messages.getCancelButton()}, -1, 0, icon, null, true);
    myValidator = validator;
    myComment = null;
    myField.setText(initialValue);
    enableOkAction();
  }

  public MessageInputDialog(@NlsContexts.DialogMessage String message,
                            @NlsContexts.DialogTitle String title,
                            @Nullable Icon icon,
                            @Nullable String initialValue,
                            @Nullable InputValidator validator) {
    super(null, null, message, title, new String[]{Messages.getOkButton(), Messages.getCancelButton()}, 0, -1, icon, null, true);
    myValidator = validator;
    myComment = null;
    myField.setText(initialValue);
    enableOkAction();
  }

  private void enableOkAction() {
    getOKAction().setEnabled(myValidator == null || myValidator.checkInput(myField.getText().trim()));
  }

  @Override
  protected Action @NotNull [] createActions() {
    final Action[] actions = new Action[myOptions.length];
    for (int i = 0; i < myOptions.length; i++) {
      String option = myOptions[i];
      final int exitCode = i;
      if (i == 0) { // "OK" is default button. It has index 0.
        actions[0] = getOKAction();
        actions[0].putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
        myField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          public void textChanged(@NotNull DocumentEvent event) {
            final String text = myField.getText().trim();
            actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(text));
            if (myValidator instanceof InputValidatorEx) {
              setErrorText(((InputValidatorEx)myValidator).getErrorText(text), myField);
            }
          }
        });
      }
      else {
        actions[i] = new AbstractAction(option) {
          @Override
          public void actionPerformed(ActionEvent e) {
            close(exitCode);
          }
        };
      }
    }
    return actions;
  }

  @Override
  protected void doOKAction() {
    String inputString = myField.getText().trim();
    if (myValidator == null ||
        myValidator.checkInput(inputString) &&
        myValidator.canClose(inputString)) {
      close(0);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = createIconPanel();

    JPanel messagePanel = createMessagePanel();
    panel.add(messagePanel, BorderLayout.CENTER);

    if (myComment != null) {
      return UI.PanelFactory.panel(panel).withComment(myComment).createPanel();
    }
    else {
      return panel;
    }
  }

  @Override
  protected @NotNull JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JComponent textComponent = createTextComponent();
      messagePanel.add(textComponent, BorderLayout.NORTH);
    }

    myField = createTextFieldComponent();
    messagePanel.add(createScrollableTextComponent(), BorderLayout.SOUTH);

    return messagePanel;
  }

  protected JComponent createScrollableTextComponent() {
    return myField;
  }

  protected JComponent createTextComponent() {
    JComponent textComponent;
    if (BasicHTML.isHTMLString(myMessage)) {
      textComponent = createMessageComponent(myMessage);
    }
    else {
      JLabel textLabel = new JLabel(myMessage);
      textLabel.setUI(new MultiLineLabelUI());
      textComponent = textLabel;
    }
    textComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 20));
    return textComponent;
  }

  public JTextComponent getTextField() {
    return myField;
  }

  protected JTextComponent createTextFieldComponent() {
    JTextField field = new JTextField(INPUT_DIALOG_COLUMNS);
    field.setMargin(JBInsets.create(0, 5));
    return field;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myField;
  }

  public @Nullable @NlsSafe String getInputString() {
    return getExitCode() == 0 ? myField.getText().trim() : null;
  }
}
