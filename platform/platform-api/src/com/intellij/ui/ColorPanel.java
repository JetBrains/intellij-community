/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.beans.EventHandler.create;

public class ColorPanel extends JComponent {
  private final List<ActionListener> myListeners = new CopyOnWriteArrayList<ActionListener>();
  private boolean myEditable;
  private ActionEvent myEvent;
  private Color myColor;

  public ColorPanel() {
    setEditable(true);
    setMinimumSize(JBUI.size(10, 10));
    addMouseListener(create(MouseListener.class, this, "onPressed", null, "mousePressed"));
    addKeyListener(create(KeyListener.class, this, "onPressed", "keyCode", "keyPressed"));
    addFocusListener(create(FocusListener.class, this, "repaint"));
  }

  public void onPressed(int keyCode) {
    if (keyCode == KeyEvent.VK_SPACE) {
      onPressed();
    }
  }

  public void onPressed() {
    if (myEditable && isEnabled()) {
      Color color = ColorChooser.chooseColor(this, UIBundle.message("color.panel.select.color.dialog.description"), myColor);
      if (color != null) {
        setSelectedColor(color);
        if (!myListeners.isEmpty() && (myEvent == null)) {
          try {
            myEvent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "colorPanelChanged");
            for (ActionListener listener : myListeners) {
              listener.actionPerformed(myEvent);
            }
          }
          finally {
            myEvent = null;
          }
        }
      }
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(hasFocus() ? JBColor.BLACK : JBColor.border());
    g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    if (myColor != null && isEnabled()) {
      g.setColor(myColor);
      g.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Font font = getFont();
    if (font != null) {
      int size = font.getSize();
      if (size > 6) {
        return new Dimension(4 + 2 * size, 4 + size);
      }
    }
    return getMinimumSize();
  }

  @Override
  public String getToolTipText() {
    if (myColor == null || !isEnabled()) {
      return null;
    }
    StringBuilder buffer = new StringBuilder("0x").append(ColorUtil.toHex(myColor).toUpperCase());
    if (myEditable && isEnabled()) {
      buffer.append(" (Click to customize)");
    }
    return buffer.toString();
  }

  public void removeActionListener(ActionListener actionlistener) {
    myListeners.remove(actionlistener);
  }

  public void addActionListener(ActionListener actionlistener) {
    myListeners.add(actionlistener);
  }

  @Nullable
  public Color getSelectedColor() {
    return myColor;
  }

  public void setSelectedColor(@Nullable Color color) {
    myColor = color;
    repaint();
  }

  public void setEditable(boolean editable) {
    myEditable = editable;
    setFocusable(editable);
    repaint();
  }
}
