// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @NotNull private final Document myDocument;
  private static final Logger LOG = Logger.getInstance(ImaginaryEditor.class);

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
    return new EmptySoftWrapModel();
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
    throw notImplemented();
  }

  @NotNull
  @Override
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    throw notImplemented();
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
    throw notImplemented();
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    throw notImplemented();
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

  @Nullable
  @Override
  public Project getProject() {
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
}
