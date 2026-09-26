// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

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
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.image.BufferedImage;

public final class EditorFragmentComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(EditorFragmentComponent.class);

  private final EditorEx myEditor;
  private final int myStartLine;
  private final int myEndLine;
  private final boolean myShowFolding;
  private final boolean myShowGutter;
  private @Nullable EditorFragmentComponent.ImageComponent myImageComponent;

  public static @Nullable LightweightHint showEditorFragmentHint(
    Editor editor,
    TextRange range,
    boolean showFolding,
    boolean hideByAnyKey
  ) {
    return EditorFragmentHint.show(editor, range, showFolding, hideByAnyKey);
  }

  public static EditorFragmentComponent createEditorFragmentComponent(
    Editor editor,
    int startLine,
    int endLine,
    boolean showFolding,
    boolean showGutter
  ) {
    return createEditorFragmentComponent(
      editor,
      startLine,
      endLine,
      showFolding,
      showGutter,
      /* useCaretRowBackground */ true
    );
  }

  public static @NotNull Color getBackgroundColor(@NotNull Editor editor) {
    return getBackgroundColor(editor, true);
  }

  public static @NotNull Color getBackgroundColor(@NotNull Editor editor, boolean useCaretRowBackground) {
    return getBackgroundColor0(editor, useCaretRowBackground);
  }

  public static @NotNull CompoundBorder createEditorFragmentBorder(@NotNull Editor editor) {
    return EditorFragmentHint.createBorder(editor);
  }

  public static int getAvailableVisualLinesAboveEditor(@NotNull Editor editor) {
    return getAvailableVisualLinesAboveEditor0(editor);
  }

  private EditorFragmentComponent(
    @NotNull EditorEx editor,
    int startLine,
    int endLine,
    boolean showFolding,
    boolean showGutter,
    @NotNull ImageComponent component,
    Color backColor
  ) {
    myEditor = editor;
    myStartLine = startLine;
    myEndLine = endLine;
    myShowFolding = showFolding;
    myShowGutter = showGutter;
    myImageComponent = component;

    setLayout(new BorderLayout());
    add(component);
    setBorder(createEditorFragmentBorder(editor));
    setBackground(backColor);
  }

  @ApiStatus.Internal
  public EditorEx getEditor() {
    return myEditor;
  }

  @ApiStatus.Internal
  public int getStartLine() {
    return myStartLine;
  }

  @ApiStatus.Internal
  public int getEndLine() {
    return myEndLine;
  }

  @ApiStatus.Internal
  public boolean showFolding() {
    return myShowFolding;
  }

  @ApiStatus.Internal
  public boolean showGutter() {
    return myShowGutter;
  }

  void releaseImages() {
    ImageComponent imageComponent = myImageComponent;
    if (imageComponent == null) {
      return;
    }
    myImageComponent = null;
    imageComponent.releaseImages();
    remove(imageComponent);
    revalidate();
    repaint();
  }

  static @NotNull EditorFragmentComponent createEditorFragmentComponent(
    Editor editor,
    int startLine,
    int endLine,
    boolean showFolding,
    boolean showGutter,
    boolean useCaretRowBackground
  ) {
    EditorEx editorEx = (EditorEx) editor;
    Color old = editorEx.getBackgroundColor();
    Color backColor = getBackgroundColor(editor, useCaretRowBackground);
    ImageComponent image;
    editorEx.setBackgroundColor(backColor);
    editorEx.setPurePaintingMode(true);
    try {
      image = createImageComponent(editorEx, startLine, endLine, showFolding, showGutter);
    } finally {
      editorEx.setPurePaintingMode(false);
      editorEx.setBackgroundColor(old);
    }
    return new EditorFragmentComponent(editorEx, startLine, endLine, showFolding, showGutter, image, backColor);
  }

  private static @NotNull ImageComponent createImageComponent(
    @NotNull EditorEx editor,
    int startLine,
    int endLine,
    boolean showFolding,
    boolean showGutter
  ) {
    FoldingModelEx foldingModel = editor.getFoldingModel();
    boolean isFoldingEnabled = foldingModel.isFoldingEnabled();
    if (!showFolding) {
      foldingModel.setFoldingEnabled(false);
    }
    try {
      return createImageComponent(editor, startLine, endLine, showGutter);
    } finally {
      if (!showFolding) {
        foldingModel.setFoldingEnabled(isFoldingEnabled);
      }
    }
  }

  private static @NotNull ImageComponent createImageComponent(
    @NotNull EditorEx editor,
    int startLine,
    int endLine,
    boolean showGutter
  ) {
    Document doc = editor.getDocument();
    int endOffset = endLine < doc.getLineCount()
                    ? doc.getLineEndOffset(Math.max(0, endLine - 1))
                    : doc.getTextLength();
    int widthAdjustment = EditorUtil.getSpaceWidth(Font.PLAIN, editor);
    int textImageWidth = Math.min(
      editor.getMaxWidthInRange(doc.getLineStartOffset(startLine), endOffset) + widthAdjustment,
      getWidthLimit(editor)
    );
    int startVisualLine = editor.logicalToVisualPosition(new LogicalPosition(startLine, 0)).line;
    int endVisualLine = editor.logicalToVisualPosition(new LogicalPosition(Math.max(endLine, startLine + 1), 0)).line;
    int y1 = editor.visualLineToY(startVisualLine);
    // as endLine is exclusive (not shown), we should also exclude block inlays associated with it
    int y2 = editor.visualLineToY(endVisualLine) - EditorUtil.getInlaysHeight(editor, endVisualLine, true);
    int textImageHeight = y2 <= y1 ? editor.getLineHeight() : Math.min(y2 - y1, getHeightLimit(editor));
    LOG.assertTrue(
      textImageHeight > 0,
      "Height: " + textImageHeight + "; startLine:" + startLine + "; endLine:" + endLine + "; y1:" + y1 + "; y2:" + y2
    );
    BufferedImage textImage = UIUtil.createImage(editor.getContentComponent(), textImageWidth, textImageHeight, BufferedImage.TYPE_INT_RGB);
    Graphics textGraphics = textImage.getGraphics();
    EditorUIUtil.setupAntialiasing(textGraphics);
    BufferedImage markersImage;
    int markersImageWidth;
    if (showGutter) {
      JComponent rowHeader = editor.getGutterComponentEx();
      markersImageWidth = Math.max(1, rowHeader.getWidth());
      markersImage = UIUtil.createImage(editor.getComponent(), markersImageWidth, textImageHeight, BufferedImage.TYPE_INT_RGB);
      Graphics markerGraphics = markersImage.getGraphics();
      EditorUIUtil.setupAntialiasing(markerGraphics);
      markerGraphics.translate(0, -y1);
      //noinspection GraphicsSetClipInspection
      markerGraphics.setClip(0, y1, rowHeader.getWidth(), textImageHeight);
      markerGraphics.setColor(getBackgroundColor(editor));
      markerGraphics.fillRect(0, y1, rowHeader.getWidth(), textImageHeight);
      rowHeader.paint(markerGraphics);
    } else {
      markersImageWidth = 0;
      markersImage = null;
    }
    textGraphics.translate(0, -y1);
    //noinspection GraphicsSetClipInspection
    textGraphics.setClip(0, y1, textImageWidth, textImageHeight);
    boolean wasVisible = editor.setCaretVisible(false);
    try {
      editor.getContentComponent().paint(textGraphics);
    } finally {
      if (wasVisible) {
        editor.setCaretVisible(true);
      }
    }
    return new ImageComponent(
      textImage,
      markersImage,
      markersImageWidth,
      textImageWidth + markersImageWidth,
      textImageHeight
    );
  }

  private static int getWidthLimit(@NotNull Editor editor) {
    Component component = editor.getComponent();
    int screenWidth = ScreenUtil.getScreenRectangle(component).width;
    if (screenWidth > 0) return screenWidth;
    Window window = SwingUtilities.getWindowAncestor(component);
    return window == null ? Integer.MAX_VALUE : window.getWidth();
  }

  private static int getHeightLimit(@NotNull Editor editor) {
    Component component = editor.getComponent();
    int screenHeight = ScreenUtil.getScreenRectangle(component).height;
    if (screenHeight > 0) return screenHeight;
    Window window = SwingUtilities.getWindowAncestor(component);
    if (window != null && window.getHeight() > 0) return window.getHeight();
    int componentHeight = component.getHeight();
    return componentHeight > 0 ? componentHeight : Integer.MAX_VALUE;
  }

  private static @NotNull Color getBackgroundColor0(@NotNull Editor editor, boolean useCaretRowBackground) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    Color color = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    if (!useCaretRowBackground || color == null) {
      color = editor instanceof EditorEx editorEx
              ? editorEx.getBackgroundColor()
              : colorsScheme.getDefaultBackground();
    }
    return color;
  }

  private static int getAvailableVisualLinesAboveEditor0(@NotNull Editor editor) {
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

  @ApiStatus.Internal
  @TestOnly
  public static @NotNull LightweightHint createEditorFragmentHintForTest(@NotNull EditorFragmentComponent fragmentComponent) {
    return new EditorFragmentHint(fragmentComponent);
  }

  private static final class ImageComponent extends JComponent {
    private @Nullable BufferedImage myTextImage;
    private @Nullable BufferedImage myMarkersImage;
    private final int myMarkersImageWidth;
    private final int myPreferredWidth;
    private final int myPreferredHeight;

    ImageComponent(
      @NotNull BufferedImage textImage,
      @Nullable BufferedImage markersImage,
      int markersImageWidth,
      int preferredWidth,
      int preferredHeight
    ) {
      myTextImage = textImage;
      myMarkersImage = markersImage;
      myMarkersImageWidth = markersImageWidth;
      myPreferredWidth = preferredWidth;
      myPreferredHeight = preferredHeight;
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(myPreferredWidth, myPreferredHeight);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      BufferedImage textImage = myTextImage;
      if (textImage == null) {
        return;
      }
      BufferedImage markersImage = myMarkersImage;
      if (markersImage != null) {
        StartupUiUtil.drawImage(graphics, markersImage, 0, 0, null);
        StartupUiUtil.drawImage(graphics, textImage, myMarkersImageWidth, 0, null);
      } else {
        StartupUiUtil.drawImage(graphics, textImage, 0, 0, null);
      }
    }

    void releaseImages() {
      BufferedImage textImage = myTextImage;
      if (textImage != null) {
        textImage.flush();
        myTextImage = null;
      }
      BufferedImage markersImage = myMarkersImage;
      if (markersImage != null) {
        markersImage.flush();
        myMarkersImage = null;
      }
    }
  }
}
