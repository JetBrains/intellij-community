// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * @author Dmitry Avdeev
 */
public class LazyEditor extends UserDataHolderBase implements Editor {

  private final PsiFile myFile;
  private Editor myEditor;

  public LazyEditor(PsiFile file) {
    myFile = file;
  }

  private Editor getEditor() {
    if (myEditor == null) {
      final Project project = myFile.getProject();
      myEditor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), false);
      assert myEditor != null;
    }
    return myEditor;
  }

  @Override
  public @NotNull Document getDocument() {
    return getEditor().getDocument();
  }

  @Override
  public boolean isViewer() {
    return getEditor().isViewer();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return getEditor().getComponent();
  }

  @Override
  public @NotNull JComponent getContentComponent() {
    return getEditor().getContentComponent();
  }

  @Override
  public void setBorder(@Nullable Border border) {
    getEditor().setBorder(border);
  }

  @Override
  public Insets getInsets() {
    return getEditor().getInsets();
  }

  @Override
  public @NotNull SelectionModel getSelectionModel() {
    return getEditor().getSelectionModel();
  }

  @Override
  public @NotNull MarkupModel getMarkupModel() {
    return getEditor().getMarkupModel();
  }

  @Override
  public @NotNull FoldingModel getFoldingModel() {
    return getEditor().getFoldingModel();
  }

  @Override
  public @NotNull ScrollingModel getScrollingModel() {
    return getEditor().getScrollingModel();
  }

  @Override
  public @NotNull CaretModel getCaretModel() {
    return getEditor().getCaretModel();
  }

  @Override
  public @NotNull SoftWrapModel getSoftWrapModel() {
    return getEditor().getSoftWrapModel();
  }

  @Override
  public @NotNull InlayModel getInlayModel() {
    return getEditor().getInlayModel();
  }

  @Override
  public @NotNull EditorKind getEditorKind() {
    return getEditor().getEditorKind();
  }

  @Override
  public @NotNull EditorSettings getSettings() {
    return getEditor().getSettings();
  }

  @Override
  public @NotNull EditorColorsScheme getColorsScheme() {
    return getEditor().getColorsScheme();
  }

  @Override
  public int getLineHeight() {
    return getEditor().getLineHeight();
  }

  @Override
  public @NotNull Point logicalPositionToXY(final @NotNull LogicalPosition pos) {
    return getEditor().logicalPositionToXY(pos);
  }

  @Override
  public int logicalPositionToOffset(final @NotNull LogicalPosition pos) {
    return getEditor().logicalPositionToOffset(pos);
  }

  @Override
  public @NotNull VisualPosition logicalToVisualPosition(final @NotNull LogicalPosition logicalPos) {
    return getEditor().logicalToVisualPosition(logicalPos);
  }

  @Override
  public @NotNull Point visualPositionToXY(final @NotNull VisualPosition visible) {
    return getEditor().visualPositionToXY(visible);
  }

  @Override
  public @NotNull Point2D visualPositionToPoint2D(@NotNull VisualPosition pos) {
    return getEditor().visualPositionToPoint2D(pos);
  }

  @Override
  public @NotNull LogicalPosition visualToLogicalPosition(final @NotNull VisualPosition visiblePos) {
    return getEditor().visualToLogicalPosition(visiblePos);
  }

  @Override
  public @NotNull LogicalPosition offsetToLogicalPosition(final int offset) {
    return getEditor().offsetToLogicalPosition(offset);
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(final int offset) {
    return getEditor().offsetToVisualPosition(offset);
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return getEditor().offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
  }

  @Override
  public @NotNull LogicalPosition xyToLogicalPosition(final @NotNull Point p) {
    return getEditor().xyToLogicalPosition(p);
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(final @NotNull Point p) {
    return getEditor().xyToVisualPosition(p);
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    return getEditor().xyToVisualPosition(p);
  }

  @Override
  public void addEditorMouseListener(final @NotNull EditorMouseListener listener) {
    getEditor().addEditorMouseListener(listener);
  }

  @Override
  public void removeEditorMouseListener(final @NotNull EditorMouseListener listener) {
    getEditor().removeEditorMouseListener(listener);
  }

  @Override
  public void addEditorMouseMotionListener(final @NotNull EditorMouseMotionListener listener) {
    getEditor().addEditorMouseMotionListener(listener);
  }

  @Override
  public void removeEditorMouseMotionListener(final @NotNull EditorMouseMotionListener listener) {
    getEditor().removeEditorMouseMotionListener(listener);
  }

  @Override
  public boolean isDisposed() {
    return getEditor().isDisposed();
  }

  @Override
  public @Nullable Project getProject() {
    return getEditor().getProject();
  }

  @Override
  public boolean isInsertMode() {
    return getEditor().isInsertMode();
  }

  @Override
  public boolean isColumnMode() {
    return getEditor().isColumnMode();
  }

  @Override
  public boolean isOneLineMode() {
    return getEditor().isOneLineMode();
  }

  @Override
  public @NotNull EditorGutter getGutter() {
    return getEditor().getGutter();
  }

  @Override
  public @Nullable EditorMouseEventArea getMouseEventArea(final @NotNull MouseEvent e) {
    return getEditor().getMouseEventArea(e);
  }

  @Override
  public void setHeaderComponent(final @Nullable JComponent header) {
    getEditor().setHeaderComponent(header);
  }

  @Override
  public boolean hasHeaderComponent() {
    return getEditor().hasHeaderComponent();
  }

  @Override
  public @Nullable JComponent getHeaderComponent() {
    return getEditor().getHeaderComponent();
  }

  @Override
  public @NotNull IndentsModel getIndentsModel() {
    return getEditor().getIndentsModel();
  }

  @Override
  public int getAscent() {
    return getEditor().getAscent();
  }
}
