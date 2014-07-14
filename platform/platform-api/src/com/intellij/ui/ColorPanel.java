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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ColorPanel extends JComponent {
  private static final Dimension SIZE = new Dimension(25, 15);

  private boolean isFiringEvent = false;
  private boolean isEditable = true;
  private final List<ActionListener> myListeners = new CopyOnWriteArrayList<ActionListener>();
  @Nullable private Color myColor = null;

  public ColorPanel() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (!isEnabled() || !isEditable) return;
        Color color = ColorChooser.chooseColor(ColorPanel.this, UIBundle.message("color.panel.select.color.dialog.description"), myColor);
        if (color != null) {
          setSelectedColor(color);
          fireActionEvent();
        }
      }
    });
  }

  @Override
  public void paint(Graphics g) {
    Graphics2D g2d = (Graphics2D)g.create();
    try {
      if (myColor != null && isEnabled()) {
        g2d.setColor(myColor);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setColor(ColorUtil.darker(myColor, 2));
        g2d.draw(new Rectangle2D.Double(0.5, 0.5, getWidth()-1, getHeight()-1));
      }
      g2d.setColor(UIUtil.getBorderColor());
      g2d.draw(new Rectangle2D.Double(1.5, 1.5, getWidth() - 3, getHeight() - 3));
    } finally {
      g2d.dispose();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return SIZE;
  }

  @Override
  public Dimension getMaximumSize() {
    return SIZE;
  }

  @Override
  public Dimension getMinimumSize() {
    return SIZE;
  }

  @Override
  public String getToolTipText() {
    if (myColor == null || !isEnabled()) {
      return null;
    }
    StringBuilder buffer = new StringBuilder("0x").append(ColorUtil.toHex(myColor).toUpperCase());
    if (isEnabled() && isEditable) {
      buffer.append(" (Click to customize)");
    }
    return buffer.toString();
  }

  private void fireActionEvent() {
    if (!isEditable) return;
    if (!isFiringEvent) {
      try {
        isFiringEvent = true;
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "colorPanelChanged");
        for (ActionListener listener : myListeners) {
          listener.actionPerformed(event);
        }
      }
      finally {
        isFiringEvent = false;
      }
    }
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

  public void setEditable(boolean isEditable) {
    this.isEditable = isEditable;
  }
}
