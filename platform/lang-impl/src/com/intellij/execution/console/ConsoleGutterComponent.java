package com.intellij.execution.console;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.ui.HintHint;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

class ConsoleGutterComponent extends JComponent implements MouseMotionListener {
  private static final TooltipGroup TOOLTIP_GROUP = new TooltipGroup("CONSOLE_GUTTER_TOOLTIP_GROUP", 0);
  private static final int RIGHT_INSET = 6;

  private int maxAnnotationWidth = 0;
  private int myLastPreferredHeight = -1;
  private final EditorImpl editor;

  private final GutterContentProvider gutterContentProvider;

  private int lastGutterToolTipLine = -1;

  public ConsoleGutterComponent(@NotNull Editor editor, @NotNull GutterContentProvider provider) {
    this.editor = (EditorImpl)editor;
    gutterContentProvider = provider;
    addListeners();

    addMouseMotionListener(this);
  }

  private void addListeners() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!e.isPopupTrigger()) {
          gutterContentProvider.doAction(getLineAtPoint(e.getPoint()), editor);
        }
      }
    });
  }

  public void updateSize() {
    int oldAnnotationsWidth = maxAnnotationWidth;
    computeMaxAnnotationWidth();
    if (oldAnnotationsWidth != maxAnnotationWidth || myLastPreferredHeight != editor.getPreferredHeight()) {
      fireResized();
    }
    repaint();
  }

  private void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  private void computeMaxAnnotationWidth() {
    if (!gutterContentProvider.hasText()) {
      maxAnnotationWidth = 0;
      return;
    }

    FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
    int lineCount = editor.getDocument().getLineCount();
    gutterContentProvider.beforeUiComponentUpdate(editor);
    int gutterSize = 0;
    for (int i = 0; i < lineCount; i++) {
      String text = gutterContentProvider.getText(i, editor);
      if (text != null) {
        gutterSize = Math.max(gutterSize, fontMetrics.stringWidth(text));
      }
    }
    maxAnnotationWidth = gutterSize + RIGHT_INSET;
  }

  @Override
  public Dimension getPreferredSize() {
    myLastPreferredHeight = editor.getPreferredHeight();
    return new Dimension(maxAnnotationWidth, myLastPreferredHeight);
  }

  @Override
  public void paint(Graphics g) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();
    try {
      Rectangle clip = g.getClipBounds();
      if (clip.height < 0 || maxAnnotationWidth == 0) {
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

    gutterContentProvider.beforeUiComponentUpdate(editor);

    g.setColor(JBColor.BLUE);
    g.setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
    int y = ((startLine + 1) * lineHeight) - editor.getDescent();
    FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
    for (int i = startLine; i < endLine; i++) {
      String text = gutterContentProvider.getText(editor.visualToLogicalPosition(new VisualPosition(i, 0)).line, editor);
      if (text != null) {
        // right-aligned
        g.drawString(text, maxAnnotationWidth - RIGHT_INSET - fontMetrics.stringWidth(text), y);
      }
      y += lineHeight;
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    TooltipController.getInstance().cancelTooltips();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    int line = getLineAtPoint(e.getPoint());
    if (line == lastGutterToolTipLine) {
      return;
    }

    TooltipController controller = TooltipController.getInstance();
    if (lastGutterToolTipLine != -1) {
      controller.cancelTooltip(TOOLTIP_GROUP, e, true);
    }

    String toolTip = gutterContentProvider.getToolTip(line, editor);
    setCursor(toolTip == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    if (toolTip == null) {
      lastGutterToolTipLine = -1;
      controller.cancelTooltip(TOOLTIP_GROUP, e, false);
    }
    else {
      lastGutterToolTipLine = line;
      RelativePoint showPoint = new RelativePoint(this, e.getPoint());
      controller.showTooltipByMouseMove(editor,
                                        showPoint,
                                        ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(toolTip),
                                        false,
                                        TOOLTIP_GROUP,
                                        new HintHint(this, e.getPoint()).setAwtTooltip(true));
    }
  }

  private int getLineAtPoint(@NotNull Point clickPoint) {
    return editor.yPositionToLogicalLine(clickPoint.y);
  }
}