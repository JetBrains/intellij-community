/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.panels.ValidatingComponent;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * @author kir

 * A textField with error label under it.
 */
public class ValidatingTextField extends ValidatingComponent {
  private final JTextField myTextField;

  public ValidatingTextField() {
    this(new JTextField(25));
  }

  public ValidatingTextField(final JTextField textField) {
    myTextField = textField;
    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        String errorText = validateTextOnChange(getTextField().getText(), e);
        if (errorText != null) {
          setErrorText(errorText);
        }
      }
    });
  }

  protected JComponent createMainComponent() {
    return myTextField;
  }

  public void requestFocus() {
    getTextField().requestFocus();
  }

  public boolean requestFocusInWindow() {
    return getTextField().requestFocusInWindow();
  }

  /**
   * to be overriden
   */
  protected String validateTextOnChange(String text, DocumentEvent e) {
    return null;
  }

  public JTextField getTextField() {
    return myTextField;
  }

  public String getText() {
    return getTextField().getText();
  }

  public void setText(String text) {
    getTextField().setText(text);
  }

}

