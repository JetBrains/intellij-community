// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ImaginaryCaret extends UserDataHolderBase implements Caret {
  private final ImaginaryCaretModel myCaretModel;
  private int myOffset;

  ImaginaryCaret(ImaginaryCaretModel caretModel) {
    myCaretModel = caretModel;
  }

  @Override
  public int getSelectionStart() {
    return myOffset;
  }

  @Override
  public int getSelectionEnd() {
    return myOffset;
  }

  @Override
  public boolean hasSelection() {
    return false;
  }

  @NotNull
  @Override
  public ImaginaryEditor getEditor() {
    return myCaretModel.getEditor();
  }

  @NotNull
  @Override
  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public void moveToOffset(int offset) {
    myOffset = offset;
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    myOffset = offset;
  }



  private RuntimeException notImplemented() {
    return getEditor().notImplemented();
  }

  @Override
  public boolean isValid() {
    throw notImplemented();
  }

  @Override
  public void moveCaretRelatively(int columnShift, int lineShift, boolean withSelection, boolean scrollToCaret) {
    throw notImplemented();
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    throw notImplemented();
  }

  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    throw notImplemented();
  }

  @Override
  public boolean isUpToDate() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public LogicalPosition getLogicalPosition() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    throw notImplemented();
  }

  @Override
  public int getVisualLineStart() {
    throw notImplemented();
  }

  @Override
  public int getVisualLineEnd() {
    throw notImplemented();
  }
  @NotNull
  @Override
  public VisualPosition getSelectionStartPosition() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    throw notImplemented();
  }

  @Nullable
  @Override
  public String getSelectedText() {
    throw notImplemented();
  }

  @Override
  public int getLeadSelectionOffset() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    throw notImplemented();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    throw notImplemented();
  }

  @Override
  public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
    throw notImplemented();
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    throw notImplemented();
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    throw notImplemented();
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition,
                           int startOffset,
                           @Nullable VisualPosition endPosition,
                           int endOffset,
                           boolean updateSystemSelection) {
    throw notImplemented();
  }

  @Override
  public void removeSelection() {
    throw notImplemented();
  }

  @Override
  public void selectLineAtCaret() {
    throw notImplemented();
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    throw notImplemented();
  }

  @Nullable
  @Override
  public Caret clone(boolean above) {
    throw notImplemented();
  }

  @Override
  public boolean isAtRtlLocation() {
    throw notImplemented();
  }

  @Override
  public boolean isAtBidiRunBoundary() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public CaretVisualAttributes getVisualAttributes() {
    throw notImplemented();
  }

  @Override
  public void setVisualAttributes(@NotNull CaretVisualAttributes attributes) {
    throw notImplemented();
  }

  @Override
  public void dispose() {
    throw notImplemented();
  }

}
