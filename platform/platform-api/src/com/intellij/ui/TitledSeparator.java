/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class TitledSeparator extends JPanel {
  protected final JLabel myLabel;
  private boolean boldFont;
  private boolean smallFont;

  public TitledSeparator() {
    this("");
  }

  public TitledSeparator(String text) {
    this(text, false, true);
  }
  
  public TitledSeparator(String text, boolean boldFont, boolean smallFont) {
    setLayout(new GridBagLayout());
    this.myLabel = new JLabel();
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));
    Color oldColor = UIManager.getColor("Separator.foreground");
    UIManager.put("Separator.foreground", UIUtil.getBorderColor());
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    UIManager.put("Separator.foreground", oldColor);
    add(separator,
        new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               new Insets(3, 0, 0, 0), 0, 0));
    setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 5, 5));

    setText(text);
    setTitleFont(UIUtil.getBorderFont());
    setBoldFont(boldFont);
    setSmallFont(smallFont);
  }

  public String getText() {
    return myLabel.getText();
  }

  public void setText(String text) {
    myLabel.setText(text);
  }

  public void setTitleFont(Font font) {
    myLabel.setFont(font);
  }

  public Font getTitleFont() {
    return myLabel.getFont();
  }

  public boolean isBoldFont() {
    return boldFont;
  }

  public boolean isSmallFont() {
    return smallFont;
  }

  public void setBoldFont(boolean boldFont) {
    this.boldFont = boldFont;
    this.setTitleFont(this.getTitleFont().deriveFont(boldFont ? Font.BOLD : Font.PLAIN));
  }

  public void setSmallFont(boolean smallFont) {
    this.smallFont = smallFont;
    this.setTitleFont(UIUtil.getFont(smallFont ? UIUtil.FontSize.SMALL : UIUtil.FontSize.NORMAL, this.getTitleFont()));
  }

}
