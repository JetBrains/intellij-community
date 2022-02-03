// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.MergingCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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

  private LineSet myOldFragmentLineSet;
  private int myOldFragmentLineSetStart;

  @ApiStatus.Internal
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
      String[] oldLines = getOldLines();
      String[] newLines = Diff.splitLines(myNewString);
      try {
        myChange = Diff.buildChanges(oldLines, newLines);
      }
      catch (FilesTooBigForDiffException e) {
        myChange = TOO_BIG_FILE;
        throw e;
      }
    }
    return myChange;
  }

  private String @NotNull [] getOldLines() {
    createOldFragmentLineSetIfNeeded();
    int offsetDiff = myOffset - myOldFragmentLineSetStart;
    LineIterator lineIterator = myOldFragmentLineSet.createIterator();
    List<String> lines = new ArrayList<>(myOldFragmentLineSet.getLineCount());
    while (!lineIterator.atEnd()) {
      int start = lineIterator.getStart() - offsetDiff;
      int end = lineIterator.getEnd() - lineIterator.getSeparatorLength() - offsetDiff;
      if (start >= 0 && end <= myOldString.length()) {
        lines.add(myOldString.subSequence(start, end).toString());
      }
      lineIterator.advance();
    }
    return lines.isEmpty() ? new String[] {""} : ArrayUtil.toStringArray(lines);
  }


  /**
   * This method is supposed to be called right after the document change, represented by this event instance (e.g. from
   * {@link DocumentListener#documentChanged(DocumentEvent)} callback).
   * Given an offset ({@code offsetBeforeUpdate}), it calculates the line number that would be returned by
   * {@link Document#getLineNumber(int)}, if that call would be performed before the document change.
   */
  public int getLineNumberBeforeUpdate(int offsetBeforeUpdate) {
    createOldFragmentLineSetIfNeeded();
    Document document = getDocument();
    if (offsetBeforeUpdate <= myOldFragmentLineSetStart) {
      return document.getLineNumber(offsetBeforeUpdate);
    }
    int oldFragmentLineSetEnd = myOldFragmentLineSetStart + myOldFragmentLineSet.getLength();
    if (offsetBeforeUpdate <= oldFragmentLineSetEnd) {
      return document.getLineNumber(myOldFragmentLineSetStart) +
             myOldFragmentLineSet.findLineIndex(offsetBeforeUpdate - myOldFragmentLineSetStart);
    }
    int shift = getNewLength() - getOldLength();
    return document.getLineNumber(myOldFragmentLineSetStart) +
           (myOldFragmentLineSetStart == oldFragmentLineSetEnd ? 0 : myOldFragmentLineSet.getLineCount() - 1) +
           document.getLineNumber(offsetBeforeUpdate + shift) - document.getLineNumber(oldFragmentLineSetEnd + shift);
  }

  private void createOldFragmentLineSetIfNeeded() {
    if (myOldFragmentLineSet != null) {
      return;
    }
    CharSequence newText = getDocument().getImmutableCharSequence();
    CharSequence oldFragment = getOldFragment();
    myOldFragmentLineSetStart = getOffset();
    if (myOldFragmentLineSetStart > 0 && newText.charAt(myOldFragmentLineSetStart - 1) == '\r') {
      myOldFragmentLineSetStart--;
      oldFragment = new MergingCharSequence("\r", oldFragment);
    }
    int newChangeEnd = getOffset() + getNewLength();
    if (newChangeEnd < newText.length() && newText.charAt(newChangeEnd) == '\n') {
      oldFragment = new MergingCharSequence(oldFragment, "\n");
    }
    myOldFragmentLineSet = LineSet.createLineSet(oldFragment);
  }
}
