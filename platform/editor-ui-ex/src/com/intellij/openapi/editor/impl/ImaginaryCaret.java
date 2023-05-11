// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImaginaryCaret extends UserDataHolderBase implements Caret {
  private final ImaginaryCaretModel myCaretModel;
  private int myStart, myPos, myEnd;

  public ImaginaryCaret(ImaginaryCaretModel caretModel) {
    myCaretModel = caretModel;
  }

  @Override
  public int getSelectionStart() {
    return myStart;
  }

  @Override
  public int getSelectionEnd() {
    return myEnd;
  }

  @Override
  public boolean hasSelection() {
    return myEnd > myStart;
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
    return myPos;
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    if (offset < 0)
      offset = 0;
    myStart = myPos = myEnd = offset;
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
    if (lineShift == 0) {
      myEnd += columnShift;
      if (!withSelection) {
        myStart = myPos = myEnd;
      }
    }
    else {
      var oldPos = myPos;
      var currentPosition = getLogicalPosition();
      moveToLogicalPosition(new LogicalPosition(currentPosition.line + lineShift, currentPosition.column + columnShift));
      if (withSelection) {
        var newPos = myPos;
        myStart = Math.min(oldPos, newPos);
        myEnd = Math.max(oldPos, newPos);
      }
    }
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    moveToOffset(getEditor().logicalPositionToOffset(pos));
  }

  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    moveToOffset(getEditor().visualPositionToOffset(pos));
  }

  @Override
  public boolean isUpToDate() {
    throw notImplemented();
  }

  @NotNull
  @Override
  public LogicalPosition getLogicalPosition() {
    return getEditor().offsetToLogicalPosition(myStart);
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    return getEditor().offsetToVisualPosition(myStart);
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
    return getEditor().offsetToVisualPosition(myStart);
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    return getEditor().offsetToVisualPosition(myEnd);
  }

  @Nullable
  @Override
  public String getSelectedText() {
    return getEditor().getDocument().getText(new TextRange(myStart, myEnd));
  }

  @Override
  public int getLeadSelectionOffset() {
    return getOffset();
  }

  @NotNull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    throw notImplemented();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    if (startOffset < 0) startOffset = 0;
    if (endOffset < 0) endOffset = 0;
    // mimicking CaretImpl's doSetSelection: removing selection if startOffset == endOffset
    if (startOffset == endOffset) {
      myStart = myPos;
      myEnd = myPos;
      return;
    }

    if (startOffset > endOffset) {
      myStart = endOffset;
      myEnd = startOffset;
    } else {
      myStart = startOffset;
      myEnd = endOffset;
    }
    if (myPos < myStart) {
      myPos = myStart;
    }
    else if (myPos > myEnd) {
      myPos = myEnd;
    }
  }

  @Override
  public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
    setSelection(startOffset, endOffset);
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
    myStart = myPos = myEnd;
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
