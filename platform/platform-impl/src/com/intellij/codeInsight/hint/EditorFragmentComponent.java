// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

public final class EditorFragmentComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(EditorFragmentComponent.class);
  private static final Key<WeakReference<LightweightHint>> CURRENT_HINT = Key.create("EditorFragmentComponent.currentHint");
  private static final int LINE_BORDER_THICKNESS = 1;
  private static final int EMPTY_BORDER_THICKNESS = 2;

  private EditorFragmentComponent(EditorEx editor, int startLine, int endLine, boolean showFolding, boolean showGutter) {
    editor.setPurePaintingMode(true);
    try {
      doInit(editor, startLine, endLine, showFolding, showGutter);
    }
    finally {
      editor.setPurePaintingMode(false);
    }
  }

  private void doInit(EditorEx editor, int startLine, int endLine, boolean showFolding, boolean showGutter) {
    FoldingModelEx foldingModel = editor.getFoldingModel();
    boolean isFoldingEnabled = foldingModel.isFoldingEnabled();
    if (!showFolding) {
      foldingModel.setFoldingEnabled(false);
    }
    int textImageWidth;
    int markersImageWidth;
    int textImageHeight;
    BufferedImage textImage;
    BufferedImage markersImage;
    JComponent rowHeader;
    try {
      Document doc = editor.getDocument();
      int endOffset = endLine < doc.getLineCount() ? doc.getLineEndOffset(Math.max(0, endLine - 1)) : doc.getTextLength();
      int widthAdjustment = EditorUtil.getSpaceWidth(Font.PLAIN, editor);
      textImageWidth = Math.min(
        editor.getMaxWidthInRange(doc.getLineStartOffset(startLine), endOffset) + widthAdjustment,
        getWidthLimit(editor)
      );

      int startVisualLine = editor.logicalToVisualPosition(new LogicalPosition(startLine, 0)).line;
      int endVisualLine = editor.logicalToVisualPosition(new LogicalPosition(Math.max(endLine, startLine + 1), 0)).line;
      int y1 = editor.visualLineToY(startVisualLine);
      // as endLine is exclusive (not shown), we should also exclude block inlays associated with it
      int y2 = editor.visualLineToY(endVisualLine) - EditorUtil.getInlaysHeight(editor, endVisualLine, true);
      textImageHeight = y2 <= y1 ? editor.getLineHeight() : y2 - y1;
      LOG.assertTrue(textImageHeight > 0,
                     "Height: " + textImageHeight + "; startLine:" + startLine + "; endLine:" + endLine + "; y1:" + y1 + "; y2:" + y2);

      textImage = UIUtil.createImage(editor.getContentComponent(), textImageWidth, textImageHeight, BufferedImage.TYPE_INT_RGB);
      Graphics textGraphics = textImage.getGraphics();
      EditorUIUtil.setupAntialiasing(textGraphics);

      if (showGutter) {
        rowHeader = editor.getGutterComponentEx();
        markersImageWidth = Math.max(1, rowHeader.getWidth());

        markersImage = UIUtil.createImage(editor.getComponent(), markersImageWidth, textImageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics markerGraphics = markersImage.getGraphics();
        EditorUIUtil.setupAntialiasing(markerGraphics);

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
      boolean wasVisible = editor.setCaretVisible(false);
      editor.getContentComponent().paint(textGraphics);
      if (wasVisible) {
        editor.setCaretVisible(true);
      }
    }
    finally {
      if (!showFolding) {
        foldingModel.setFoldingEnabled(isFoldingEnabled);
      }
    }

    JComponent component = new JComponent() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(textImageWidth + markersImageWidth, textImageHeight);
      }

      @Override
      protected void paintComponent(Graphics graphics) {
        if (markersImage != null) {
          StartupUiUtil.drawImage(graphics, markersImage, 0, 0, null);
          StartupUiUtil.drawImage(graphics, textImage, rowHeader.getWidth(), 0, null);
        }
        else {
          StartupUiUtil.drawImage(graphics, textImage, 0, 0, null);
        }
      }
    };

    setLayout(new BorderLayout());
    add(component);

    setBorder(createEditorFragmentBorder(editor));
  }

  private static int getWidthLimit(@NotNull Editor editor) {
    Component component = editor.getComponent();
    int screenWidth = ScreenUtil.getScreenRectangle(component).width;
    if (screenWidth > 0) return screenWidth;
    Window window = SwingUtilities.getWindowAncestor(component);
    return window == null ? Integer.MAX_VALUE : window.getWidth();
  }

  /**
   * @param y {@code y} coordinate in layered pane coordinate system.
   */
  @Nullable
  static LightweightHint showEditorFragmentHintAt(Editor editor,
                                                  TextRange range,
                                                  int y,
                                                  boolean showUpward,
                                                  boolean showFolding,
                                                  boolean hideByAnyKey,
                                                  boolean useCaretRowBackground) {
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

    int endLine = document.getLineNumber(range.getEndOffset()) + 1;

    if (startLine >= endLine) return null;

    EditorFragmentComponent fragmentComponent = createEditorFragmentComponent(editor, startLine, endLine, showFolding, true,
                                                                              useCaretRowBackground);

    if (showUpward) {
      y -= fragmentComponent.getPreferredSize().height;
      y  = Math.max(0,y);
    }

    final JComponent c = editor.getComponent();
    int x = SwingUtilities.convertPoint(c, new Point(JBUIScale.scale(-3), 0), UIUtil.getRootPane(c)).x; //IDEA-68016

    LightweightHint currentHint = SoftReference.dereference(editor.getUserData(CURRENT_HINT));
    if (currentHint != null) currentHint.hide();

    Point p = new Point(x, y);
    LightweightHint hint = new MyComponentHint(fragmentComponent);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, (hideByAnyKey ? HintManager.HIDE_BY_ANY_KEY : 0) |
                                                                      HintManager.HIDE_BY_SCROLLING |
                                                                      HintManager.HIDE_BY_TEXT_CHANGE |
                                                                      HintManager.HIDE_BY_MOUSEOVER,
                                                     0, false, new HintHint(editor, p));
    editor.putUserData(CURRENT_HINT, new WeakReference<>(hint));
    return hint;
  }

  public static EditorFragmentComponent createEditorFragmentComponent(Editor editor,
                                                                      int startLine,
                                                                      int endLine,
                                                                      boolean showFolding, boolean showGutter) {
    return createEditorFragmentComponent(editor, startLine, endLine, showFolding, showGutter, true);
  }

  private static EditorFragmentComponent createEditorFragmentComponent(Editor editor,
                                                                       int startLine,
                                                                       int endLine,
                                                                       boolean showFolding, boolean showGutter,
                                                                       boolean useCaretRowBackground) {
    final EditorEx editorEx = (EditorEx)editor;
    final Color old = editorEx.getBackgroundColor();
    Color backColor = getBackgroundColor(editor, useCaretRowBackground);
    editorEx.setBackgroundColor(backColor);
    EditorFragmentComponent fragmentComponent = new EditorFragmentComponent(editorEx, startLine, endLine, showFolding, showGutter);
    fragmentComponent.setBackground(backColor);

    editorEx.setBackgroundColor(old);
    return fragmentComponent;
  }

  @Nullable
  public static LightweightHint showEditorFragmentHint(Editor editor, TextRange range, boolean showFolding, boolean hideByAnyKey){
    if (!(editor instanceof EditorEx)) return null;
    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane == null) return null;
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    int lineHeight = editor.getLineHeight();
    int overhang = editor.getScrollingModel().getVisibleArea().y -
            editor.logicalPositionToXY(editor.offsetToLogicalPosition(range.getEndOffset())).y;
    int yRelative = overhang > 0 && overhang < lineHeight ?
                    lineHeight - overhang + JBUIScale.scale(LINE_BORDER_THICKNESS + EMPTY_BORDER_THICKNESS) : 0;
    Point point = SwingUtilities.convertPoint(((EditorEx)editor).getScrollPane().getViewport(), -2, yRelative, layeredPane);
    return showEditorFragmentHintAt(editor, range, point.y, true, showFolding, hideByAnyKey, false);
  }

  public static Color getBackgroundColor(Editor editor){
    return getBackgroundColor(editor, true);
  }

  public static Color getBackgroundColor(Editor editor, boolean useCaretRowBackground){
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    Color color = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    if (!useCaretRowBackground || color == null){
      color = editor instanceof EditorEx ? ((EditorEx)editor).getBackgroundColor() : colorsScheme.getDefaultBackground();
    }
    return color;
  }

  @NotNull
  public static CompoundBorder createEditorFragmentBorder(@NotNull Editor editor) {
    Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
    Border outsideBorder = JBUI.Borders.customLine(borderColor, LINE_BORDER_THICKNESS);
    Border insideBorder = JBUI.Borders.empty(EMPTY_BORDER_THICKNESS);
    return BorderFactory.createCompoundBorder(outsideBorder, insideBorder);
  }

  public static int getAvailableVisualLinesAboveEditor(@NotNull Editor editor) {
    int availableVisualLines = 2;
    JComponent editorComponent = editor.getComponent();
    Container editorComponentParent = editorComponent.getParent();
    if (editorComponentParent != null) {
      JRootPane rootPane = editorComponent.getRootPane();
      if (rootPane != null) {
        Container contentPane = rootPane.getContentPane();
        if (contentPane != null) {
          int y = SwingUtilities.convertPoint(editorComponentParent, editorComponent.getLocation(), contentPane).y;
          int visualLines = y / editor.getLineHeight();
          availableVisualLines = Math.max(availableVisualLines, visualLines);
        }
      }
    }
    return availableVisualLines;
  }

  private static class MyComponentHint extends LightweightHint {
    MyComponentHint(JComponent component) {
      super(component);
      setForceLightweightPopup(true);
    }

    @Override
    public void hide() {
      // needed for Alt-Q multiple times
      // Q: not good?
      SwingUtilities.invokeLater(
        () -> super.hide()
      );
    }
  }
}
