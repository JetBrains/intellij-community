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

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class TitledSeparator extends JPanel {
  public static int TOP_INSET = 7;
  public static int BOTTOM_INSET = 5;
  public static int SEPARATOR_LEFT_INSET = 6;
  public static int SEPARATOR_RIGHT_INSET = 3;

  protected final JBLabel myLabel = new JBLabel() {
    @Override
    public Font getFont() {
      return UIUtil.getTitledBorderFont();
    }
  };
  protected final JSeparator mySeparator = new JSeparator(SwingConstants.HORIZONTAL);
  private String originalText;

  public TitledSeparator() {
    this("");
  }

  public TitledSeparator(String text) {
    this(text, null);
  }

  public TitledSeparator(String text, @Nullable JComponent labelFor) {
    super();
    setLayout(new GridBagLayout());
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    add(mySeparator,
        new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               new Insets(2, SEPARATOR_LEFT_INSET, 0, SEPARATOR_RIGHT_INSET), 0, 0));
    setBorder(IdeBorderFactory.createEmptyBorder(TOP_INSET, 0, BOTTOM_INSET, 0));

    setText(text);
    setLabelFor(labelFor);
  }

  public String getText() {
    return originalText;
  }

  public void setText(String text) {
    originalText = text;
    myLabel.setText(UIUtil.replaceMnemonicAmpersand(originalText));
  }
  public void setTitleFont(Font font) {
    myLabel.setFont(font);
  }

  public Font getTitleFont() {
    return myLabel.getFont();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public JSeparator getSeparator() {
    return mySeparator;
  }


  public Component getLabelFor() {
    return myLabel.getLabelFor();
  }

  public void setLabelFor(Component labelFor) {
    myLabel.setLabelFor(labelFor);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myLabel.setEnabled(enabled);
    mySeparator.setEnabled(enabled);
  }
}
