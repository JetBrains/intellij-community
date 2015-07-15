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
package com.intellij.find;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.InplaceActionButtonLook;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

class SearchWrapper extends NonOpaquePanel implements PropertyChangeListener, FocusListener {
  private final JTextComponent myToWrap;

  public SearchWrapper(JTextComponent toWrap, AnAction showHistoryAction) {
    myToWrap = toWrap;
    setBorder(JBUI.Borders.empty(6, 6, 6, 8));
    setLayout(new BorderLayout(JBUI.scale(4), 0));
    myToWrap.addPropertyChangeListener("background", this);
    myToWrap.addFocusListener(this);
    myToWrap.setBorder(null);
    myToWrap.setOpaque(false);
    JBScrollPane scrollPane = new JBScrollPane(myToWrap,
                                               ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.getVerticalScrollBar().setBackground(UIUtil.TRANSPARENT_COLOR);
    scrollPane.getViewport().setBorder(null);
    scrollPane.getViewport().setOpaque(false);
    scrollPane.setBorder(JBUI.Borders.empty(0, 0, 0, 2));
    scrollPane.setOpaque(false);
    ActionButton button =
      new ActionButton(showHistoryAction, showHistoryAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, new Dimension(JBUI.scale(16), JBUI.scale(16)));
    button.setLook(new InplaceActionButtonLook());
    JPanel p = new NonOpaquePanel(new BorderLayout());
    p.add(button, BorderLayout.NORTH);
    add(p, BorderLayout.WEST);
    add(scrollPane, BorderLayout.CENTER);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();//myToWrap.getPreferredScrollableViewportSize();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    repaint();
  }

  @Override
  public void focusGained(FocusEvent e) {
    repaint();
  }

  @Override
  public void focusLost(FocusEvent e) {
    repaint();
  }

  @Override
  public void paint(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      Rectangle r = new Rectangle(getSize());
      r.x += JBUI.scale(4);
      r.y += JBUI.scale(3);
      r.width -= JBUI.scale(9);
      r.height -= JBUI.scale(6);
      int arcSize = Math.min(25, r.height - 1) - 6;
      g.setColor(myToWrap.getBackground());
      boolean hasFocus = myToWrap.hasFocus();
      g.fillRoundRect(r.x, r.y + 1, r.width, r.height - 2, arcSize, arcSize);
      g.setColor(myToWrap.isEnabled() ? Gray._100 : Gray._83);

      if (hasFocus) {
        DarculaUIUtil.paintSearchFocusRing(g, r, arcSize + 6);
      }
      else {
        g.drawRoundRect(r.x, r.y, r.width, r.height - 1, arcSize, arcSize);
      }
    }
    finally {
      g.dispose();
    }
    super.paint(graphics);
  }
}
