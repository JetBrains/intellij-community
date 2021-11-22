// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import javax.swing.text.Highlighter.HighlightPainter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;

import static com.intellij.codeInsight.documentation.DocumentationManager.LOG;

final class DocumentationLinkHighlightPainter implements HighlightPainter {

  public static final HighlightPainter INSTANCE = new DocumentationLinkHighlightPainter();

  private static final Stroke STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{1}, 0);

  private DocumentationLinkHighlightPainter() { }

  @Override
  public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
    try {
      Rectangle target = c.getUI().getRootView(c).modelToView(p0, Position.Bias.Forward, p1, Position.Bias.Backward, bounds).getBounds();
      Graphics2D g2d = (Graphics2D)g.create();
      try {
        g2d.setStroke(STROKE);
        g2d.setColor(c.getSelectionColor());
        g2d.drawRect(target.x, target.y, target.width - 1, target.height - 1);
      }
      finally {
        g2d.dispose();
      }
    }
    catch (Exception e) {
      LOG.warn("Error painting link highlight", e);
    }
  }
}
