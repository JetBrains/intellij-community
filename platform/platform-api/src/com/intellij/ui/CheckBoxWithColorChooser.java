// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public class CheckBoxWithColorChooser extends JPanel {
  private Color myColor;
  private final JCheckBox myCheckbox;

  public CheckBoxWithColorChooser(String text, boolean selected, Color color) {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    myColor = color;
    myCheckbox = new JCheckBox(text, selected);
    add(myCheckbox);
    add(new MyColorButton());
  }

  public CheckBoxWithColorChooser(String text, boolean selected) {
    this(text, selected, Color.WHITE);
  }

  public CheckBoxWithColorChooser(String text) {
    this(text, false);
  }

  public void setMnemonic(char c) {
    myCheckbox.setMnemonic(c);
  }

  public Color getColor() {
    return myColor;
  }

  public void setColor(Color color) {
    myColor = color;
  }

  public void setSelected(boolean selected) {
    myCheckbox.setSelected(selected);
  }


  public boolean isSelected() {
    return myCheckbox.isSelected();
  }

  private class MyColorButton extends JButton {
    MyColorButton() {
      setMargin(new Insets(0, 0, 0, 0));
      setDefaultCapable(false);
      setFocusable(false);
      if (SystemInfo.isMac) {
        putClientProperty("JButton.buttonType", "square");
      }

      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (myCheckbox.isSelected()) {
            final Color color = ColorChooser.chooseColor(myCheckbox, "Chose color", CheckBoxWithColorChooser.this.myColor);
            if (color != null) {
              myColor = color;
            }
          }
          return true;
        }
      }.installOn(this);
    }

    @Override
    public void paint(Graphics g) {
      final Color color = g.getColor();
      g.setColor(myColor);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(color);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(12, 12);
    }
  }
}
