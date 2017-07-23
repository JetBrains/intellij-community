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
package com.intellij.ui.components;

import com.intellij.openapi.wm.IdeFocusManager;
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
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(getMainComponent(), true);
    });
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

