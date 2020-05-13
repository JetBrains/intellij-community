// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ImaginarySelectionModel implements SelectionModel {
  private final ImaginaryEditor myEditor;

  ImaginarySelectionModel(ImaginaryEditor editor) {
    myEditor = editor;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Nullable
  @Override
  public String getSelectedText(boolean allCarets) {
    return null;
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener) {
    throw myEditor.notImplemented();
  }

  @Override
  public void removeSelectionListener(@NotNull SelectionListener listener) {
    throw myEditor.notImplemented();
  }

  @Override
  public void copySelectionToClipboard() {
    throw myEditor.notImplemented();
  }

  @Override
  public void setBlockSelection(@NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd) {
    throw myEditor.notImplemented();
  }

  @NotNull
  @Override
  public int[] getBlockSelectionStarts() {
    throw myEditor.notImplemented();
  }

  @NotNull
  @Override
  public int[] getBlockSelectionEnds() {
    throw myEditor.notImplemented();
  }

  @Override
  public TextAttributes getTextAttributes() {
    throw myEditor.notImplemented();
  }
}
