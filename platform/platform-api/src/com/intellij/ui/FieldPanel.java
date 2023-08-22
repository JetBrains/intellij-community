// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.fields.ExtendableTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;

public class FieldPanel extends AbstractFieldPanel implements TextAccessor {
  private final JTextField myTextField;

  public FieldPanel() {
    this(new ExtendableTextField(30));
  }

  protected FieldPanel(JTextField textField) {
    super(textField);
    myTextField = textField;
    createComponent();
  }

  public FieldPanel(@NlsContexts.Label String labelText, final @NlsContexts.DialogTitle String viewerDialogTitle, ActionListener browseButtonActionListener, final Runnable documentListener) {
    this(new ExtendableTextField(30), labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
  }

  public FieldPanel(JTextField textField, @NlsContexts.Label String labelText, final @NlsContexts.DialogTitle String viewerDialogTitle, ActionListener browseButtonActionListener, final Runnable documentListener) {
    super(textField, labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
    myTextField = textField;
    createComponent();
  }

  @Override
  public void createComponent() {
    super.createComponent();
    TextFieldWithBrowseButton.MyDoClickAction doClickAction = getDoClickAction();
    if (doClickAction != null) {
      doClickAction.registerShortcut(getTextField());
    }

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        if (getChangeListener() != null) {
          getChangeListener().run();
        }
      }
    });
  }

  @Override
  public String getText() {
    return myTextField.getText();
  }

  @Override
  public void setText(String text) {
    myTextField.setText(text);
  }

  public JTextField getTextField() {
    return myTextField;
  }

  public static FieldPanel create(@NlsContexts.Label String labelText, @NlsContexts.DialogTitle String viewerDialogTitle) {
    return create(labelText, viewerDialogTitle, null, null);
  }

  public static FieldPanel withPaths(@NlsContexts.Label String labelText, @NlsContexts.DialogTitle String viewerDialogTitle) {
    return withPaths(labelText, viewerDialogTitle, null, null);
  }

  public static FieldPanel withPaths(@NlsContexts.Label String labelText, @NlsContexts.DialogTitle String viewerDialogTitle, ActionListener browseButtonActionListener, Runnable documentListener) {
    FieldPanel fieldPanel = create(labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
    InsertPathAction.addTo(fieldPanel.myTextField);
    return fieldPanel;
  }

  private static FieldPanel create(@NlsContexts.Label String labelText, @NlsContexts.DialogTitle String viewerDialogTitle, ActionListener browseButtonActionListener, Runnable documentListener) {
    return new FieldPanel(labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
  }

  public void setEditable(boolean editable) {
    myTextField.setEditable(editable);
    for (JButton button : myButtons) {
      button.setEnabled(editable);
    }
  }
}
