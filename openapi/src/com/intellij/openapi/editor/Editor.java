/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface Editor extends UserDataHolder {
  @NotNull Document getDocument();
  boolean isViewer();

  @NotNull JComponent getComponent();
  @NotNull JComponent getContentComponent();

  @NotNull SelectionModel getSelectionModel();
  @NotNull MarkupModel getMarkupModel();
  @NotNull FoldingModel getFoldingModel();
  @NotNull ScrollingModel getScrollingModel();
  @NotNull CaretModel getCaretModel();

  @NotNull EditorSettings getSettings();
  @NotNull EditorColorsScheme getColorsScheme();

  int getLineHeight();

  @NotNull Point logicalPositionToXY(@NotNull LogicalPosition pos);
  int logicalPositionToOffset(@NotNull LogicalPosition pos);
  @NotNull VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos);

  @NotNull Point visualPositionToXY(@NotNull VisualPosition visible);
  @NotNull LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos);

  @NotNull LogicalPosition offsetToLogicalPosition(int offset);
  @NotNull VisualPosition offsetToVisualPosition(int offset);

  @NotNull LogicalPosition xyToLogicalPosition(@NotNull Point p);
  @NotNull VisualPosition xyToVisualPosition(@NotNull Point p);

  void addEditorMouseListener(@NotNull EditorMouseListener listener);
  void removeEditorMouseListener(@NotNull EditorMouseListener listener);

  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);
  void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  boolean isDisposed();

  @Nullable Project getProject();

  boolean isInsertMode();

  boolean isColumnMode();

  boolean isBlockSelectionMode();

  boolean isOneLineMode();

  @NotNull EditorGutter getGutter();

  @Nullable EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e);
}
