/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ui;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author evgeny zakrevsky
 */

public class TitledSeparatorWithMnemonic extends TitledSeparator {
  private JComponent labelFor;
  private String originalText;

  public TitledSeparatorWithMnemonic() {
    super();
  }

  public TitledSeparatorWithMnemonic(String textWithMnemonic, @Nullable JComponent labelFor) {
    super();
    setText(textWithMnemonic);
    setLabelFor(labelFor);
  }

  public String getText() {
    return originalText;
  }

  public void setText(String text) {
    originalText = text;
    String textWithoutMnemonic = UIUtil.removeMnemonic(originalText);
    myLabel.setText(textWithoutMnemonic);
    final int index = originalText.indexOf('&');
    if (index != -1 && index != originalText.length() - 1 && index == originalText.lastIndexOf('&')) {
      char m = textWithoutMnemonic.charAt(index);
      myLabel.setDisplayedMnemonic(m);
    }
  }

  public JComponent getLabelFor() {
    return labelFor;
  }

  public void setLabelFor(JComponent labelFor) {
    this.labelFor = labelFor;
    myLabel.setLabelFor(labelFor);
  }
}
