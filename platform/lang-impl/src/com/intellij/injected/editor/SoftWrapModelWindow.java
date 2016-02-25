/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.injected.editor;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.SoftWrapChangeListener;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

class SoftWrapModelWindow implements SoftWrapModelEx {
  private final EditorWindowImpl myEditorWindow;

  SoftWrapModelWindow(EditorWindowImpl editorWindow) {
    myEditorWindow = editorWindow;
  }

  @NotNull
  @Override
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visual) {
    return myEditorWindow.visualToLogicalPosition(visual);
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return myEditorWindow.offsetToLogicalPosition(offset);
  }

  @Override
  public int logicalPositionToOffset(@NotNull LogicalPosition logicalPosition) {
    return myEditorWindow.logicalPositionToOffset(logicalPosition);
  }

  @NotNull
  @Override
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    return defaultVisual;
  }

  @Override
  public List<? extends SoftWrap> getRegisteredSoftWraps() {
    return Collections.emptyList();
  }

  @Override
  public int getSoftWrapIndex(int offset) {
    return -1;
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    return 0;
  }

  @Override
  public int getMinDrawingWidthInPixels(@NotNull SoftWrapDrawingType drawingType) {
    return 0;
  }

  @Override
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return false;
  }

  @Override
  public boolean isRespectAdditionalColumns() {
    return false;
  }

  @Override
  public void forceAdditionalColumnsUsage() {
  }

  @Override
  public EditorTextRepresentationHelper getEditorTextRepresentationHelper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSoftWrappingEnabled() {
    return false;
  }

  @Nullable
  @Override
  public SoftWrap getSoftWrap(int offset) {
    return null;
  }

  @NotNull
  @Override
  public List<? extends SoftWrap> getSoftWrapsForRange(int start, int end) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends SoftWrap> getSoftWrapsForLine(int documentLine) {
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
