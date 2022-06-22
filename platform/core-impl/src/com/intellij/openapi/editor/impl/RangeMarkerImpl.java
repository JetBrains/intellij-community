// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RangeMarkerImpl extends UserDataHolderBase implements RangeMarkerEx {
  private static final Logger LOG = Logger.getInstance(RangeMarkerImpl.class);

  @NotNull
  private final Object myDocumentOrFile; // either VirtualFile (if any) or DocumentEx if no file associated
  RangeMarkerTree.RMNode<RangeMarkerEx> myNode;

  private final long myId;
  private static final StripedIDGenerator counter = new StripedIDGenerator();

  @ApiStatus.Internal
  public RangeMarkerImpl(@NotNull DocumentEx document, int start, int end, boolean register, boolean forceDocumentStrongReference) {
    this(forceDocumentStrongReference ? document : ObjectUtils.notNull(FileDocumentManager.getInstance().getFile(document), document),
         document.getTextLength(), start, end, register, false, false);
  }

  // constructor which creates marker without document and saves it in the virtual file directly. Can be cheaper than loading document.
  RangeMarkerImpl(@NotNull VirtualFile virtualFile, int start, int end, int estimatedDocumentLength, boolean register) {
    // unfortunately we don't know the exact document size until we load it
    this(virtualFile, estimatedDocumentLength, start, end, register, false, false);
  }

  private RangeMarkerImpl(@NotNull Object documentOrFile,
                          int documentTextLength,
                          int start,
                          int end,
                          boolean register,
                          boolean greedyToLeft,
                          boolean greedyToRight) {
    if (start < 0) {
      throw new IllegalArgumentException("Wrong start: " + start+"; end="+end);
    }
    if (end > documentTextLength) {
      throw new IllegalArgumentException("Wrong end: " + end + "; document length=" + documentTextLength + "; start=" + start);
    }
    if (start > end) {
      throw new IllegalArgumentException("start > end: start=" + start+"; end="+end);
    }

    myDocumentOrFile = documentOrFile;
    myId = counter.next();
    if (register) {
      registerInTree(start, end, greedyToLeft, greedyToRight, 0);
    }
  }

  static int estimateDocumentLength(@NotNull VirtualFile virtualFile) {
    Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    return document == null ? (int)virtualFile.getLength() : document.getTextLength();
  }

  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    getDocument().registerRangeMarker(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  protected void unregisterInTree() {
    if (!isValid()) return;
    IntervalTreeImpl<?> tree = myNode.getTree();
    tree.checkMax(true);
    DocumentEx document = getCachedDocument();
    if (document == null) {
      myNode = null;
    }
    else {
      document.removeRangeMarker(this);
    }
    tree.checkMax(true);
  }

  @Override
  public long getId() {
    return myId;
  }

  @Override
  public void dispose() {
    unregisterInTree();
  }

  @Override
  public int getStartOffset() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node == null ? -1 : node.intervalStart() + node.computeDeltaUpToRoot();
  }

  @Override
  public int getEndOffset() {
    RangeMarkerTree.RMNode<?> node = myNode;
    return node == null ? -1 : node.intervalEnd() + node.computeDeltaUpToRoot();
  }

  void invalidate(@NotNull final Object reason) {
    setValid(false);
    RangeMarkerTree.RMNode<?> node = myNode;

    if (node != null) {
      node.processAliveKeys(markerEx -> {
        myNode.getTree().beforeRemove(markerEx, reason);
        return true;
      });
    }
  }

  @Override
  @NotNull
  public final DocumentEx getDocument() {
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
  public void setGreedyToLeft(final boolean greedy) {
    if (!isValid() || greedy == isGreedyToLeft()) return;

    myNode.getTree().changeData(this, getStartOffset(), getEndOffset(), greedy, isGreedyToRight(), isStickingToRight(), getLayer());
  }

  @Override
  public void setGreedyToRight(final boolean greedy) {
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

  @Override
  public final void documentChanged(@NotNull DocumentEvent e) {
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
      invalidate(e);
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
      invalidate(e);
    }
  }

  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    doChangeUpdate(e);
  }

  private void doChangeUpdate(@NotNull DocumentEvent e) {
    if (!isValid()) return;

    TextRange newRange = applyChange(e, intervalStart(), intervalEnd(), isGreedyToLeft(), isGreedyToRight(), isStickingToRight());
    if (newRange == null) {
      invalidate(e);
      return;
    }

    setIntervalStart(newRange.getStartOffset());
    setIntervalEnd(newRange.getEndOffset());
  }

  protected void persistentHighlighterUpdate(@NotNull DocumentEvent e, boolean wholeLineRange) {
    int line = 0;
    DocumentEventImpl event = (DocumentEventImpl)e;
    boolean viaDiff = isValid() && PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, getStartOffset(), getEndOffset());
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
        line = getDocument().getLineNumber(getStartOffset());
        int endLine = getDocument().getLineNumber(getEndOffset());
        if (endLine != line) {
          setIntervalEnd(getDocument().getLineEndOffset(line));
        }
      }
    }
    if (isValid() && wholeLineRange) {
      setIntervalStart(DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), line));
      setIntervalEnd(getDocument().getLineEndOffset(line));
    }
  }

  private int translatedViaDiff(@NotNull DocumentEventImpl e, int line) throws FilesTooBigForDiffException {
    line = e.translateLineViaDiff(line);
    if (line < 0 || line >= getDocument().getLineCount()) {
      invalidate(e);
    }
    else {
      DocumentEx document = getDocument();
      setIntervalStart(document.getLineStartOffset(line));
      setIntervalEnd(document.getLineEndOffset(line));
    }
    return line;
  }

  // Called after the range was shifted from e.getMoveOffset() to e.getOffset()
  protected void onReTarget(@NotNull DocumentEvent e) {}

  @Nullable
  static TextRange applyChange(@NotNull DocumentEvent e, int intervalStart, int intervalEnd,
                               boolean isGreedyToLeft, boolean isGreedyToRight, boolean isStickingToRight) {
    if (intervalStart == intervalEnd) {
      return processIfOnePoint(e, intervalStart, isGreedyToRight, isStickingToRight);
    }

    final int offset = e.getOffset();
    final int oldLength = e.getOldLength();
    final int newLength = e.getNewLength();

    // changes after the end.
    if (intervalEnd < offset) {
      return new UnfairTextRange(intervalStart, intervalEnd);
    }
    if (!isGreedyToRight && intervalEnd == offset) {
      // handle replaceString that was minimized and resulted in insertString at the range end
      if (e instanceof DocumentEventImpl && oldLength == 0 && ((DocumentEventImpl)e).getInitialStartOffset() < offset) {
        return new UnfairTextRange(intervalStart, intervalEnd + newLength);
      }
      return new UnfairTextRange(intervalStart, intervalEnd);
    }

    // changes before start
    if (intervalStart > offset + oldLength) {
      return new UnfairTextRange(intervalStart + newLength - oldLength, intervalEnd + newLength - oldLength);
    }
    if (!isGreedyToLeft && intervalStart == offset + oldLength) {
      // handle replaceString that was minimized and resulted in insertString at the range start
      if (e instanceof DocumentEventImpl && oldLength == 0 && ((DocumentEventImpl)e).getInitialStartOffset() + ((DocumentEventImpl)e).getInitialOldLength() > offset) {
        return new UnfairTextRange(intervalStart, intervalEnd + newLength);
      }
      return new UnfairTextRange(intervalStart + newLength - oldLength, intervalEnd + newLength - oldLength);
    }

    // Changes inside marker's area. Expand/collapse.
    if (intervalStart <= offset && intervalEnd >= offset + oldLength) {
      return new ProperTextRange(intervalStart, intervalEnd + newLength - oldLength);
    }

    // At this point we either have (myStart xor myEnd inside changed area) or whole area changed.

    // Replacing prefix or suffix...
    if (intervalStart >= offset && intervalStart <= offset + oldLength && intervalEnd > offset + oldLength) {
      return new ProperTextRange(offset + newLength, intervalEnd + newLength - oldLength);
    }

    if (intervalEnd >= offset && intervalEnd <= offset + oldLength && intervalStart < offset) {
      return new UnfairTextRange(intervalStart, offset);
    }

    return null;
  }

  @Nullable
  private static TextRange processIfOnePoint(@NotNull DocumentEvent e, int intervalStart, boolean greedyRight, boolean stickyRight) {
    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int oldEnd = offset + oldLength;
    if (offset < intervalStart && intervalStart < oldEnd) {
      return null;
    }

    if (offset == intervalStart && oldLength == 0) {
      if (greedyRight) {
        return new UnfairTextRange(intervalStart, intervalStart + e.getNewLength());
      }
      else if (stickyRight) {
        return new UnfairTextRange(intervalStart + e.getNewLength(), intervalStart + e.getNewLength());
      }
    }

    if (intervalStart > oldEnd || intervalStart == oldEnd  && oldLength > 0) {
      return new UnfairTextRange(intervalStart + e.getNewLength() - oldLength, intervalStart + e.getNewLength() - oldLength);
    }

    return new UnfairTextRange(intervalStart, intervalStart);
  }

  @Override
  @NonNls
  public String toString() {
    return "RangeMarker" + (isGreedyToLeft() ? "[" : "(")
           + (isValid() ? "" : "invalid:") + getStartOffset() + "," + getEndOffset()
           + (isGreedyToRight() ? "]" : ")") + " " + getId();
  }

  int setIntervalStart(int start) {
    if (start < 0) {
      LOG.error("Negative start: " + start);
    }
    return myNode.setIntervalStart(start);
  }

  int setIntervalEnd(int end) {
    if (end < 0) {
      LOG.error("Negative end: "+end);
    }
    return myNode.setIntervalEnd(end);
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
    final FileType fileType = file.getFileType();
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

  public RangeMarker findRangeMarkerAfter() {
    return myNode.getTree().findRangeMarkerAfter(this);
  }

  public RangeMarker findRangeMarkerBefore() {
    return myNode.getTree().findRangeMarkerBefore(this);
  }

  // re-register myself in the document tree (e.g. after document load)
  void reRegister(@NotNull DocumentImpl document, int tabSize) {
    int startOffset = getStartOffset();
    int endOffset = getEndOffset();
    if (startOffset <= endOffset && endOffset <= document.getTextLength()) {
      document.registerRangeMarker(this, startOffset, endOffset, isGreedyToLeft(), isGreedyToRight(), 0);
    }
    else {
      invalidate("document was gc-ed and re-created with invalid offsets: ("+startOffset+","+endOffset+"): "+document.getTextLength());
    }
  }
}
