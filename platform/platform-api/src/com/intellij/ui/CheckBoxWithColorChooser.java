/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
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
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (myCheckbox.isSelected()) {
            final Color color = ColorChooser.chooseColor(myCheckbox, "Chose color", CheckBoxWithColorChooser.this.myColor);
            if (color != null) {
              myColor = color;
            }
          }
        }
      });
    }

    @Override
    public void paint(Graphics g) {
      final Color color = g.getColor();
      g.setColor(myColor);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(color);
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    public Dimension getPreferredSize() {
      return new Dimension(12, 12);
    }
  }
}
