// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class LineFunction {
  private final int myLines;
  private final boolean myShowDots;
  private Point myLastPoint;

  public LineFunction(int lines, boolean showDots) {
    myLines = lines + 1;
    myShowDots = showDots;
  }

  public void paintComponent(@NotNull JEditorPane pane, @NotNull Graphics g) {
    if (myShowDots && myLastPoint != null) {
      if (g instanceof Graphics2D) {
        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      }
      g.setColor(pane.getForeground());
      g.drawString("...", myLastPoint.x, myLastPoint.y + g.getFontMetrics().getAscent());
    }
  }

  public int getHeight(@NotNull JEditorPane pane) {
    myLastPoint = null;

    try {
      int line = 0;
      int startLineY = -1;
      int length = pane.getDocument().getLength();

      for (int i = 0; i < length; i++) {
        Rectangle r = pane.modelToView(i);
        if (r != null && r.height > 0 && startLineY < r.y) {
          startLineY = r.y;
          if (++line == myLines) {
            int ii = i;
            while (ii > 0) {
              Rectangle rr = pane.modelToView(--ii);
              if (rr != null) {
                myLastPoint = rr.getLocation();
                break;
              }
            }
            return r.y;
          }
        }
      }
    }
    catch (BadLocationException ignored) {
    }

    return (int)(pane.getUI().getRootView(pane).getPreferredSpan(View.Y_AXIS) + JBUIScale.scale(2f));
  }
}