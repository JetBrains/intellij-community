// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;

public class ImaginaryEditor extends UserDataHolderBase implements Editor {
  private final ImaginaryCaretModel myCaretModel;
  private final ImaginarySelectionModel mySelectionModel;
  private final Project myProject;
  @NotNull private final Document myDocument;
  private final ImaginarySoftWrapModel mySoftWrapModel = new ImaginarySoftWrapModel();

  public ImaginaryEditor(@NotNull Project project, @NotNull Document document) {
    myProject = project;
    myDocument = document;
    myCaretModel = new ImaginaryCaretModel(this);
    mySelectionModel = new ImaginarySelectionModel(this);
  }

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  @Override
  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  @NotNull
  @Override
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }


  protected RuntimeException notImplemented() {
    return new UnsupportedOperationException();
  }

  @Override
  public boolean isViewer() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public JComponent getContentComponent() {
    throw notImplemented();
  }

  @Override
  public void setBorder(@Nullable Border border) {
    throw notImplemented();
  }

  @Override
  public Insets getInsets() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public MarkupModel getMarkupModel() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public FoldingModel getFoldingModel() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public ScrollingModel getScrollingModel() {
    return new ImaginaryScrollingModel(this);
  }

  @NotNull
  @Override
  public SoftWrapModel getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @NotNull
  @Override
  public EditorSettings getSettings() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public EditorColorsScheme getColorsScheme() {
    throw notImplemented();
  }

  @Override
  public int getLineHeight() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public Point logicalPositionToXY(@NotNull LogicalPosition pos) {
    throw notImplemented();
  }

  @Override
  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    Document document = getDocument();
    int lineStart = document.getLineStartOffset(pos.line);
    int lineEnd = document.getLineEndOffset(pos.line);
    return Math.min(lineEnd, lineStart + pos.column);
  }

  @NotNull
  @Override
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    // No folding support: logicalPos is always the same as visual pos
    return new VisualPosition(logicalPos.line, logicalPos.column);
  }

  @NotNull
  @Override
  public Point visualPositionToXY(@NotNull VisualPosition visible) {
    throw notImplemented();
  }

  @NotNull
  @Override
  public Point2D visualPositionToPoint2D(@NotNull VisualPosition pos) {
    throw notImplemented();
  }

  @NotNull
  @Override
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    return new LogicalPosition(visiblePos.line, visiblePos.column);
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    Document document = getDocument();
    int line = document.getLineNumber(offset);
    int col = document.getLineStartOffset(line);
    return new LogicalPosition(line, col);
  }

  @NotNull
  @Override
  public VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @NotNull
  @Override
  public VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return offsetToVisualPosition(offset);
  }

  @NotNull
  @Override
  public LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    throw notImplemented();
  }

  @NotNull
  @Override
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    throw notImplemented();
  }

  @NotNull
  @Override
  public VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    throw notImplemented();
  }

  @Override
  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
    throw notImplemented();
  }

  @Override
  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
    throw notImplemented();
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    throw notImplemented();
  }

  @Override
  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    throw notImplemented();
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isInsertMode() {
    return false;
  }

  @Override
  public boolean isColumnMode() {
    return false;
  }

  @Override
  public boolean isOneLineMode() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public EditorGutter getGutter() {
    throw notImplemented();
  }

  @Nullable
  @Override
  public EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e) {
    throw notImplemented();
  }

  @Override
  public void setHeaderComponent(@Nullable JComponent header) {
    throw notImplemented();
  }

  @Override
  public boolean hasHeaderComponent() {
    throw notImplemented();
  }

  @Nullable
  @Override
  public JComponent getHeaderComponent() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public IndentsModel getIndentsModel() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public InlayModel getInlayModel() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public EditorKind getEditorKind() {
    throw notImplemented();
  }

  // No soft-wraps at all
  private static class ImaginarySoftWrapModel implements SoftWrapModel {
    @Override
    public boolean isSoftWrappingEnabled() {
      return false;
    }

    @Override
    public @Nullable SoftWrap getSoftWrap(int offset) {
      return null;
    }

    @Override
    public @NotNull List<? extends SoftWrap> getSoftWrapsForRange(int start, int end) {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<? extends SoftWrap> getSoftWrapsForLine(int documentLine) {
      return Collections.emptyList();
    }

    @Override
    public boolean isVisible(SoftWrap softWrap) {
      return false;
    }

    @Override
    public void beforeDocumentChangeAtCaret() {

    }

    @Override
    public boolean isInsideSoftWrap(@NotNull VisualPosition position) {
      return false;
    }

    @Override
    public boolean isInsideOrBeforeSoftWrap(@NotNull VisualPosition visual) {
      return false;
    }

    @Override
    public void release() {

    }
  }
}
