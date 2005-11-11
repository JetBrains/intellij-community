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
public class ValidatingTextField extends ValidatingComponent<JTextField> {
  private final JTextField myTextField;

  public ValidatingTextField() {
    this(new JTextField(25));
  }

  public ValidatingTextField(final JTextField textField) {
    myTextField = textField;
    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        String errorText = validateTextOnChange(getMainComponent().getText(), e);
        if (errorText != null) {
          setErrorText(errorText);
        }
      }
    });
    doInitialize();
  }

  protected JTextField createMainComponent() {
    return myTextField;
  }

  public void requestFocus() {
    getMainComponent().requestFocus();
  }

  public boolean requestFocusInWindow() {
    return getMainComponent().requestFocusInWindow();
  }

  /**
   * to be overriden
   */
  protected String validateTextOnChange(String text, DocumentEvent e) {
    return null;
  }

  public String getText() {
    return getMainComponent().getText();
  }

  public void setText(String text) {
    getMainComponent().setText(text);
  }

}

