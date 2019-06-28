// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.awt.*;

public class JBTextArea extends JTextArea {
  //private final DefaultBoundedRangeModel visibility;

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
    Dimension d = super.getPreferredSize();
    Insets insets = getInsets();
    String[] lines = getText().split("\n");
    int columns = 0;
    int rows = lines.length;
    for (String line : lines) {
      columns = Math.max(columns, line.length());
    }
    if (columns != 0) {
      d.width = Math.max(d.width, columns * getColumnWidth() +
                                  insets.left + insets.right);
    }
    if (rows != 0) {
      d.height = Math.max(d.height, rows * getRowHeight() +
                                    insets.top + insets.bottom);
    }
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


}
