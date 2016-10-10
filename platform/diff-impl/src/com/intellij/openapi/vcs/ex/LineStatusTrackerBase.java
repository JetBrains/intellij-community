/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.openapi.localVcs.UpToDateLineNumberProvider.ABSENT_LINE_NUMBER;

@SuppressWarnings({"MethodMayBeStatic", "FieldAccessedSynchronizedAndUnsynchronized"})
public abstract class LineStatusTrackerBase {
  public static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker");

  // all variables should be modified in EDT and under LOCK
  // read access allowed from EDT or while holding LOCK
  private final Object LOCK = new Object();

  @Nullable protected final Project myProject;
  @NotNull protected final Document myDocument;
  @NotNull protected final Document myVcsDocument;

  @NotNull protected final Application myApplication;

  @NotNull protected final MyDocumentListener myDocumentListener;
  @NotNull protected final ApplicationAdapter myApplicationListener;

  private boolean myInitialized;
  private boolean myDuringRollback;
  private boolean myBulkUpdate;
  private boolean myAnathemaThrown;
  private boolean myReleased;

  @NotNull private List<Range> myRanges;

  @Nullable private DirtyRange myDirtyRange;

  public LineStatusTrackerBase(@Nullable final Project project,
                               @NotNull final Document document) {
    myDocument = document;
    myProject = project;

    myApplication = ApplicationManager.getApplication();

    myDocumentListener = new MyDocumentListener();
    myDocument.addDocumentListener(myDocumentListener);

    myApplicationListener = new MyApplicationListener();
    myApplication.addApplicationListener(myApplicationListener);

    myRanges = new ArrayList<>();

    myVcsDocument = new DocumentImpl("", true);
    myVcsDocument.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected abstract void createHighlighter(@NotNull Range range);

  @CalledInAwt
  protected boolean isDetectWhitespaceChangedLines() {
    return false;
  }

  @CalledInAwt
  protected void installNotification(@NotNull String text) {
  }

  @CalledInAwt
  protected void destroyNotification() {
  }

  @CalledInAwt
  protected void fireFileUnchanged() {
  }

  //
  // Impl
  //

  @CalledInAwt
  public void setBaseRevision(@NotNull final CharSequence vcsContent) {
    myApplication.assertIsDispatchThread();
    if (myReleased) return;

    synchronized (LOCK) {
      try {
        myVcsDocument.setReadOnly(false);
        myVcsDocument.setText(vcsContent);
        myVcsDocument.setReadOnly(true);
      }
      finally {
        myInitialized = true;
      }

      reinstallRanges();
    }
  }

  @CalledInAwt
  protected void reinstallRanges() {
    if (!myInitialized || myReleased || myBulkUpdate) return;

    synchronized (LOCK) {
      destroyRanges();
      try {
        myRanges = RangesBuilder.createRanges(myDocument, myVcsDocument, isDetectWhitespaceChangedLines());
        for (final Range range : myRanges) {
          createHighlighter(range);
        }

        if (myRanges.isEmpty()) {
          fireFileUnchanged();
        }
      }
      catch (FilesTooBigForDiffException e) {
        installAnathema();
      }
    }
  }

  @CalledInAwt
  private void destroyRanges() {
    removeAnathema();
    for (Range range : myRanges) {
      disposeHighlighter(range);
    }
    myRanges = Collections.emptyList();
    myDirtyRange = null;
  }

  @CalledInAwt
  private void installAnathema() {
    myAnathemaThrown = true;
    installNotification("Can not highlight changed lines. File is too big and there are too many changes.");
  }

  @CalledInAwt
  private void removeAnathema() {
    if (!myAnathemaThrown) return;
    myAnathemaThrown = false;
    destroyNotification();
  }

  @CalledInAwt
  private void disposeHighlighter(@NotNull Range range) {
    try {
      range.invalidate();
      RangeHighlighter highlighter = range.getHighlighter();
      if (highlighter != null) {
        range.setHighlighter(null);
        highlighter.dispose();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private boolean tryValidate() {
    if (myApplication.isDispatchThread()) updateRanges();
    return isValid();
  }

  public boolean isOperational() {
    synchronized (LOCK) {
      return myInitialized && !myReleased;
    }
  }

  public boolean isValid() {
    synchronized (LOCK) {
      return !isSuppressed() && myDirtyRange == null;
    }
  }

  private boolean isSuppressed() {
    return !myInitialized || myReleased || myAnathemaThrown || myBulkUpdate || myDuringRollback;
  }

  public void release() {
    Runnable runnable = () -> {
      if (myReleased) return;
      LOG.assertTrue(!myDuringRollback);

      synchronized (LOCK) {
        myReleased = true;
        myDocument.removeDocumentListener(myDocumentListener);
        myApplication.removeApplicationListener(myApplicationListener);

        destroyRanges();
      }
    };

    if (myApplication.isDispatchThread() && !myDuringRollback) {
      runnable.run();
    }
    else {
      myApplication.invokeLater(runnable);
    }
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public Document getVcsDocument() {
    return myVcsDocument;
  }

  /**
   * Ranges can be modified without taking the write lock, so calling this method twice not from EDT can produce different results.
   */
  @Nullable
  public List<Range> getRanges() {
    synchronized (LOCK) {
      if (!tryValidate()) return null;
      myApplication.assertReadAccessAllowed();

      List<Range> result = new ArrayList<>(myRanges.size());
      for (Range range : myRanges) {
        result.add(new Range(range));
      }
      return result;
    }
  }

  @NotNull
  @TestOnly
  public List<Range> getRangesInner() {
    return myRanges;
  }

  @CalledInAwt
  public void startBulkUpdate() {
    if (myReleased) return;
    synchronized (LOCK) {
      myBulkUpdate = true;
      destroyRanges();
    }
  }

  @CalledInAwt
  public void finishBulkUpdate() {
    if (myReleased) return;
    synchronized (LOCK) {
      myBulkUpdate = false;
      reinstallRanges();
    }
  }

  @CalledInAwt
  private void updateRanges() {
    if (isSuppressed()) return;
    if (myDirtyRange != null) {
      synchronized (LOCK) {
        try {
          doUpdateRanges(myDirtyRange.line1, myDirtyRange.line2, myDirtyRange.lineShift, myDirtyRange.beforeTotalLines);
          myDirtyRange = null;
        }
        catch (Exception e) {
          LOG.error(e);
          reinstallRanges();
        }
      }
    }
  }

  private class MyApplicationListener extends ApplicationAdapter {
    @Override
    public void afterWriteActionFinished(@NotNull Object action) {
      updateRanges();
    }
  }

  private class MyDocumentListener extends DocumentAdapter {
    /*
     *   beforeWriteLock   beforeChange     Current
     *              |            |             |
     *              |            | line1       |
     * updatedLine1 +============+-------------+ newLine1
     *              |            |             |
     *      r.line1 +------------+ oldLine1    |
     *              |            |             |
     *              |     old    |             |
     *              |    dirty   |             |
     *              |            | oldLine2    |
     *      r.line2 +------------+         ----+ newLine2
     *              |            |        /    |
     * updatedLine2 +============+--------     |
     *                            line2
     */

    private int myLine1;
    private int myLine2;
    private int myBeforeTotalLines;

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      if (isSuppressed()) return;
      assert myDocument == e.getDocument();

      myLine1 = myDocument.getLineNumber(e.getOffset());
      if (e.getOldLength() == 0) {
        myLine2 = myLine1 + 1;
      }
      else {
        myLine2 = myDocument.getLineNumber(e.getOffset() + e.getOldLength()) + 1;
      }

      myBeforeTotalLines = getLineCount(myDocument);
    }

    @Override
    public void documentChanged(final DocumentEvent e) {
      myApplication.assertIsDispatchThread();

      if (isSuppressed()) return;
      assert myDocument == e.getDocument();

      synchronized (LOCK) {
        int newLine1 = myLine1;
        int newLine2;
        if (e.getNewLength() == 0) {
          newLine2 = newLine1 + 1;
        }
        else {
          newLine2 = myDocument.getLineNumber(e.getOffset() + e.getNewLength()) + 1;
        }

        int linesShift = (newLine2 - newLine1) - (myLine2 - myLine1);

        int[] fixed = fixRanges(e, myLine1, myLine2);
        int line1 = fixed[0];
        int line2 = fixed[1];

        if (myDirtyRange == null) {
          myDirtyRange = new DirtyRange(line1, line2, linesShift, myBeforeTotalLines);
        }
        else {
          int oldLine1 = myDirtyRange.line1;
          int oldLine2 = myDirtyRange.line2 + myDirtyRange.lineShift;

          int updatedLine1 = myDirtyRange.line1 - Math.max(oldLine1 - line1, 0);
          int updatedLine2 = myDirtyRange.line2 + Math.max(line2 - oldLine2, 0);

          myDirtyRange = new DirtyRange(updatedLine1, updatedLine2, linesShift + myDirtyRange.lineShift, myDirtyRange.beforeTotalLines);
        }
      }
    }
  }

  @NotNull
  private int[] fixRanges(@NotNull DocumentEvent e, int line1, int line2) {
    CharSequence document = myDocument.getCharsSequence();
    int offset = e.getOffset();

    if (e.getOldLength() == 0 && e.getNewLength() != 0) {
      if (StringUtil.endsWithChar(e.getNewFragment(), '\n') && isNewline(offset - 1, document)) {
        return new int[]{line1, line2 - 1};
      }
      if (StringUtil.startsWithChar(e.getNewFragment(), '\n') && isNewline(offset + e.getNewLength(), document)) {
        return new int[]{line1 + 1, line2};
      }
    }
    if (e.getOldLength() != 0 && e.getNewLength() == 0) {
      if (StringUtil.endsWithChar(e.getOldFragment(), '\n') && isNewline(offset - 1, document)) {
        return new int[]{line1, line2 - 1};
      }
      if (StringUtil.startsWithChar(e.getOldFragment(), '\n') && isNewline(offset + e.getNewLength(), document)) {
        return new int[]{line1 + 1, line2};
      }
    }

    return new int[]{line1, line2};
  }

  private static boolean isNewline(int offset, @NotNull CharSequence sequence) {
    if (offset < 0) return false;
    if (offset >= sequence.length()) return false;
    return sequence.charAt(offset) == '\n';
  }

  private void doUpdateRanges(int beforeChangedLine1,
                              int beforeChangedLine2,
                              int linesShift,
                              int beforeTotalLines) {
    LOG.assertTrue(!myReleased);

    List<Range> rangesBeforeChange = new ArrayList<>();
    List<Range> rangesAfterChange = new ArrayList<>();
    List<Range> changedRanges = new ArrayList<>();

    sortRanges(beforeChangedLine1, beforeChangedLine2, linesShift, rangesBeforeChange, changedRanges, rangesAfterChange);

    Range firstChangedRange = ContainerUtil.getFirstItem(changedRanges);
    Range lastChangedRange = ContainerUtil.getLastItem(changedRanges);

    if (firstChangedRange != null && firstChangedRange.getLine1() < beforeChangedLine1) {
      beforeChangedLine1 = firstChangedRange.getLine1();
    }
    if (lastChangedRange != null && lastChangedRange.getLine2() > beforeChangedLine2) {
      beforeChangedLine2 = lastChangedRange.getLine2();
    }

    doUpdateRanges(beforeChangedLine1, beforeChangedLine2, linesShift, beforeTotalLines,
                   rangesBeforeChange, changedRanges, rangesAfterChange);
  }

  private void doUpdateRanges(int beforeChangedLine1,
                              int beforeChangedLine2,
                              int linesShift, // before -> after
                              int beforeTotalLines,
                              @NotNull List<Range> rangesBefore,
                              @NotNull List<Range> changedRanges,
                              @NotNull List<Range> rangesAfter) {
    try {
      int vcsTotalLines = getLineCount(myVcsDocument);

      Range lastRangeBefore = ContainerUtil.getLastItem(rangesBefore);
      Range firstRangeAfter = ContainerUtil.getFirstItem(rangesAfter);

      //noinspection UnnecessaryLocalVariable
      int afterChangedLine1 = beforeChangedLine1;
      int afterChangedLine2 = beforeChangedLine2 + linesShift;

      int vcsLine1 = getVcsLine1(lastRangeBefore, beforeChangedLine1);
      int vcsLine2 = getVcsLine2(firstRangeAfter, beforeChangedLine2, beforeTotalLines, vcsTotalLines);

      List<Range> newChangedRanges = getNewChangedRanges(afterChangedLine1, afterChangedLine2, vcsLine1, vcsLine2);

      shiftRanges(rangesAfter, linesShift);

      if (!changedRanges.equals(newChangedRanges)) {
        myRanges = new ArrayList<>(rangesBefore.size() + newChangedRanges.size() + rangesAfter.size());

        myRanges.addAll(rangesBefore);
        myRanges.addAll(newChangedRanges);
        myRanges.addAll(rangesAfter);

        for (Range range : changedRanges) {
          disposeHighlighter(range);
        }
        for (Range range : newChangedRanges) {
          createHighlighter(range);
        }

        if (myRanges.isEmpty()) {
          fireFileUnchanged();
        }
      }
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (FilesTooBigForDiffException e1) {
      destroyRanges();
      installAnathema();
    }
  }

  private static int getVcsLine1(@Nullable Range range, int line) {
    return range == null ? line : line + range.getVcsLine2() - range.getLine2();
  }

  private static int getVcsLine2(@Nullable Range range, int line, int totalLinesBefore, int totalLinesAfter) {
    return range == null ? totalLinesAfter - totalLinesBefore + line : line + range.getVcsLine1() - range.getLine1();
  }

  private List<Range> getNewChangedRanges(int changedLine1, int changedLine2, int vcsLine1, int vcsLine2)
    throws FilesTooBigForDiffException {

    if (changedLine1 == changedLine2 && vcsLine1 == vcsLine2) {
      return Collections.emptyList();
    }
    if (changedLine1 == changedLine2) {
      return Collections.singletonList(new Range(changedLine1, changedLine2, vcsLine1, vcsLine2));
    }
    if (vcsLine1 == vcsLine2) {
      return Collections.singletonList(new Range(changedLine1, changedLine2, vcsLine1, vcsLine2));
    }

    List<String> lines = DiffUtil.getLines(myDocument, changedLine1, changedLine2);
    List<String> vcsLines = DiffUtil.getLines(myVcsDocument, vcsLine1, vcsLine2);

    return RangesBuilder.createRanges(lines, vcsLines, changedLine1, vcsLine1, isDetectWhitespaceChangedLines());
  }

  private static void shiftRanges(@NotNull List<Range> rangesAfterChange, int shift) {
    for (final Range range : rangesAfterChange) {
      range.shift(shift);
    }
  }

  private void sortRanges(int beforeChangedLine1,
                          int beforeChangedLine2,
                          int linesShift,
                          @NotNull List<Range> rangesBeforeChange,
                          @NotNull List<Range> changedRanges,
                          @NotNull List<Range> rangesAfterChange) {
    int lastBefore = -1;
    int firstAfter = myRanges.size();
    for (int i = 0; i < myRanges.size(); i++) {
      Range range = myRanges.get(i);

      if (range.getLine2() < beforeChangedLine1) {
        lastBefore = i;
      }
      else if (range.getLine1() > beforeChangedLine2) {
        firstAfter = i;
        break;
      }
    }

    // Expand on ranges, that are separated from changed lines only by whitespaces

    while (lastBefore != -1) {
      int firstChangedLine = beforeChangedLine1;
      if (lastBefore + 1 < myRanges.size()) {
        Range firstChanged = myRanges.get(lastBefore + 1);
        firstChangedLine = Math.min(firstChangedLine, firstChanged.getLine1());
      }

      if (!isLineRangeEmpty(myDocument, myRanges.get(lastBefore).getLine2(), firstChangedLine)) {
        break;
      }

      lastBefore--;
    }

    while (firstAfter != myRanges.size()) {
      int firstUnchangedLineAfter = beforeChangedLine2 + linesShift;
      if (firstAfter > 0) {
        Range lastChanged = myRanges.get(firstAfter - 1);
        firstUnchangedLineAfter = Math.max(firstUnchangedLineAfter, lastChanged.getLine2() + linesShift);
      }

      if (!isLineRangeEmpty(myDocument, firstUnchangedLineAfter, myRanges.get(firstAfter).getLine1() + linesShift)) {
        break;
      }

      firstAfter++;
    }

    for (int i = 0; i < myRanges.size(); i++) {
      Range range = myRanges.get(i);
      if (i <= lastBefore) {
        rangesBeforeChange.add(range);
      }
      else if (i >= firstAfter) {
        rangesAfterChange.add(range);
      }
      else {
        changedRanges.add(range);
      }
    }
  }

  private static boolean isLineRangeEmpty(@NotNull Document document, int line1, int line2) {
    int lineCount = getLineCount(document);
    int startOffset = line1 == lineCount ? document.getTextLength() : document.getLineStartOffset(line1);
    int endOffset = line2 == lineCount ? document.getTextLength() : document.getLineStartOffset(line2);

    CharSequence interval = document.getImmutableCharSequence().subSequence(startOffset, endOffset);
    return StringUtil.isEmptyOrSpaces(interval);
  }

  @Nullable
  public Range getNextRange(Range range) {
    synchronized (LOCK) {
      if (!tryValidate()) return null;
      final int index = myRanges.indexOf(range);
      if (index == myRanges.size() - 1) return null;
      return myRanges.get(index + 1);
    }
  }

  @Nullable
  public Range getPrevRange(Range range) {
    synchronized (LOCK) {
      if (!tryValidate()) return null;
      final int index = myRanges.indexOf(range);
      if (index <= 0) return null;
      return myRanges.get(index - 1);
    }
  }

  @Nullable
  public Range getNextRange(int line) {
    synchronized (LOCK) {
      if (!tryValidate()) return null;
      for (Range range : myRanges) {
        if (line < range.getLine2() && !range.isSelectedByLine(line)) {
          return range;
        }
      }
      return null;
    }
  }

  @Nullable
  public Range getPrevRange(int line) {
    synchronized (LOCK) {
      if (!tryValidate()) return null;
      for (int i = myRanges.size() - 1; i >= 0; i--) {
        Range range = myRanges.get(i);
        if (line > range.getLine1() && !range.isSelectedByLine(line)) {
          return range;
        }
      }
      return null;
    }
  }

  @Nullable
  public Range getRangeForLine(int line) {
    synchronized (LOCK) {
      if (!tryValidate()) return null;
      for (final Range range : myRanges) {
        if (range.isSelectedByLine(line)) return range;
      }
      return null;
    }
  }

  protected void doRollbackRange(@NotNull Range range) {
    DiffUtil.applyModification(myDocument, range.getLine1(), range.getLine2(), myVcsDocument, range.getVcsLine1(), range.getVcsLine2());
  }

  @CalledWithWriteLock
  public void rollbackChanges(@NotNull Range range) {
    rollbackChanges(Collections.singletonList(range));
  }

  @CalledWithWriteLock
  public void rollbackChanges(@NotNull final BitSet lines) {
    List<Range> toRollback = new ArrayList<>();
    for (Range range : myRanges) {
      boolean check = DiffUtil.isSelectedByLine(lines, range.getLine1(), range.getLine2());
      if (check) {
        toRollback.add(range);
      }
    }

    rollbackChanges(toRollback);
  }

  /**
   * @param ranges - sorted list of ranges to rollback
   */
  @CalledWithWriteLock
  private void rollbackChanges(@NotNull final List<Range> ranges) {
    runBulkRollback(() -> {
      Range first = null;
      Range last = null;

      int shift = 0;
      for (Range range : ranges) {
        if (!range.isValid()) {
          LOG.warn("Rollback of invalid range");
          break;
        }

        if (first == null) {
          first = range;
        }
        last = range;

        Range shiftedRange = new Range(range);
        shiftedRange.shift(shift);

        doRollbackRange(shiftedRange);

        shift += (range.getVcsLine2() - range.getVcsLine1()) - (range.getLine2() - range.getLine1());
      }

      if (first != null) {
        int beforeChangedLine1 = first.getLine1();
        int beforeChangedLine2 = last.getLine2();

        int beforeTotalLines = getLineCount(myDocument) - shift;

        doUpdateRanges(beforeChangedLine1, beforeChangedLine2, shift, beforeTotalLines);
      }
    });
  }

  @CalledWithWriteLock
  private void runBulkRollback(@NotNull Runnable task) {
    myApplication.assertWriteAccessAllowed();
    if (!tryValidate()) return;

    synchronized (LOCK) {
      try {
        myDuringRollback = true;

        task.run();
      }
      catch (Error | RuntimeException e) {
        reinstallRanges();
        throw e;
      }
      finally {
        myDuringRollback = false;
      }
    }
  }

  @NotNull
  public CharSequence getCurrentContent(@NotNull Range range) {
    TextRange textRange = getCurrentTextRange(range);
    final int startOffset = textRange.getStartOffset();
    final int endOffset = textRange.getEndOffset();
    return myDocument.getImmutableCharSequence().subSequence(startOffset, endOffset);
  }

  @NotNull
  public CharSequence getVcsContent(@NotNull Range range) {
    TextRange textRange = getVcsTextRange(range);
    final int startOffset = textRange.getStartOffset();
    final int endOffset = textRange.getEndOffset();
    return myVcsDocument.getImmutableCharSequence().subSequence(startOffset, endOffset);
  }

  @NotNull
  public TextRange getCurrentTextRange(@NotNull Range range) {
    synchronized (LOCK) {
      assert isValid();
      if (!range.isValid()) {
        LOG.warn("Current TextRange of invalid range");
      }
      return DiffUtil.getLinesRange(myDocument, range.getLine1(), range.getLine2());
    }
  }

  @NotNull
  public TextRange getVcsTextRange(@NotNull Range range) {
    synchronized (LOCK) {
      assert isValid();
      if (!range.isValid()) {
        LOG.warn("Vcs TextRange of invalid range");
      }
      return DiffUtil.getLinesRange(myVcsDocument, range.getVcsLine1(), range.getVcsLine2());
    }
  }

  public boolean isLineModified(int line) {
    return isRangeModified(line, line + 1);
  }

  public boolean isRangeModified(int line1, int line2) {
    synchronized (LOCK) {
      if (!tryValidate()) return false;
      if (line1 == line2) return false;
      assert line1 < line2;

      for (Range range : myRanges) {
        if (range.getLine1() >= line2) return false;
        if (range.getLine2() > line1) return true;
      }
      return false;
    }
  }

  public int transferLineToFromVcs(int line, boolean approximate) {
    return transferLine(line, approximate, true);
  }

  public int transferLineToVcs(int line, boolean approximate) {
    return transferLine(line, approximate, false);
  }

  private int transferLine(int line, boolean approximate, boolean fromVcs) {
    synchronized (LOCK) {
      if (!tryValidate()) return approximate ? line : ABSENT_LINE_NUMBER;

      int result = line;

      for (Range range : myRanges) {
        int startLine1 = fromVcs ? range.getVcsLine1() : range.getLine1();
        int endLine1 = fromVcs ? range.getVcsLine2() : range.getLine2();
        int startLine2 = fromVcs ? range.getLine1() : range.getVcsLine1();
        int endLine2 = fromVcs ? range.getLine2() : range.getVcsLine2();

        if (startLine1 <= line && endLine1 > line) {
          return approximate ? startLine2 : ABSENT_LINE_NUMBER;
        }

        if (endLine1 > line) return result;

        int length1 = endLine1 - startLine1;
        int length2 = endLine2 - startLine2;
        result += length2 - length1;
      }
      return result;
    }
  }

  private static class DirtyRange {
    public final int line1;
    public final int line2;
    public final int lineShift;
    public final int beforeTotalLines;

    public DirtyRange(int line1, int line2, int lineShift, int beforeTotalLines) {
      this.line1 = line1;
      this.line2 = line2;
      this.lineShift = lineShift;
      this.beforeTotalLines = beforeTotalLines;
    }
  }
}
