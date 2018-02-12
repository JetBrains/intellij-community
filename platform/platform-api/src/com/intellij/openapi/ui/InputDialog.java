/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;

import static com.intellij.openapi.ui.Messages.CANCEL_BUTTON;
import static com.intellij.openapi.ui.Messages.OK_BUTTON;

public class InputDialog extends MessageDialog {
  protected JTextComponent myField;
  private final InputValidator myValidator;

  public InputDialog(@Nullable Project project,
                     String message,
                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                     @Nullable Icon icon,
                     @Nullable String initialValue,
                     @Nullable InputValidator validator,
                     @NotNull String[] options,
                     int defaultOption) {
    super(project, message, title, options, defaultOption, icon, true);
    myValidator = validator;
    myField.setText(initialValue);
    enableOkAction();
  }

  public InputDialog(@Nullable Project project,
                     String message,
                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                     @Nullable Icon icon,
                     @Nullable String initialValue,
                     @Nullable InputValidator validator) {
    this(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
  }

  public InputDialog(@NotNull Component parent,
                     String message,
                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                     @Nullable Icon icon,
                     @Nullable String initialValue,
                     @Nullable InputValidator validator) {
    super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
    myValidator = validator;
    myField.setText(initialValue);
    enableOkAction();
  }

  public InputDialog(String message,
                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                     @Nullable Icon icon,
                     @Nullable String initialValue,
                     @Nullable InputValidator validator) {
    super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
    myValidator = validator;
    myField.setText(initialValue);
    enableOkAction();
  }

  private void enableOkAction() {
    getOKAction().setEnabled(myValidator == null || myValidator.checkInput(myField.getText().trim()));
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    final Action[] actions = new Action[myOptions.length];
    for (int i = 0; i < myOptions.length; i++) {
      String option = myOptions[i];
      final int exitCode = i;
      if (i == 0) { // "OK" is default button. It has index 0.
        actions[i] = getOKAction();
        actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
        myField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          public void textChanged(DocumentEvent event) {
            final String text = myField.getText().trim();
            actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(text));
            if (myValidator instanceof InputValidatorEx) {
              setErrorText(((InputValidatorEx) myValidator).getErrorText(text), myField);
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
    if (myValidator == null || myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
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

    return panel;
  }

  protected JPanel createMessagePanel() {
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
    textComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    return textComponent;
  }

  public JTextComponent getTextField() {
    return myField;
  }

  protected JTextComponent createTextFieldComponent() {
    return new JTextField(30);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myField;
  }

  @Nullable
  public String getInputString() {
    if (getExitCode() == 0) {
      return myField.getText().trim();
    }
    return null;
  }
}
