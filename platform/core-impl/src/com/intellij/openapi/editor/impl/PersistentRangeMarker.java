// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private volatile boolean documentLoaded;

  PersistentRangeMarker(@NotNull DocumentEx document, int startOffset, int endOffset, boolean register) {
    super(document, startOffset, endOffset, register, false);
    myLinesCols = Objects.requireNonNull(storeLinesAndCols(document, getStartOffset(), getEndOffset()));
    documentLoaded = true;
  }

  // constructor which creates marker without document and saves it in the virtual file directly. Can be cheaper than loading document.
  PersistentRangeMarker(@NotNull VirtualFile virtualFile,
                        int startOffset,
                        int endOffset,
                        int startLine,
                        int startCol,
                        int endLine,
                        int endCol,
                        int estimatedDocumentLength,
                        boolean register) {
    super(virtualFile, startOffset, endOffset, estimatedDocumentLength, register);
    myLinesCols = new LinesCols(startLine, startCol, endLine, endCol);
    documentLoaded = FileDocumentManager.getInstance().getCachedDocument(virtualFile) != null;
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
    try {
      int myStartLine = event.translateLineViaDiffStrict(linesCols.myStartLine);
      Document document = event.getDocument();
      if (myStartLine < 0 || myStartLine >= document.getLineCount()) {
        return null;
      }

      int start = document.getLineStartOffset(myStartLine) + linesCols.myStartColumn;
      if (start >= document.getTextLength()) return null;

      int myEndLine = event.translateLineViaDiffStrict(linesCols.myEndLine);
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
  private static Pair.NonNull<TextRange, LinesCols> applyChange(@NotNull DocumentEvent event, @NotNull Segment range,
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

  @Override
  public int getStartOffset() {
    // load document in case this is a lazy persistent marker and the document wasn't loaded yet, because we need to convert (line;col) to offset first in this case
    if (!isDocumentLoaded()) {
      ReadAction.run(()->getDocument());
    }
    return super.getStartOffset();
  }

  @Override
  public int getEndOffset() {
    // load document in case this is a lazy persistent marker and the document wasn't loaded yet, because we need to convert (line;col) to offset first in this case
    if (!isDocumentLoaded()) {
      ReadAction.run(()->getDocument());
    }
    return super.getEndOffset();
  }

  @Override
  void reRegister(@NotNull DocumentImpl document, int tabSize) {
    // have to convert line/col back to offset if the persistent range marker was created with line/col only
    LinesCols linesCols = myLinesCols;
    int startOffset = DocumentUtil.calculateOffset(document, linesCols.myStartLine, linesCols.myStartColumn, tabSize);
    int endOffset = DocumentUtil.calculateOffset(document, linesCols.myEndLine, linesCols.myEndColumn, tabSize);
    document.registerRangeMarker(this, startOffset, endOffset, isGreedyToLeft(), isGreedyToRight(), 0);
    documentLoaded = true;
  }
  private boolean isDocumentLoaded() {
    return documentLoaded;
  }
}
