/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class EditorFragmentComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.EditorFragmentComponent");

  private EditorFragmentComponent(EditorEx editor, int startLine, int endLine, boolean showFolding, boolean showGutter, Pair<RangeHighlighter, int[]>... highlighters) {
    editor.setPurePaintingMode(true);
    try {
      doInit(editor, startLine, endLine, showFolding, showGutter, highlighters);
    }
    finally {
      editor.setPurePaintingMode(false);
    }
  }

  private void doInit(final EditorEx editor, final int startLine, final int endLine, boolean showFolding, boolean showGutter, final Pair<RangeHighlighter, int[]>... highlighters) {
    final Document doc = editor.getDocument();
    final int endOffset = endLine < doc.getLineCount() ? doc.getLineEndOffset(endLine) : doc.getTextLength();
    final int textImageWidth = Math.min(editor.getMaxWidthInRange(doc.getLineStartOffset(startLine), endOffset), ScreenUtil
      .getScreenRectangle(1, 1).width);
    LOG.assertTrue(textImageWidth > 0, "TextWidth: "+textImageWidth+"; startLine:" + startLine + "; endLine:" + endLine + ";");

    FoldingModelEx foldingModel = editor.getFoldingModel();
    boolean isFoldingEnabled = foldingModel.isFoldingEnabled();
    if (!showFolding) {
      foldingModel.setFoldingEnabled(false);
    }

    Point p1 = editor.logicalPositionToXY(new LogicalPosition(startLine, 0));
    Point p2 = editor.logicalPositionToXY(new LogicalPosition(Math.max(endLine, startLine + 1), 0));
    final int y1 = p1.y;
    int y2 = p2.y;
    final int textImageHeight = y2 - y1 == 0 ? editor.getLineHeight() : y2 - y1;
    LOG.assertTrue(textImageHeight > 0, "Height: " + textImageHeight + "; startLine:" + startLine + "; endLine:" + endLine + "; p1:" + p1 + "; p2:" + p2);

    int savedScrollOffset = editor.getScrollingModel().getHorizontalScrollOffset();
    if (savedScrollOffset > 0) {
      editor.stopOptimizedScrolling();
      editor.getScrollingModel().scrollHorizontally(0);
    }

    final BufferedImage textImage = UIUtil.createImage(textImageWidth, textImageHeight, BufferedImage.TYPE_INT_RGB);
    Graphics textGraphics = textImage.getGraphics();
    UISettings.setupAntialiasing(textGraphics);

    final JComponent rowHeader;
    final BufferedImage markersImage;
    final int markersImageWidth;

    if (showGutter) {
      rowHeader = editor.getGutterComponentEx();
      markersImageWidth = Math.max(1, rowHeader.getWidth());

      markersImage = UIUtil.createImage(markersImageWidth, textImageHeight, BufferedImage.TYPE_INT_RGB);
      Graphics markerGraphics = markersImage.getGraphics();
      UISettings.setupAntialiasing(markerGraphics);

      markerGraphics.translate(0, -y1);
      markerGraphics.setClip(0, y1, rowHeader.getWidth(), textImageHeight);
      markerGraphics.setColor(getBackgroundColor(editor));
      markerGraphics.fillRect(0, y1, rowHeader.getWidth(), textImageHeight);
      rowHeader.paint(markerGraphics);
    }
    else {
      markersImageWidth = 0;
      rowHeader = null;
      markersImage = null;
    }

    textGraphics.translate(0, -y1);
    textGraphics.setClip(0, y1, textImageWidth, textImageHeight);
    final boolean wasVisible = editor.setCaretVisible(false);
    editor.getContentComponent().paint(textGraphics);
    if (wasVisible) {
      editor.setCaretVisible(true);
    }

    if (!showFolding) {
      foldingModel.setFoldingEnabled(isFoldingEnabled);
    }

    if (savedScrollOffset > 0) {
      editor.stopOptimizedScrolling();
      editor.getScrollingModel().scrollHorizontally(savedScrollOffset);
    }

    final JComponent component = new JComponent() {
      private static final int R = 6;
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(textImageWidth + markersImageWidth, textImageHeight);
      }

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D graphics = (Graphics2D)g;
        if (markersImage != null) {
          UIUtil.drawImage(graphics, markersImage, 0, 0, null);
          UIUtil.drawImage(graphics, textImage, rowHeader.getWidth(), 0, null);
        }
        else {
          UIUtil.drawImage(graphics, textImage, 0, 0, null);
        }

        if (highlighters.length == 0 || !ApplicationManager.getApplication().isInternal()) return;

        List<Pair<RangeHighlighter, int[]>> list = Arrays.asList(highlighters);
        Collections.sort(list, new Comparator<Pair<RangeHighlighter, int[]>>() {
          public int compare(Pair<RangeHighlighter, int[]> p1, Pair<RangeHighlighter, int[]> p2) {
            LogicalPosition startPos1 = editor.offsetToLogicalPosition(p1.getSecond()[0]);
            LogicalPosition startPos2 = editor.offsetToLogicalPosition(p2.getSecond()[0]);
            if (startPos1.line != startPos2.line) return 0;
            return startPos1.column - startPos2.column;
          }
        });
        Map<Integer, Integer> rightEdges = new HashMap<Integer, Integer>();
        for (Pair<RangeHighlighter, int[]> highlightInfo : list) {
          RangeHighlighter highlighter = highlightInfo.getFirst();
          int hStartOffset = highlightInfo.getSecond()[0];
          int hEndOffset = highlightInfo.getSecond()[1];
          Object tooltip = highlighter.getErrorStripeTooltip();
          if (tooltip == null) continue;
          String s = String.valueOf(tooltip);
          if (s.isEmpty()) continue;
          final int endOffset2 = endLine - 1 < doc.getLineCount() ? doc.getLineEndOffset(endLine - 1) : doc.getTextLength();
          if (hEndOffset < doc.getLineStartOffset(startLine)) continue;
          if (hStartOffset > endOffset2 || doc.getLineNumber(hStartOffset) > endLine) continue;

          LogicalPosition logicalPosition = editor.offsetToLogicalPosition(hStartOffset);
          Point placeToShow = editor.logicalPositionToXY(logicalPosition);
          placeToShow.y -= (y1 - editor.getLineHeight() * 3 / 2);
          if (markersImage != null) {
            placeToShow.x += rowHeader.getWidth();
          }

          int w = graphics.getFontMetrics().stringWidth(s);
          int a = graphics.getFontMetrics().getAscent();
          int h = editor.getLineHeight();

          Integer rightEdge = rightEdges.get(logicalPosition.line);
          if (rightEdge == null) rightEdge = 0;
          placeToShow.x = Math.max(placeToShow.x, rightEdge);
          rightEdge  = Math.max(rightEdge, placeToShow.x + w + 3 * R);
          rightEdges.put(logicalPosition.line, rightEdge);

          GraphicsUtil.setupAAPainting(graphics);
          graphics.setColor(MessageType.WARNING.getPopupBackground());
          graphics.fillRoundRect(placeToShow.x - R, placeToShow.y - a, w + 2 * R, h, R, R);
          graphics.setColor(new JBColor(JBColor.GRAY, Gray._200));
          graphics.drawRoundRect(placeToShow.x - R, placeToShow.y - a, w + 2 * R, h, R, R);
          graphics.setColor(JBColor.foreground());
          graphics.drawString(s, placeToShow.x, placeToShow.y + 2);
        }
      }
    };

    setLayout(new GridLayout(1, 1));
    add(component);

    final Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);

    Border outsideBorder = BorderFactory.createLineBorder(borderColor, 1);
    Border insideBorder = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
  }

  /**
   * @param hideByAnyKey
   * @param x <code>x</code> coordinate in layered pane coordinate system.
   * @param y <code>y</code> coordinate in layered pane coordinate system.
   */
  @Nullable
  public static LightweightHint showEditorFragmentHintAt(Editor editor,
                                                         TextRange range,
                                                         int x,
                                                         int y,
                                                         boolean showUpward,
                                                         boolean showFolding,
                                                         boolean hideByAnyKey,
                                                         Pair<RangeHighlighter, int[]>... highlighters) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    Document document = editor.getDocument();

    int startOffset = range.getStartOffset();
    int startLine = document.getLineNumber(startOffset);
    CharSequence text = document.getCharsSequence();
    // There is a possible case that we have a situation like below:
    //    line 1
    //    line 2 <fragment start>
    //    line 3<fragment end>
    // We don't want to include 'line 2' to the target fragment then.
    boolean incrementLine = false;
    for (int offset = startOffset, max = Math.min(range.getEndOffset(), text.length()); offset < max; offset++) {
      char c = text.charAt(offset);
      incrementLine = StringUtil.isWhiteSpace(c);
      if (!incrementLine || c == '\n') {
        break;
      } 
    }
    if (incrementLine) {
      startLine++;
    } 
    
    int endLine = Math.min(document.getLineNumber(range.getEndOffset()) + 1, document.getLineCount() - 1);

    //if (editor.logicalPositionToXY(new LogicalPosition(startLine, 0)).y >= editor.logicalPositionToXY(new LogicalPosition(endLine, 0)).y) return null;
    if (startLine >= endLine) return null;

    EditorFragmentComponent fragmentComponent = createEditorFragmentComponent(editor, startLine, endLine, showFolding, true, highlighters);


    if (showUpward) {
      y -= fragmentComponent.getPreferredSize().height + 10;
      y  = Math.max(0,y);
    }

    final JComponent c = editor.getComponent();
    x = SwingUtilities.convertPoint(c, new Point(-3,0), UIUtil.getRootPane(c)).x; //IDEA-68016

    Point p = new Point(x, y);
    LightweightHint hint = new MyComponentHint(fragmentComponent);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, (hideByAnyKey ? HintManager.HIDE_BY_ANY_KEY : 0) |
                                                                      HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_MOUSEOVER,
                                                     0, false, new HintHint(editor, p));
    return hint;
  }

  public static EditorFragmentComponent createEditorFragmentComponent(Editor editor,
                                                                      int startLine,
                                                                      int endLine,
                                                                      boolean showFolding,
                                                                      boolean showGutter,
                                                                      Pair<RangeHighlighter, int[]>... highlighters) {
    final EditorEx editorEx = (EditorEx)editor;
    final Color old = editorEx.getBackgroundColor();
    Color backColor = getBackgroundColor(editor);
    editorEx.setBackgroundColor(backColor);
    EditorFragmentComponent fragmentComponent = new EditorFragmentComponent(editorEx, startLine, endLine,
                                                                            showFolding, showGutter, highlighters);
    fragmentComponent.setBackground(backColor);

    editorEx.setBackgroundColor(old);
    return fragmentComponent;
  }

  @Nullable
  public static LightweightHint showEditorFragmentHint(Editor editor, TextRange range, boolean showFolding, boolean hideByAnyKey){

    JComponent editorComponent = editor.getComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) return null;
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    int x = -2;
    int y = 0;
    Point point = SwingUtilities.convertPoint(editorComponent, x, y, layeredPane);

    return showEditorFragmentHintAt(editor, range, point.x, point.y, true, showFolding, hideByAnyKey);
  }

  public static Color getBackgroundColor(Editor editor){
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    Color color = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    if (color == null){
      color = colorsScheme.getDefaultBackground();
    }
    return color;
  }

  private static class MyComponentHint extends LightweightHint {
    public MyComponentHint(JComponent component) {
      super(component);
      setForceLightweightPopup(true);
    }

    @Override
    public void hide() {
      // needed for Alt-Q multiple times
      // Q: not good?
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            MyComponentHint.super.hide();
          }
        }
      );
    }
  }
}
