// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
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
  private Diff.Change myChange;
  private static final Diff.Change TOO_BIG_FILE = new Diff.Change(0, 0, 0, 0, null);

  private final int myInitialStartOffset;
  private final int myInitialOldLength;
  private final int myMoveOffset;

  public DocumentEventImpl(@NotNull Document document,
                           int offset,
                           @NotNull CharSequence oldString,
                           @NotNull CharSequence newString,
                           long oldTimeStamp,
                           boolean wholeTextReplaced) {
    this(document, offset, oldString, newString, oldTimeStamp, wholeTextReplaced, offset, oldString.length(), offset);
  }

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
    assert moveOffset == offset || myOldLength == 0 || myNewLength == 0;
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

  public int translateLineViaDiff(int line) throws FilesTooBigForDiffException {
    Diff.Change change = reBuildDiffIfNeeded();
    if (change == null) return line;

    int startLine = getDocument().getLineNumber(getOffset());
    line -= startLine;
    int newLine = line;

    while (change != null) {
      if (line < change.line0) break;
      if (line >= change.line0 + change.deleted) {
        newLine += change.inserted - change.deleted;
      }
      else {
        int delta = Math.min(change.inserted, line - change.line0);
        newLine = change.line1 + delta;
        break;
      }

      change = change.link;
    }

    return newLine + startLine;
  }

  public int translateLineViaDiffStrict(int line) throws FilesTooBigForDiffException {
    Diff.Change change = reBuildDiffIfNeeded();
    if (change == null) return line;
    int startLine = getDocument().getLineNumber(getOffset());
    if (line < startLine) return line;
    int translatedRelative = Diff.translateLine(change, line - startLine);
    return translatedRelative < 0 ? -1 : translatedRelative + startLine;
  }

  // line numbers in Diff.Change are relative to change start
  private Diff.Change reBuildDiffIfNeeded() throws FilesTooBigForDiffException {
    if (myChange == TOO_BIG_FILE) throw new FilesTooBigForDiffException();
    if (myChange == null) {
      try {
        myChange = Diff.buildChanges(myOldString, myNewString);
      }
      catch (FilesTooBigForDiffException e) {
        myChange = TOO_BIG_FILE;
        throw e;
      }
    }
    return myChange;
  }
}
