// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImaginarySelectionModel implements SelectionModel {
  private final ImaginaryEditor myEditor;
  private static final Logger LOG = Logger.getInstance(ImaginarySelectionModel.class);

  public ImaginarySelectionModel(ImaginaryEditor editor) {
    myEditor = editor;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Override
  public @Nullable String getSelectedText(boolean allCarets) {
    return myEditor.getDocument().getText(TextRange.create(getSelectionStart(), getSelectionEnd()));
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener) {
    LOG.info("Called ImaginarySelectionModel#addSelectionListener which is stubbed and has no implementation");
  }

  @Override
  public void removeSelectionListener(@NotNull SelectionListener listener) {
    LOG.info("Called ImaginarySelectionModel#addSelectionListener which is stubbed and has no implementation");
  }

  @Override
  public void copySelectionToClipboard() {
    throw myEditor.notImplemented();
  }

  @Override
  public void setBlockSelection(@NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd) {
    throw myEditor.notImplemented();
  }

  @Override
  public int @NotNull [] getBlockSelectionStarts() {
    return new int[]{myEditor.getSelectionModel().getSelectionStart()};
  }

  @Override
  public int @NotNull [] getBlockSelectionEnds() {
    return new int[]{myEditor.getSelectionModel().getSelectionEnd()};
  }

  @Override
  public TextAttributes getTextAttributes() {
    throw myEditor.notImplemented();
  }
}
