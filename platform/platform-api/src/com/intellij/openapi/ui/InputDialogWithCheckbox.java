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

import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class InputDialogWithCheckbox extends InputDialog {
  private JCheckBox myCheckBox;

  public InputDialogWithCheckbox(String message,
                                 @Nls(capitalization = Nls.Capitalization.Title) String title,
                                 String checkboxText,
                                 boolean checked,
                                 boolean checkboxEnabled,
                                 @Nullable Icon icon,
                                 @Nullable String initialValue,
                                 @Nullable InputValidator validator) {
    super(message, title, icon, initialValue, validator);
    myCheckBox.setText(checkboxText);
    myCheckBox.setSelected(checked);
    myCheckBox.setEnabled(checkboxEnabled);
  }

  @Override
  protected JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JComponent textComponent = createTextComponent();
      messagePanel.add(textComponent, BorderLayout.NORTH);
    }

    myField = createTextFieldComponent();
    messagePanel.add(createScrollableTextComponent(), BorderLayout.CENTER);

    myCheckBox = new JCheckBox();
    messagePanel.add(myCheckBox, BorderLayout.SOUTH);

    return messagePanel;
  }

  public Boolean isChecked() {
    return myCheckBox.isSelected();
  }
}
