/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;

public class FieldPanel extends AbstractFieldPanel {
  private final JTextField myTextField;

  public FieldPanel() {
    this(new JTextField(30));
  }

  protected FieldPanel(JTextField textField) {
    super(textField);
    myTextField = textField;
    createComponent();
  }

  public FieldPanel(String labelText, final String viewerDialogTitle, ActionListener browseButtonActionListener, final Runnable documentListener) {
    this(new JTextField(30), labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
  }

  public FieldPanel(JTextField textField, String labelText, final String viewerDialogTitle, ActionListener browseButtonActionListener, final Runnable documentListener) {
    super(textField, labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
    myTextField = textField;
    createComponent();
  }

  public void createComponent() {
    super.createComponent();
    TextFieldWithBrowseButton.MyDoClickAction doClickAction = getDoClickAction();
    if (doClickAction != null) {
      doClickAction.registerShortcut(getTextField());
    }

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        if (getChangeListener() != null) {
          getChangeListener().run();
        }
      }
    });
  }

  public String getText() {
    return myTextField.getText();
  }

  public void setText(String text) {
    myTextField.setText(text);
  }

  public JTextField getTextField() {
    return myTextField;
  }

  public static FieldPanel create(String labelText, String viewerDialogTitle) {
    return create(labelText, viewerDialogTitle, null, null);
  }

  public static FieldPanel withPaths(String labelText, String viewerDialogTitle) {
    return withPaths(labelText, viewerDialogTitle, null, null);
  }

  public static FieldPanel withPaths(String labelText, String viewerDialogTitle, ActionListener browseButtonActionListener, Runnable documentListener) {
    FieldPanel fieldPanel = create(labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
    InsertPathAction.addTo(fieldPanel.myTextField);
    return fieldPanel;
  }

  private static FieldPanel create(String labelText, String viewerDialogTitle, ActionListener browseButtonActionListener, Runnable documentListener) {
    return new FieldPanel(labelText, viewerDialogTitle, browseButtonActionListener, documentListener);
  }

  public void setEditable(boolean editable) {
    myTextField.setEditable(editable);
    for (int i = 0; i < myButtons.size(); i++) {
      JButton button = (JButton)myButtons.get(i);
      button.setEnabled(editable);
    }
  }
}
