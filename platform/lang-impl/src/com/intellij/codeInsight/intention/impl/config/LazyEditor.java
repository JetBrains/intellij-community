/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

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

/**
 * @author Dmitry Avdeev
 */
class LazyEditor extends UserDataHolderBase implements Editor {

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

  @NotNull
  public Document getDocument() {
    return getEditor().getDocument();
  }

  public boolean isViewer() {
    return getEditor().isViewer();
  }

  @NotNull
  public JComponent getComponent() {
    return getEditor().getComponent();
  }

  @NotNull
  public JComponent getContentComponent() {
    return getEditor().getContentComponent();
  }

  @Override
  public void setBorder(@Nullable Border border) {
    getEditor().setBorder(border);
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return getEditor().getSelectionModel();
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return getEditor().getMarkupModel();
  }

  @NotNull
  public FoldingModel getFoldingModel() {
    return getEditor().getFoldingModel();
  }

  @NotNull
  public ScrollingModel getScrollingModel() {
    return getEditor().getScrollingModel();
  }

  @NotNull
  public CaretModel getCaretModel() {
    return getEditor().getCaretModel();
  }

  @NotNull
  public SoftWrapModel getSoftWrapModel() {
    return getEditor().getSoftWrapModel();
  }

  @NotNull
  public EditorSettings getSettings() {
    return getEditor().getSettings();
  }

  @NotNull
  public EditorColorsScheme getColorsScheme() {
    return getEditor().getColorsScheme();
  }

  public int getLineHeight() {
    return getEditor().getLineHeight();
  }

  @NotNull
  public Point logicalPositionToXY(@NotNull final LogicalPosition pos) {
    return getEditor().logicalPositionToXY(pos);
  }

  public int logicalPositionToOffset(@NotNull final LogicalPosition pos) {
    return getEditor().logicalPositionToOffset(pos);
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull final LogicalPosition logicalPos) {
    return getEditor().logicalToVisualPosition(logicalPos);
  }

  @NotNull
  public Point visualPositionToXY(@NotNull final VisualPosition visible) {
    return getEditor().visualPositionToXY(visible);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition visiblePos) {
    return getEditor().visualToLogicalPosition(visiblePos);
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset) {
    return getEditor().offsetToLogicalPosition(offset);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(final int offset) {
    return getEditor().offsetToVisualPosition(offset);
  }

  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull final Point p) {
    return getEditor().xyToLogicalPosition(p);
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull final Point p) {
    return getEditor().xyToVisualPosition(p);
  }

  public void addEditorMouseListener(@NotNull final EditorMouseListener listener) {
    getEditor().addEditorMouseListener(listener);
  }

  public void removeEditorMouseListener(@NotNull final EditorMouseListener listener) {
    getEditor().removeEditorMouseListener(listener);
  }

  public void addEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    getEditor().addEditorMouseMotionListener(listener);
  }

  public void removeEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    getEditor().removeEditorMouseMotionListener(listener);
  }

  public boolean isDisposed() {
    return getEditor().isDisposed();
  }

  @Nullable
  public Project getProject() {
    return getEditor().getProject();
  }

  public boolean isInsertMode() {
    return getEditor().isInsertMode();
  }

  public boolean isColumnMode() {
    return getEditor().isColumnMode();
  }

  public boolean isOneLineMode() {
    return getEditor().isOneLineMode();
  }

  @NotNull
  public EditorGutter getGutter() {
    return getEditor().getGutter();
  }

  @Nullable
  public EditorMouseEventArea getMouseEventArea(@NotNull final MouseEvent e) {
    return getEditor().getMouseEventArea(e);
  }

  public void setHeaderComponent(@Nullable final JComponent header) {
    getEditor().setHeaderComponent(header);
  }

  public boolean hasHeaderComponent() {
    return getEditor().hasHeaderComponent();
  }

  @Nullable
  public JComponent getHeaderComponent() {
    return getEditor().getHeaderComponent();
  }

  public IndentsModel getIndentsModel() {
    return getEditor().getIndentsModel();
  }
}
