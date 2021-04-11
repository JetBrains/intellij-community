// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.MergingCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class is an extension to range marker that tries to restore its range even in situations when target text referenced by it
 * is replaced.
 * <p/>
 * Example: consider that the user selects all text at editor (Ctrl+A), copies it to the buffer (Ctrl+C) and performs paste (Ctrl+V).
 * All document text is replaced then but in essence it's the same, hence, we may want particular range markers to be still valid.
 *
 * @author max
 */
class PersistentRangeMarker extends RangeMarkerImpl {
  @NotNull
  private LinesCols myLinesCols;

  PersistentRangeMarker(@NotNull DocumentEx document, int startOffset, int endOffset, boolean register) {
    super(document, startOffset, endOffset, register, false);
    myLinesCols = Objects.requireNonNull(storeLinesAndCols(document, getStartOffset(), getEndOffset()));
  }

  // constructor which creates marker without document and saves it in the virtual file directly. Can be cheaper than loading document.
  PersistentRangeMarker(@NotNull VirtualFile virtualFile, int startOffset, int endOffset, int startLine, int startCol, int endLine, int endCol, boolean register) {
    super(virtualFile, startOffset, endOffset, register);
    myLinesCols = new LinesCols(startLine, startCol, endLine, endCol);
  }

  @Nullable
  static LinesCols storeLinesAndCols(@NotNull Document myDocument, int startOffset, int endOffset) {
    LineCol start = calcLineCol(myDocument, startOffset);
    LineCol end = calcLineCol(myDocument, endOffset);

    if (start == null || end == null) {
      return null;
    }
    return new LinesCols(start.line, start.col, end.line, end.col);
  }

  private static LineCol calcLineCol(@NotNull Document document, int offset) {
    // document might have been changed already
    if (offset <= document.getTextLength()) {
      int line = document.getLineNumber(offset);
      int col = offset - document.getLineStartOffset(line);
      if (col < 0) {
        return null;
      }
      return new LineCol(line, col);
    }
    return null;
  }

  private static class LineCol {
    private final int line;
    private final int col;

    LineCol(int line, int col) {
      this.line = line;
      this.col = col;
    }
  }
  @Nullable
  static Pair.NonNull<TextRange, LinesCols> translateViaDiff(@NotNull final DocumentEventImpl event, @NotNull LinesCols linesCols) {
    LineSet myOldFragmentLineSet = createOldFragmentLineSet(event);
    int myOldFragmentLineSetStart = event.getOffset();
    CharSequence newText = event.getDocument().getImmutableCharSequence();
    if (myOldFragmentLineSetStart > 0 && newText.charAt(myOldFragmentLineSetStart - 1) == '\r') {
      myOldFragmentLineSetStart--;
    }

    try {
      Diff.Change change = buildDiff(event,myOldFragmentLineSetStart, myOldFragmentLineSet);
      int myStartLine = change == null ? linesCols.myStartLine : translateLineViaDiffStrict(event, linesCols.myStartLine, change);
      Document document = event.getDocument();
      if (myStartLine < 0 || myStartLine >= document.getLineCount()) {
        return null;
      }

      int start = document.getLineStartOffset(myStartLine) + linesCols.myStartColumn;
      if (start >= document.getTextLength()) return null;

      int myEndLine = change == null ? linesCols.myEndLine : translateLineViaDiffStrict(event, linesCols.myEndLine, change);
      if (myEndLine < 0 || myEndLine >= document.getLineCount()) {
        return null;
      }

      int end = document.getLineStartOffset(myEndLine) + linesCols.myEndColumn;
      if (end > document.getTextLength() || end < start) return null;

      if (end > event.getDocument().getTextLength() ||
          myEndLine < myStartLine ||
          myStartLine == myEndLine && linesCols.myEndColumn < linesCols.myStartColumn ||
          event.getDocument().getLineCount() < myEndLine) {
        return null;
      }

      return Pair.createNonNull(new TextRange(start, end), new LinesCols(myStartLine, linesCols.myStartColumn, myEndLine, linesCols.myEndColumn));
    }
    catch (FilesTooBigForDiffException e) {
      return null;
    }
  }

  private static int translateLineViaDiff(@NotNull DocumentEventImpl event, int line, @NotNull Diff.Change change) {
    int startLine = event.getDocument().getLineNumber(event.getOffset());
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

  private static int translateLineViaDiffStrict(@NotNull DocumentEventImpl event, int line, @NotNull Diff.Change change) throws FilesTooBigForDiffException {
    int startLine = event.getDocument().getLineNumber(event.getOffset());
    if (line < startLine) return line;
    int translatedRelative = Diff.translateLine(change, line - startLine);
    return translatedRelative < 0 ? -1 : translatedRelative + startLine;
  }

  private static String @NotNull [] getOldLines(@NotNull DocumentEventImpl event,
                                               int myOldFragmentLineSetStart,
                                               @NotNull LineSet myOldFragmentLineSet) {
    int offsetDiff = event.getOffset() - myOldFragmentLineSetStart;
    LineIterator lineIterator = myOldFragmentLineSet.createIterator();
    List<String> lines = new ArrayList<>(myOldFragmentLineSet.getLineCount());
    while (!lineIterator.atEnd()) {
      int start = lineIterator.getStart() - offsetDiff;
      int end = lineIterator.getEnd() - lineIterator.getSeparatorLength() - offsetDiff;
      if (start >= 0 && end <= event.getOldFragment().length()) {
        lines.add(event.getOldFragment().subSequence(start, end).toString());
      }
      lineIterator.advance();
    }
    return lines.isEmpty() ? new String[] {""} : ArrayUtil.toStringArray(lines);
  }

  // line numbers in Diff.Change are relative to change start
  private static Diff.Change buildDiff(@NotNull DocumentEventImpl event,
                                       int myOldFragmentLineSetStart,
                                       @NotNull LineSet myOldFragmentLineSet) throws FilesTooBigForDiffException {
    String[] oldLines = getOldLines(event, myOldFragmentLineSetStart, myOldFragmentLineSet);
    String[] newLines = Diff.splitLines(event.getNewFragment());
    return Diff.buildChanges(oldLines, newLines);
  }

  static int translateOffsetViaDiff(@NotNull DocumentEventImpl event, int startOffset) throws FilesTooBigForDiffException {
    LineSet myOldFragmentLineSet = createOldFragmentLineSet(event);
    int myOldFragmentLineSetStart = event.getOffset();
    CharSequence newText = event.getDocument().getImmutableCharSequence();
    if (myOldFragmentLineSetStart > 0 && newText.charAt(myOldFragmentLineSetStart - 1) == '\r') {
      myOldFragmentLineSetStart--;
    }
    int line;
    line = getLineNumberBeforeUpdate(event, startOffset, myOldFragmentLineSetStart, myOldFragmentLineSet);
    Diff.Change change = buildDiff(event, myOldFragmentLineSetStart, myOldFragmentLineSet);
    line = change == null ? line : translateLineViaDiff(event, line, change);
    return line;
  }

  @TestOnly
  static int computeLineBeforeUpdateForTest(@NotNull DocumentEventImpl event, int startOffset) {
    LineSet myOldFragmentLineSet = createOldFragmentLineSet(event);
    int myOldFragmentLineSetStart = event.getOffset();
    CharSequence newText = event.getDocument().getImmutableCharSequence();
    if (myOldFragmentLineSetStart > 0 && newText.charAt(myOldFragmentLineSetStart - 1) == '\r') {
      myOldFragmentLineSetStart--;
    }
    int line;
    line = getLineNumberBeforeUpdate(event, startOffset, myOldFragmentLineSetStart, myOldFragmentLineSet);
    return line;
  }
  /**
   * This method is supposed to be called right after the document change, represented by this event instance (e.g. from
   * {@link DocumentListener#documentChanged(DocumentEvent)} callback).
   * Given an offset ({@code offsetBeforeUpdate}), it calculates the line number that would be returned by
   * {@link Document#getLineNumber(int)}, if that call would be performed before the document change.
   */
  private static int getLineNumberBeforeUpdate(@NotNull DocumentEventImpl event,
                                              int offsetBeforeUpdate,
                                              int myOldFragmentLineSetStart,
                                              @NotNull LineSet myOldFragmentLineSet) {
    Document document = event.getDocument();
    if (offsetBeforeUpdate <= myOldFragmentLineSetStart) {
      return document.getLineNumber(offsetBeforeUpdate);
    }
    int oldFragmentLineSetEnd = myOldFragmentLineSetStart + myOldFragmentLineSet.getLength();
    if (offsetBeforeUpdate <= oldFragmentLineSetEnd) {
      return document.getLineNumber(myOldFragmentLineSetStart) +
             myOldFragmentLineSet.findLineIndex(offsetBeforeUpdate - myOldFragmentLineSetStart);
    }
    int shift = event.getNewLength() - event.getOldLength();
    return document.getLineNumber(myOldFragmentLineSetStart) +
           (myOldFragmentLineSetStart == oldFragmentLineSetEnd ? 0 : myOldFragmentLineSet.getLineCount() - 1) +
           document.getLineNumber(offsetBeforeUpdate + shift) - document.getLineNumber(oldFragmentLineSetEnd + shift);
  }

  @NotNull
  private static LineSet createOldFragmentLineSet(@NotNull DocumentEventImpl event) {
    CharSequence newText = event.getDocument().getImmutableCharSequence();
    CharSequence oldFragment = event.getOldFragment();
    int myOldFragmentLineSetStart = event.getOffset();
    if (myOldFragmentLineSetStart > 0 && newText.charAt(myOldFragmentLineSetStart - 1) == '\r') {
      oldFragment = new MergingCharSequence("\r", oldFragment);
    }
    int newChangeEnd = event.getOffset() + event.getNewLength();
    if (newChangeEnd < newText.length() && newText.charAt(newChangeEnd) == '\n') {
      oldFragment = new MergingCharSequence(oldFragment, "\n");
    }
    return LineSet.createLineSet(oldFragment);
  }


  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    if (!isValid()) return;

    Pair<TextRange, LinesCols> pair =
      applyChange(e, this, intervalStart(), intervalEnd(), isGreedyToLeft(), isGreedyToRight(), isStickingToRight(), myLinesCols);
    if (pair == null) {
      invalidate(e);
      return;
    }

    setIntervalStart(pair.first.getStartOffset());
    setIntervalEnd(pair.first.getEndOffset());
    myLinesCols = pair.second;
  }

  @Nullable
  private static Pair.NonNull<TextRange, LinesCols> applyChange(@NotNull DocumentEvent event,
                                                                @NotNull Segment range,
                                                                int intervalStart, int intervalEnd,
                                                                boolean greedyLeft, boolean greedyRight, boolean stickingToRight,
                                                                @NotNull LinesCols linesCols) {
    boolean shouldTranslateViaDiff = PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, range.getStartOffset(), range.getEndOffset());
    Pair.NonNull<TextRange, LinesCols> translated = null;
    if (shouldTranslateViaDiff) {
      translated = translateViaDiff((DocumentEventImpl)event, linesCols);
    }
    if (translated == null) {
      TextRange fallback = applyChange(event, intervalStart, intervalEnd, greedyLeft, greedyRight, stickingToRight);
      if (fallback == null) return null;

      LinesCols lc = storeLinesAndCols(event.getDocument(), fallback.getStartOffset(), fallback.getEndOffset());
      if (lc == null) return null;

      translated = Pair.createNonNull(fallback, lc);
    }
    return translated;
  }

  @Override
  public String toString() {
    return "PersistentRangeMarker" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() +
           " " + myLinesCols +
           (isGreedyToRight() ? "]" : ")");
  }

  static final class LinesCols {
    private final int myStartLine;
    private final int myStartColumn;
    private final int myEndLine;
    private final int myEndColumn;

    private LinesCols(int startLine, int startColumn, int endLine, int endColumn) {
      myStartLine = startLine;
      myStartColumn = startColumn;
      myEndLine = endLine;
      myEndColumn = endColumn;
    }

    @Override
    public String toString() {
      return myStartLine + ":" + myStartColumn + "-" + myEndLine + ":" + myEndColumn;
    }
  }

}
