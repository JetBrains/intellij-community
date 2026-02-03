// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * This class is intended to simplify implementation of dummy editors needed only to pass to place which expect {@link Editor}
 * but do nothing complicate with it, only simple things like getting document/project/caret/selection.<p></p>
 * <p>
 * Since Imaginary* classes are intended to be used by multiple parties,
 * they should be as free as possible of simplified versions of any real Editor's logic.
 * Simplification involves making some assumptions what the clients would need, and different clients may disagree on that.
 * Having a simplified version that "almost always" works would make it hard to notice when it's not enough,
 * so the default implementation of most methods is to throw an exception to make the problem obvious immediately.
 * Clients can add simplified logic themselves via subclassing, if they really need to.
 */
public class ImaginaryEditor extends UserDataHolderBase implements Editor {
  private final ImaginaryCaretModel myCaretModel;
  private final ImaginarySelectionModel mySelectionModel;
  private final Project myProject;
  private final @NotNull Document myDocument;
  private static final Logger LOG = Logger.getInstance(ImaginaryEditor.class);

  public ImaginaryEditor(@NotNull Project project, @NotNull Document document) {
    myProject = project;
    myDocument = document;
    myCaretModel = new ImaginaryCaretModel(this);
    mySelectionModel = new ImaginarySelectionModel(this);
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @NotNull CaretModel getCaretModel() {
    return myCaretModel;
  }

  @Override
  public @NotNull SelectionModel getSelectionModel() {
    return mySelectionModel;
  }


  protected RuntimeException notImplemented() {
    return new UnsupportedOperationException();
  }

  @Override
  public boolean isViewer() {
    throw notImplemented();
  }

  @Override
  public @NotNull JComponent getComponent() {
    throw notImplemented();
  }

  @Override
  public @NotNull JComponent getContentComponent() {
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

  @Override
  public @NotNull MarkupModel getMarkupModel() {
    throw notImplemented();
  }

  @Override
  public @NotNull FoldingModel getFoldingModel() {
    throw notImplemented();
  }

  @Override
  public @NotNull ScrollingModel getScrollingModel() {
    return new ImaginaryScrollingModel(this);
  }

  @Override
  public @NotNull SoftWrapModel getSoftWrapModel() {
    return new EmptySoftWrapModel();
  }

  @Override
  public @NotNull EditorSettings getSettings() {
    throw notImplemented();
  }

  @Override
  public @NotNull EditorColorsScheme getColorsScheme() {
    throw notImplemented();
  }

  @Override
  public int getLineHeight() {
    throw notImplemented();
  }

  @Override
  public @NotNull Point logicalPositionToXY(@NotNull LogicalPosition pos) {
    throw notImplemented();
  }

  @Override
  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    int line = MathUtil.clamp(pos.line, 0, myDocument.getLineCount());
    int startOffset = myDocument.getLineStartOffset(line);
    int endOffset = myDocument.getLineEndOffset(line);
    return MathUtil.clamp(startOffset + pos.column, startOffset, endOffset);
  }

  @Override
  public @NotNull VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    throw notImplemented();
  }

  @Override
  public @NotNull Point visualPositionToXY(@NotNull VisualPosition visible) {
    throw notImplemented();
  }

  @Override
  public @NotNull Point2D visualPositionToPoint2D(@NotNull VisualPosition pos) {
    throw notImplemented();
  }

  @Override
  public @NotNull LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    throw notImplemented();
  }

  @Override
  public @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    int clamped = MathUtil.clamp(offset, 0, myDocument.getTextLength());
    int line = myDocument.getLineNumber(clamped);
    int col = clamped - myDocument.getLineStartOffset(line);
    return new LogicalPosition(line, col);
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return offsetToVisualPosition(offset);
  }

  @Override
  public @NotNull LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    throw notImplemented();
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point p) {
    throw notImplemented();
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    throw notImplemented();
  }

  @Override
  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
    LOG.info("Called ImaginaryEditor#addEditorMouseListener which is stubbed and has no implementation");
  }

  @Override
  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
    LOG.info("Called ImaginaryEditor#removeEditorMouseListener which is stubbed and has no implementation");
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

  @Override
  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  public boolean isInsertMode() {
    return true;
  }

  @Override
  public boolean isColumnMode() {
    return false;
  }

  @Override
  public boolean isOneLineMode() {
    throw notImplemented();
  }

  @Override
  public @NotNull EditorGutter getGutter() {
    throw notImplemented();
  }

  @Override
  public @Nullable EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e) {
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

  @Override
  public @Nullable JComponent getHeaderComponent() {
    throw notImplemented();
  }

  @Override
  public @NotNull IndentsModel getIndentsModel() {
    throw notImplemented();
  }

  @Override
  public @NotNull InlayModel getInlayModel() {
    throw notImplemented();
  }

  @Override
  public @NotNull EditorKind getEditorKind() {
    return EditorKind.UNTYPED;
  }
}
