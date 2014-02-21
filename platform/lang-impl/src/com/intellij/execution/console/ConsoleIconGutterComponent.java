package com.intellij.execution.console;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;

class ConsoleIconGutterComponent extends JComponent {
  private final int iconAreaWidth;

  private int myLastPreferredHeight = -1;
  private final EditorImpl editor;

  private final GutterContentProvider gutterContentProvider;

  public ConsoleIconGutterComponent(@NotNull Editor editor, @NotNull GutterContentProvider provider) {
    this.editor = (EditorImpl)editor;
    gutterContentProvider = provider;

    // icon/one-char symbol + space
    iconAreaWidth = EditorUtil.getSpaceWidth(Font.PLAIN, editor) * 2;
  }

  public void updateSize() {
    if (myLastPreferredHeight != editor.getPreferredHeight()) {
      fireResized();
    }
    repaint();
  }

  private void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  public Dimension getPreferredSize() {
    myLastPreferredHeight = editor.getPreferredHeight();
    return new Dimension(iconAreaWidth, myLastPreferredHeight);
  }

  @Override
  public void paint(Graphics g) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();
    try {
      Rectangle clip = g.getClipBounds();
      if (clip.height < 0) {
        return;
      }

      g.setColor(editor.getBackgroundColor());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);

      UISettings.setupAntialiasing(g);

      Graphics2D g2 = (Graphics2D)g;
      Object hint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      if (!UIUtil.isRetina()) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      try {
        paintAnnotations(g, clip);
      }
      finally {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
      }
    }
    finally {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
    }
  }

  private void paintAnnotations(Graphics g, Rectangle clip) {
    int lineHeight = editor.getLineHeight();
    int startLine = clip.y / lineHeight;
    int endLine = Math.min(((clip.y + clip.height) / lineHeight) + 1, editor.getVisibleLineCount());
    if (startLine >= endLine) {
      return;
    }

    g.setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
    int y = ((startLine + 1) * lineHeight) - editor.getDescent();
    for (int i = startLine; i < endLine; i++) {
      gutterContentProvider.drawIcon(editor.visualToLogicalPosition(new VisualPosition(i, 0)).line, g, y, editor);
      y += lineHeight;
    }
  }
}