package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class LineRenderer implements LineMarkerRenderer {
  private final boolean myDrawBottom;

  private LineRenderer(boolean drawBottom) {
    myDrawBottom = drawBottom;
  }

  public void paint(Editor editor, Graphics g, Rectangle r) {
    g.setColor(Color.GRAY);
    int y = r.y - 1;
    if (myDrawBottom) y += r.height + editor.getLineHeight();
    UIUtil.drawLine(g, 0, y, ((EditorEx)editor).getGutterComponentEx().getWidth(), y);
  }

  public static LineMarkerRenderer bottom() {
    return new LineRenderer(true);
  }

  public static LineMarkerRenderer top() {
    return new LineRenderer(false);
  }
}
