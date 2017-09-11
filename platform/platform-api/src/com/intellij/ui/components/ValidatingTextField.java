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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.DocumentEvent;

/**
 * A text field with validation and error notification.
 */
public class ValidatingTextField extends JBTextField {

  public ValidatingTextField() {
    getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        String errorText = validateTextOnChange(getText(), e);
        highlightState(StringUtil.isEmpty(errorText));
        if (StringUtil.isNotEmpty(errorText)) {
          setErrorText(errorText);
        }
      }
    });
  }

  private void highlightState(boolean isValid) {
    putClientProperty("JComponent.error.outline", isValid ? null : true);
  }

  /**
   * @return Error text if there are errors, empty text or null otherwise.
   */
  protected String validateTextOnChange(String text, DocumentEvent e) {
    return null;
  }

  protected void setErrorText(@NotNull String errorText) {
    // TODO: to be implemented later
  }

}

