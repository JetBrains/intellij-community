// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.util.PlatformIcons;
import org.intellij.lang.annotations.MagicConstant;

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
    //noinspection ConstantConditions
    if (myText != null) {
      // null when called from the constructor of the superclass
      myText.setForeground(color);
    }
  }

  public void setHorizontalAlignment(@MagicConstant(valuesFromClass = SwingConstants.class) int alignment) {
     myText.setHorizontalAlignment(alignment);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myText.setEnabled(enabled);
    myIcon.setEnabled(enabled);
  }
}
