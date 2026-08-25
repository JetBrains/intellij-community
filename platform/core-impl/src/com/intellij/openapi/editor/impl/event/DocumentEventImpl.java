// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentLineDiff;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class DocumentEventImpl extends DocumentEvent {
  private final int myOffset;
  private final @NotNull CharSequence myOldString;
  private final int myOldLength;
  private final @NotNull CharSequence myNewString;
  private final int myNewLength;

  private final long myOldTimeStamp;
  private final boolean myIsWholeDocReplaced;
  private final @NotNull DocumentLineDiff myLineDiff;

  private final int myInitialStartOffset;
  private final int myInitialOldLength;
  private final int myMoveOffset;

  @ApiStatus.Internal
  public DocumentEventImpl(@NotNull Document document,
                           int offset,
                           @NotNull CharSequence oldString,
                           @NotNull CharSequence newString,
                           long oldTimeStamp,
                           boolean wholeTextReplaced,
                           int initialStartOffset,
                           int initialOldLength,
                           int moveOffset,
                           int textLength) {
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

    myIsWholeDocReplaced = textLength != 0 && wholeTextReplaced;
    myLineDiff = new DocumentLineDiff(offset, oldString, newString);
    assert initialStartOffset >= 0 : initialStartOffset;
    assert initialOldLength >= 0 : initialOldLength;
    assert moveOffset == offset || myOldLength == 0 || myNewLength == 0 : this;
    assert getOldFragment().length() ==  getOldLength() : "event.getOldFragment().length() = " + getOldFragment().length()+"; event.getOldLength() = " + getOldLength();
    assert getNewFragment().length() ==  getNewLength() : "event.getNewFragment().length() = " + getNewFragment().length()+"; event.getNewLength() = " + getNewLength();
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

  @Override
  public @NotNull CharSequence getOldFragment() {
    return myOldString;
  }

  @Override
  public @NotNull CharSequence getNewFragment() {
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
           "]" + (isWholeTextReplaced() ? " Whole" : "");
  }

  @Override
  public boolean isWholeTextReplaced() {
    return myIsWholeDocReplaced;
  }

  @ApiStatus.Internal
  public @NotNull DocumentLineDiff getLineDiff() {
    return myLineDiff;
  }

  public int translateLineViaDiff(int line) throws FilesTooBigForDiffException {
    Document document = getDocument();
    int startLine = document.getLineNumber(myLineDiff.getChangeStartOffset());
    return myLineDiff.translateLine(line, startLine, document.getImmutableCharSequence());
  }

  public int translateLineViaDiffStrict(int line) throws FilesTooBigForDiffException {
    Document document = getDocument();
    int startLine = document.getLineNumber(myLineDiff.getChangeStartOffset());
    return myLineDiff.translateLineStrict(line, startLine, document.getImmutableCharSequence());
  }


  /**
   * This method is supposed to be called right after the document change, represented by this event instance (e.g. from
   * {@link DocumentListener#documentChanged(DocumentEvent)} callback).
   * Given an offset ({@code offsetBeforeUpdate}), it calculates the line number that would be returned by
   * {@link Document#getLineNumber(int)}, if that call would be performed before the document change.
   */
  public int getLineNumberBeforeUpdate(int offsetBeforeUpdate) {
    Document document = getDocument();
    CharSequence afterText = document.getImmutableCharSequence();
    LineSet oldFragmentLineSet = myLineDiff.getOldFragmentLineSet(afterText);
    int oldFragmentLineSetStart = myLineDiff.getOldFragmentLineSetStart(afterText);
    if (offsetBeforeUpdate <= oldFragmentLineSetStart) {
      return document.getLineNumber(offsetBeforeUpdate);
    }
    int oldFragmentLineSetEnd = oldFragmentLineSetStart + oldFragmentLineSet.getLength();
    if (offsetBeforeUpdate <= oldFragmentLineSetEnd) {
      return document.getLineNumber(oldFragmentLineSetStart) +
             oldFragmentLineSet.findLineIndex(offsetBeforeUpdate - oldFragmentLineSetStart);
    }
    int shift = getNewLength() - getOldLength();
    return document.getLineNumber(oldFragmentLineSetStart) +
           (oldFragmentLineSetStart == oldFragmentLineSetEnd ? 0 : oldFragmentLineSet.getLineCount() - 1) +
           document.getLineNumber(offsetBeforeUpdate + shift) - document.getLineNumber(oldFragmentLineSetEnd + shift);
  }
}
