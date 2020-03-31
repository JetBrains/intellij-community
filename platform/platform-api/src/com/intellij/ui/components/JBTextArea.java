// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.BooleanFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class JBTextArea extends JTextArea implements ComponentWithEmptyText {
  //private final DefaultBoundedRangeModel visibility;

  private final TextComponentEmptyText myEmptyText;

  public JBTextArea() {
    this(null, null, 0, 0);
  }

  public JBTextArea(String text) {
    this(null, text, 0, 0);
  }

  public JBTextArea(int rows, int columns) {
    this(null, null, rows, columns);
  }

  public JBTextArea(String text, int rows, int columns) {
    this(null, text, rows, columns);
  }

  public JBTextArea(Document doc) {
    this(doc, null, 0, 0);
  }

  public JBTextArea(Document doc, String text, int rows, int columns) {
    super(doc, text, rows, columns);

    myEmptyText = new TextComponentEmptyText(this) {
      @Override
      protected boolean isStatusVisible() {
        Object function = getClientProperty("StatusVisibleFunction");
        if (function instanceof BooleanFunction) {
          //noinspection unchecked
          return ((BooleanFunction<JTextComponent>)function).fun(JBTextArea.this);
        }
        return super.isStatusVisible();
      }

      @Override
      protected Rectangle getTextComponentBound() {
        Insets insets = ObjectUtils.notNull(getInsets(), JBUI.emptyInsets());
        Insets margin = ObjectUtils.notNull(getMargin(), JBUI.emptyInsets());
        Insets ipad = getComponent().getIpad();
        Dimension size = getSize();
        int left = insets.left + margin.left - ipad.left;
        int top = insets.top + margin.top - ipad.top;
        int right = size.width - (insets.right + margin.right - ipad.right);
        return new Rectangle(left, top, right - left, getRowHeight());
      }
    };

    getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        invalidate();
        revalidate();
        repaint();
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) return super.getPreferredSize();
    int width = 0;
    FontMetrics fontMetrics = getFontMetrics(getFont());
    for (String line : getText().split("\n")) {
      width = Math.max(width, fontMetrics.stringWidth(line));
    }
    Dimension d = super.getPreferredSize();
    Insets insets = getInsets();
    d.width = Math.min(d.width, width + insets.left + insets.right);
    return d;
  }

  @Override
  public void scrollRectToVisible(Rectangle r) {
    JViewport viewport = ComponentUtil.getParentOfType((Class<? extends JViewport>)JViewport.class, (Component)this);
    if (viewport != null) {
      r = SwingUtilities.convertRectangle(this, r, viewport);
      viewport.scrollRectToVisible(r);
    }
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (!myEmptyText.getStatusTriggerText().isEmpty() && myEmptyText.isStatusVisible()) {
      g.setColor(getBackground());

      Rectangle rect = new Rectangle(getSize());
      JBInsets.removeFrom(rect, getInsets());
      JBInsets.removeFrom(rect, getMargin());
      ((Graphics2D)g).fill(rect);

      g.setColor(getForeground());
    }
    myEmptyText.paintStatusText(g);
  }
}
