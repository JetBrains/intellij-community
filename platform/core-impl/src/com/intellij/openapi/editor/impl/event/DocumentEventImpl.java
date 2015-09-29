/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

public class DocumentEventImpl extends DocumentEvent {
  private final int myOffset;
  private final CharSequence myOldString;
  private final int myOldLength;
  private final CharSequence myNewString;
  private final int myNewLength;

  private boolean isOnlyOneLineChangedCalculated = false;
  private boolean isOnlyOneLineChanged;

  private boolean isStartOldIndexCalculated = false;
  private int myStartOldIndex;

  private final long myOldTimeStamp;
  private final boolean myIsWholeDocReplaced;
  private Diff.Change myChange;
  private static final Diff.Change TOO_BIG_FILE = new Diff.Change(0, 0, 0, 0, null) {
  };

  private int myOptimizedLineShift = -1;
  private boolean myOptimizedLineShiftCalculated;

  private int myOptimizedOldLineShift = -1;
  private boolean myOptimizedOldLineShiftCalculated;
  private final int myInitialStartOffset;
  private final int myInitialOldLength;

  public DocumentEventImpl(@NotNull Document document,
                           int offset,
                           CharSequence oldString,
                           CharSequence newString,
                           long oldTimeStamp,
                           boolean wholeTextReplaced) {
    this(document, offset, oldString, newString, oldTimeStamp, wholeTextReplaced, offset, oldString == null ? 0 : oldString.length());
  }
  public DocumentEventImpl(@NotNull Document document,
                           int offset,
                           CharSequence oldString,
                           CharSequence newString,
                           long oldTimeStamp,
                           boolean wholeTextReplaced,
                           int initialStartOffset,
                           int initialOldLength) {
    super(document);
    myOffset = offset;

    myOldString = oldString == null ? "" : oldString;
    myOldLength = myOldString.length();

    myNewString = newString == null ? "" : newString;
    myNewLength = myNewString.length();

    myInitialStartOffset = initialStartOffset;
    myInitialOldLength = initialOldLength;

    myOldTimeStamp = oldTimeStamp;

    if (getDocument().getTextLength() == 0) {
      isOnlyOneLineChangedCalculated = true;
      isOnlyOneLineChanged = false;
      myIsWholeDocReplaced = false;
    }
    else {
      myIsWholeDocReplaced = wholeTextReplaced;
    }
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

  @Override
  @NotNull
  public Document getDocument() {
    return (Document)getSource();
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

  public int getStartOldIndex() {
    if (isStartOldIndexCalculated) return myStartOldIndex;

    isStartOldIndexCalculated = true;
    myStartOldIndex = getDocument().getLineNumber(myOffset);
    return myStartOldIndex;
  }

  public boolean isOnlyOneLineChanged() {
    if (isOnlyOneLineChangedCalculated) return isOnlyOneLineChanged;

    isOnlyOneLineChangedCalculated = true;
    isOnlyOneLineChanged = true;

    for (int i = 0; i < myOldString.length(); i++) {
      if (myOldString.charAt(i) == '\n') {
        isOnlyOneLineChanged = false;
        break;
      }
    }

    if (isOnlyOneLineChanged) {
      for (int i = 0; i < myNewString.length(); i++) {
        if (myNewString.charAt(i) == '\n') {
          isOnlyOneLineChanged = false;
          break;
        }
      }
    }
    return isOnlyOneLineChanged;
  }

  @Override
  public long getOldTimeStamp() {
    return myOldTimeStamp;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DocumentEventImpl[myOffset=" + myOffset + ", myOldLength=" + myOldLength + ", myNewLength=" + myNewLength +
           ", myOldString='" + myOldString + "', myNewString='" + myNewString + "']" + (isWholeTextReplaced() ? " Whole." : ".");
  }

  @Override
  public boolean isWholeTextReplaced() {
    return myIsWholeDocReplaced;
  }

  public int translateLineViaDiff(int line) throws FilesTooBigForDiffException {
    Diff.Change change = reBuildDiffIfNeeded();
    if (change == null) return line;

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

    return newLine;
  }

  public int translateLineViaDiffStrict(int line) throws FilesTooBigForDiffException {
    Diff.Change change = reBuildDiffIfNeeded();
    if (change == null) return line;
    return Diff.translateLine(change, line);
  }

  private Diff.Change reBuildDiffIfNeeded() throws FilesTooBigForDiffException {
    if (myChange == TOO_BIG_FILE) throw new FilesTooBigForDiffException(0);
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

  public int getOptimizedLineShift() {
    if (!myOptimizedLineShiftCalculated) {
      myOptimizedLineShiftCalculated = true;

      if (myOldLength == 0) {
        int lineShift = StringUtil.countNewLines(myNewString);

        myOptimizedLineShift = lineShift == 0 ? -1 : lineShift;
      }
    }
    return myOptimizedLineShift;
  }

  public int getOptimizedOldLineShift() {
    if (!myOptimizedOldLineShiftCalculated) {
      myOptimizedOldLineShiftCalculated = true;

      if (myNewLength == 0) {
        int lineShift = StringUtil.countNewLines(myOldString);

        myOptimizedOldLineShift = lineShift == 0 ? -1 : lineShift;
      }
    }
    return myOptimizedOldLineShift;
  }
}
