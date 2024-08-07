// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public class RangeMarkerImpl extends UserDataHolderBase implements RangeMarkerEx {
  private static final Logger LOG = Logger.getInstance(RangeMarkerImpl.class);

  private final @NotNull Object myDocumentOrFile; // either VirtualFile (if any) or DocumentEx if no file associated
  RangeMarkerTree.RMNode<RangeMarkerEx> myNode;

  private volatile long myId;
  private static final StripedIDGenerator counter = new StripedIDGenerator();

  RangeMarkerImpl(@NotNull DocumentEx document, int start, int end, boolean register, boolean forceDocumentStrongReference) {
    this(forceDocumentStrongReference ? document : ObjectUtils.notNull(FileDocumentManager.getInstance().getFile(document), document),
         document.getTextLength(), start, end, register, false, false);
  }

  // The constructor which creates a marker without a document and saves it in the virtual file directly. Can be cheaper than loading the entire document.
  RangeMarkerImpl(@NotNull VirtualFile virtualFile, int start, int end, int estimatedDocumentLength, boolean register) {
    // unfortunately, we don't know the exact document size until we load it
    this(virtualFile, estimatedDocumentLength, start, end, register, false, false);
  }

  private RangeMarkerImpl(@NotNull Object documentOrFile,
                          int documentTextLength,
                          int start,
                          int end,
                          boolean register,
                          boolean greedyToLeft,
                          boolean greedyToRight) {
    if (end > documentTextLength) {
      throw new IllegalArgumentException("Invalid offsets: start=" +start+ "; end=" + end + "; document length=" + documentTextLength);
    }

    myDocumentOrFile = documentOrFile;
    myId = counter.next();
    if (register) {
      registerInTree(start, end, greedyToLeft, greedyToRight, 0);
    }
  }

  static int estimateDocumentLength(@NotNull VirtualFile virtualFile) {
    Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    return document == null ? Math.max(0, (int)virtualFile.getLength()) : document.getTextLength();
  }

  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    getDocument().registerRangeMarker(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  protected void unregisterInTree() {
    RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;
    if (!isValid()) return;
    IntervalTreeImpl<?> tree = node.getTree();
    tree.checkMax(true);
    DocumentEx document = getCachedDocument();
    if (document == null) {
      storeOffsetsBeforeDying(node);
      myNode = null;
    }
    else {
      document.removeRangeMarker(this);
    }
    tree.checkMax(true);
  }

  @Override
  public long getId() {
    // read id before myNode to avoid returning changed id during concurrent dispose
    // (because myId is assigned before myNode in dispose)
    long id = myId;
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null) {
      throw new IllegalStateException("Already disposed");
    }
    return id;
  }

  @Override
  public void dispose() {
    unregisterInTree();
  }

  @Override
  public int getStartOffset() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node == null ? TextRangeScalarUtil.startOffset(myId) : node.intervalStart() + node.computeDeltaUpToRoot();
  }

  @Override
  public int getEndOffset() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node == null ? TextRangeScalarUtil.endOffset(myId) : node.intervalEnd() + node.computeDeltaUpToRoot();
  }

  @Override
  public @NotNull TextRange getTextRange() {
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null) {
      return TextRangeScalarUtil.create(myId);
    }
    int delta = node.computeDeltaUpToRoot();
    return TextRangeScalarUtil.create(TextRangeScalarUtil.shift(node.toScalarRange(), delta, delta));
  }

  void invalidate() {
    RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;
    if (node != null) {
      node.invalidate();
    }
  }

  @Override
  public final @NotNull DocumentEx getDocument() {
    Object file = myDocumentOrFile;
    DocumentEx document =
      file instanceof VirtualFile ? (DocumentEx)FileDocumentManager.getInstance().getDocument((VirtualFile)file) : (DocumentEx)file;
    if (document == null) {
      LOG.error("document is null; isValid=" + isValid()+"; file="+file);
    }
    return document;
  }

  DocumentEx getCachedDocument() {
    Object file = myDocumentOrFile;
    return file instanceof VirtualFile ? (DocumentEx)FileDocumentManager.getInstance().getCachedDocument((VirtualFile)file) : (DocumentEx)file;
  }

  // fake method to simplify setGreedyToLeft/right methods. overridden in RangeHighlighter
  public int getLayer() {
    return 0;
  }

  @Override
  public void setGreedyToLeft(boolean greedy) {
    if (!isValid() || greedy == isGreedyToLeft()) return;

    myNode.getTree().changeData(this, getStartOffset(), getEndOffset(), greedy, isGreedyToRight(), isStickingToRight(), getLayer());
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    if (!isValid() || greedy == isGreedyToRight()) return;
    myNode.getTree().changeData(this, getStartOffset(), getEndOffset(), isGreedyToLeft(), greedy, isStickingToRight(), getLayer());
  }

  public void setStickingToRight(boolean value) {
    if (!isValid() || value == isStickingToRight()) return;
    myNode.getTree().changeData(this, getStartOffset(), getEndOffset(), isGreedyToLeft(), isGreedyToRight(), value, getLayer());
  }

  @Override
  public boolean isGreedyToLeft() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node != null && node.isGreedyToLeft();
  }

  @Override
  public boolean isGreedyToRight() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node != null && node.isGreedyToRight();
  }

  public boolean isStickingToRight() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node != null && node.isStickingToRight();
  }

  /**
   * @deprecated do not use because it can mess internal offsets
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public final void documentChanged(@NotNull DocumentEvent e) {
    doChangeUpdate(e);
  }

  final void onDocumentChanged(@NotNull DocumentEvent e) {
    int oldStart = intervalStart();
    int oldEnd = intervalEnd();
    int docLength = e.getDocument().getTextLength();
    if (!isValid()) {
      LOG.error("Invalid range marker "+ (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                ". Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      return;
    }
    if (oldStart > oldEnd || oldStart < 0 || oldEnd > docLength - e.getNewLength() + e.getOldLength()) {
      LOG.error("RangeMarker" + (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                " is invalid before update. Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      invalidate();
      return;
    }
    changedUpdateImpl(e);
    int newStart;
    int newEnd;
    if (isValid() && ((newStart=intervalStart()) > (newEnd=intervalEnd()) || newStart < 0 || newEnd > docLength)) {
      LOG.error("Update failed. Event = " + e + ". " +
                "Doc length=" + docLength +
                "; "+getClass()+". Before update: " + (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                " After update: '"+this+"'");
      invalidate();
    }
  }

  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    doChangeUpdate(e);
  }

  private void doChangeUpdate(@NotNull DocumentEvent e) {
    if (!isValid()) return;
    RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;
    long newRange = node == null ? -1 : applyChange(e, node.toScalarRange(), isGreedyToLeft(), isGreedyToRight(), isStickingToRight());
    if (newRange == -1) {
      invalidate();
    }
    else {
      node.setRange(newRange);
    }
  }

  protected void persistentHighlighterUpdate(@NotNull DocumentEvent e, boolean wholeLineRange) {
    int line = 0;
    DocumentEventImpl event = (DocumentEventImpl)e;
    boolean viaDiff = isValid() && PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, toScalarRange());
    if (viaDiff) {
      try {
        line = event.getLineNumberBeforeUpdate(getStartOffset());
        line = translatedViaDiff(event, line);
      }
      catch (FilesTooBigForDiffException exception) {
        viaDiff = false;
      }
    }
    if (!viaDiff) {
      doChangeUpdate(e);
      if (isValid()) {
        int startOffset = getStartOffset();
        line = getDocument().getLineNumber(startOffset);
        int endLine = getDocument().getLineNumber(getEndOffset());
        if (endLine != line) {
          setRange(TextRangeScalarUtil.toScalarRange(startOffset, getDocument().getLineEndOffset(line)));
        }
      }
    }
    if (isValid() && wholeLineRange) {
      int newStart = DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), line);
      int newEnd = getDocument().getLineEndOffset(line);
      setRange(TextRangeScalarUtil.toScalarRange(newStart, newEnd));
    }
  }

  private int translatedViaDiff(@NotNull DocumentEventImpl e, int line) throws FilesTooBigForDiffException {
    line = e.translateLineViaDiff(line);
    if (line < 0 || line >= getDocument().getLineCount()) {
      invalidate();
    }
    else {
      DocumentEx document = getDocument();
      setRange(TextRangeScalarUtil.toScalarRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
    }
    return line;
  }

  // Called after the range was shifted from e.getMoveOffset() to e.getOffset()
  protected void onReTarget(@NotNull DocumentEvent e) {}

  // return -1 if invalid
  static long applyChange(@NotNull DocumentEvent e, long range,
                          boolean isGreedyToLeft, boolean isGreedyToRight, boolean isStickingToRight) {
    int intervalStart = TextRangeScalarUtil.startOffset(range);
    int intervalEnd = TextRangeScalarUtil.endOffset(range);
    if (intervalStart == intervalEnd) {
      return processIfOnePoint(e, intervalStart, isGreedyToRight, isStickingToRight);
    }

    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int newLength = e.getNewLength();

    // changes after the end.
    if (offset > intervalEnd) {
      return TextRangeScalarUtil.toScalarRange(intervalStart, intervalEnd);
    }
    if (!isGreedyToRight && intervalEnd == offset) {
      // handle replaceString that was minimized and resulted in insertString at the range end
      if (e instanceof DocumentEventImpl && oldLength == 0 && ((DocumentEventImpl)e).getInitialStartOffset() < offset) {
        return TextRangeScalarUtil.toScalarRange(intervalStart, intervalEnd + newLength);
      }
      return TextRangeScalarUtil.toScalarRange(intervalStart, intervalEnd);
    }

    // changes before start
    if (intervalStart > offset + oldLength) {
      return TextRangeScalarUtil.toScalarRange(intervalStart + newLength - oldLength, intervalEnd + newLength - oldLength);
    }
    if (!isGreedyToLeft && intervalStart == offset + oldLength) {
      // handle replaceString that was minimized and resulted in insertString at the range start
      if (e instanceof DocumentEventImpl && oldLength == 0 && ((DocumentEventImpl)e).getInitialStartOffset() + ((DocumentEventImpl)e).getInitialOldLength() > offset) {
        return TextRangeScalarUtil.toScalarRange(intervalStart, intervalEnd + newLength);
      }
      return TextRangeScalarUtil.toScalarRange(intervalStart + newLength - oldLength, intervalEnd + newLength - oldLength);
    }

    // Changes inside marker's area. Expand/collapse.
    if (intervalStart <= offset && intervalEnd >= offset + oldLength) {
      return TextRangeScalarUtil.toScalarRange(intervalStart, intervalEnd + newLength - oldLength);
    }

    // At this point we either have (myStart xor myEnd inside changed area) or whole area changed.

    // Replacing prefix or suffix...
    if (intervalStart >= offset && intervalStart <= offset + oldLength && intervalEnd > offset + oldLength) {
      return TextRangeScalarUtil.toScalarRange(offset + newLength, intervalEnd + newLength - oldLength);
    }

    if (intervalEnd <= offset + oldLength && intervalStart < offset) {
      return TextRangeScalarUtil.toScalarRange(intervalStart, offset);
    }

    return -1;
  }

  private static long processIfOnePoint(@NotNull DocumentEvent e, int intervalStart, boolean greedyRight, boolean stickyRight) {
    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int oldEnd = offset + oldLength;
    if (offset < intervalStart && intervalStart < oldEnd) {
      return -1;
    }

    if (offset == intervalStart && oldLength == 0) {
      if (greedyRight) {
        return TextRangeScalarUtil.toScalarRange(intervalStart, intervalStart + e.getNewLength());
      }
      else if (stickyRight) {
        int off = intervalStart + e.getNewLength();
        return TextRangeScalarUtil.toScalarRange(off, off);
      }
    }

    if (intervalStart > oldEnd || intervalStart == oldEnd && oldLength > 0) {
      int off = intervalStart + e.getNewLength() - oldLength;
      return TextRangeScalarUtil.toScalarRange(off, off);
    }

    return TextRangeScalarUtil.toScalarRange(intervalStart, intervalStart);
  }

  @Override
  public @NonNls String toString() {
    return "RangeMarker" + (isGreedyToLeft() ? "[" : "(")
           + (isValid() ? "" : "invalid:") + getStartOffset() + "," + getEndOffset()
           + (isGreedyToRight() ? "]" : ")")
           + " " + (isValid() ? getId() : "");
  }

  void setRange(long scalarRange) {
    myNode.setRange(scalarRange);
  }

  @Override
  public boolean isValid() {
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null || !node.isValid()) return false;
    Object file = myDocumentOrFile;
    return file instanceof Document || canHaveDocument((VirtualFile)file);
  }

  private static boolean canHaveDocument(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) return true;
    if (!file.isValid() || file.isDirectory() || isBinaryWithoutDecompiler(file)) return false;

    return !file.getFileType().isBinary() || !FileUtilRt.isTooLarge(file.getLength());
  }

  private static boolean isBinaryWithoutDecompiler(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    return fileType.isBinary() && BinaryFileTypeDecompilers.getInstance().forFileType(fileType) == null;
  }

  protected boolean setValid(boolean value) {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node == null || node.setValid(value);
  }

  public int intervalStart() {
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null) {
      return -1;
    }
    return node.intervalStart();
  }

  public int intervalEnd() {
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null) {
      return -1;
    }
    return node.intervalEnd();
  }

  /**
   * @return this marker text range in the scalar form
   */
  @ApiStatus.Internal
  public long getScalarRange() {
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null) {
      return myId;
    }
    long range = node.toScalarRange();
    int delta = node.computeDeltaUpToRoot();
    return TextRangeScalarUtil.shift(range, delta, delta);
  }

  // return intrinsic range belonging to that node (without delta-up-to-the-root correction)
  long toScalarRange() {
    RangeMarkerTree.RMNode<?> node = myNode;
    if (node == null) {
      return myId;
    }
    return node.toScalarRange();
  }

  public RangeMarker findRangeMarkerAfter() {
    return myNode.getTree().findRangeMarkerAfter(this);
  }

  public RangeMarker findRangeMarkerBefore() {
    return myNode.getTree().findRangeMarkerBefore(this);
  }

  @NotNull
  TextRange reCalcTextRangeAfterReload(@NotNull DocumentImpl document, int tabSize) {
    return getTextRange();
  }

  void storeOffsetsBeforeDying(@NotNull IntervalTreeImpl.IntervalNode<?> node) {
    // store current offsets to give async listeners the ability to get offsets
    int delta = node.computeDeltaUpToRoot();
    long range = TextRangeScalarUtil.shift(node.toScalarRange(), delta, delta);
    int startOffset = Math.max(0, TextRangeScalarUtil.startOffset(range));
    int endOffset = Math.max(startOffset, TextRangeScalarUtil.endOffset(range));
    // piggyback myId to store offsets, to conserve memory
    myId = TextRangeScalarUtil.toScalarRange(startOffset, endOffset); // avoid invalid range
  }

  @TestOnly
  public static void runAssertingInternalInvariants(@NotNull ThrowableRunnable<?> runnable) throws Throwable {
    RedBlackTree.VERIFY = true;
    try {
      runnable.run();
    }
    finally {
      RedBlackTree.VERIFY = false;
    }
  }
}
