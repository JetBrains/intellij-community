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
package com.intellij.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBComboBoxLabel extends JPanel {
  private final JLabel myIcon = new JLabel(PlatformIcons.COMBOBOX_ARROW_ICON);
  private final JLabel myText = new JLabel();

  public JBComboBoxLabel() {
    super(new BorderLayout());
    add(myText, BorderLayout.CENTER);
    add(myIcon, BorderLayout.EAST);
  }

  public void setTextFont(Font font) {
    myText.setFont(font);
  }

  public void setText(String text) {
    myText.setText(text);
  }

  public String getText() {
    return myText.getText();
  }

  public void setIcon(Icon icon) {
    myIcon.setIcon(icon);
  }

  public Icon getIcon() {
    return myIcon.getIcon();
  }

  public void setRegularIcon() {
    myIcon.setIcon(PlatformIcons.COMBOBOX_ARROW_ICON);
  }

  public void setSelectionIcon() {
    myIcon.setIcon(AllIcons.General.Combo);
  }

  @Override
  public void setForeground(Color color) {
    super.setForeground(color);
    if (myText != null) {
      myText.setForeground(color);
    }
  }
}
