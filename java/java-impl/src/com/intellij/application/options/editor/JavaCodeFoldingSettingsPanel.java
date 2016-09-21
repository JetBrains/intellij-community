/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.editor;

import javax.swing.*;

public class JavaCodeFoldingSettingsPanel {
  private JTextField minNameLengthThresholdText;
  private JTextField minArgumentsToFoldText;
  private JPanel row0;
  private JPanel row1;
  private JPanel row2;

  int getMinNameLengthThresholdText() {
    try {
      return Integer.parseInt(minNameLengthThresholdText.getText());
    }
    catch (NumberFormatException e) {
      return 3;
    }
  }

  void setMinNameLengthThresholdText(int value) {
    minNameLengthThresholdText.setText(String.valueOf(value));
  }

  int getMinArgumentsToFoldText() {
    try {
      return Integer.parseInt(minArgumentsToFoldText.getText());
    }
    catch (NumberFormatException e) {
      return 2;
    }
  }

  void setMinArgumentsToFoldText(int value) {
    minArgumentsToFoldText.setText(String.valueOf(value));
  }

  JComponent getRow0() {
    return row0;
  }

  JPanel getRow1() {
    return row1;
  }

  JPanel getRow2() {
    return row2;
  }
}
