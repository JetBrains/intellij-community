/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

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
