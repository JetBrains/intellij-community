/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;

import javax.swing.*;
import java.awt.*;

public interface Editor extends UserDataHolder {
  Document getDocument();
  boolean isViewer();

  JComponent getComponent();
  JComponent getContentComponent();

  SelectionModel getSelectionModel();
  MarkupModel getMarkupModel();
  FoldingModel getFoldingModel();
  ScrollingModel getScrollingModel();
  CaretModel getCaretModel();

  EditorSettings getSettings();
  EditorColorsScheme getColorsScheme();

  int getLineHeight();

  Point logicalPositionToXY(LogicalPosition pos);
  int logicalPositionToOffset(LogicalPosition pos);
  VisualPosition logicalToVisualPosition(LogicalPosition logicalPos);

  Point visualPositionToXY(VisualPosition visible);
  LogicalPosition visualToLogicalPosition(VisualPosition visiblePos);

  LogicalPosition offsetToLogicalPosition(int offset);
  VisualPosition offsetToVisualPosition(int offset);

  LogicalPosition xyToLogicalPosition(Point p);
  VisualPosition xyToVisualPosition(Point p);

  void addEditorMouseListener(EditorMouseListener listener);
  void removeEditorMouseListener(EditorMouseListener listener);

  void addEditorMouseMotionListener(EditorMouseMotionListener listener);
  void removeEditorMouseMotionListener(EditorMouseMotionListener listener);

  boolean isDisposed();

  Project getProject();

  boolean isInsertMode();

  boolean isColumnMode();

  boolean isBlockSelectionMode();

  boolean isOneLineMode();

  EditorGutter getGutter();
}
