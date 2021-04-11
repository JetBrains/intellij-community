// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

public class DocumentEventImpl extends DocumentEvent {
  private final int myOffset;
  @NotNull
  private final CharSequence myOldString;
  private final int myOldLength;
  @NotNull
  private final CharSequence myNewString;
  private final int myNewLength;

  private final long myOldTimeStamp;
  private final boolean myIsWholeDocReplaced;

  private final int myInitialStartOffset;
  private final int myInitialOldLength;
  private final int myMoveOffset;

  public DocumentEventImpl(@NotNull Document document,
                           int offset,
                           @NotNull CharSequence oldString,
                           @NotNull CharSequence newString,
                           long oldTimeStamp,
                           boolean wholeTextReplaced,
                           int initialStartOffset,
                           int initialOldLength,
                           int moveOffset) {
    super(document);
    myOffset = offset;

    myOldString = oldString;
    myOldLength = oldString.length();

    myNewString = newString;
    myNewLength = newString.length();

    myInitialStartOffset = initialStartOffset;
    myInitialOldLength = initialOldLength;
    myMoveOffset = moveOffset;

    myOldTimeStamp = oldTimeStamp;

    myIsWholeDocReplaced = getDocument().getTextLength() != 0 && wholeTextReplaced;
    assert initialStartOffset >= 0 : initialStartOffset;
    assert initialOldLength >= 0 : initialOldLength;
    assert moveOffset == offset || myOldLength == 0 || myNewLength == 0 : this;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getOldLength() {
    return myOldLength;
  }

  @Override
  public int getNewLength() {
    return myNewLength;
  }

  @NotNull
  @Override
  public CharSequence getOldFragment() {
    return myOldString;
  }

  @NotNull
  @Override
  public CharSequence getNewFragment() {
    return myNewString;
  }

  /**
   * @return initial start offset as requested in {@link Document#replaceString(int, int, CharSequence)} call, before common prefix and
   * suffix were removed from the changed range.
   */
  public int getInitialStartOffset() {
    return myInitialStartOffset;
  }

  /**
   * @return initial "old fragment" length (endOffset - startOffset) as requested in {@link Document#replaceString(int, int, CharSequence)} call, before common prefix and
   * suffix were removed from the changed range.
   */
  public int getInitialOldLength() {
    return myInitialOldLength;
  }

  @Override
  public int getMoveOffset() {
    return myMoveOffset;
  }

  @Override
  public long getOldTimeStamp() {
    return myOldTimeStamp;
  }

  @Override
  public String toString() {
    return "DocumentEventImpl[myOffset=" + myOffset + ", myOldLength=" + myOldLength + ", myNewLength=" + myNewLength +
           "]" + (isWholeTextReplaced() ? " Whole." : ".");
  }

  @Override
  public boolean isWholeTextReplaced() {
    return myIsWholeDocReplaced;
  }
}
