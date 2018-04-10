/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RangeMarkerImpl extends UserDataHolderBase implements RangeMarkerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeMarkerImpl");

  @NotNull
  private final Object myDocumentOrFile; // either VirtualFile (if any) or DocumentEx if no file associated
  RangeMarkerTree.RMNode<RangeMarkerEx> myNode;

  private final long myId;
  private static final StripedIDGenerator counter = new StripedIDGenerator();

  RangeMarkerImpl(@NotNull DocumentEx document, int start, int end, boolean register) {
    this(document, start, end, register, false, false);
  }
  private RangeMarkerImpl(@NotNull DocumentEx document, int start, int end, boolean register, boolean greedyToLeft, boolean greedyToRight) {
    if (start < 0) {
      throw new IllegalArgumentException("Wrong start: " + start+"; end="+end);
    }
    if (end > document.getTextLength()) {
      throw new IllegalArgumentException("Wrong end: " + end+ "; document length="+document.getTextLength()+"; start="+start);
    }
    if (start > end){
      throw new IllegalArgumentException("start > end: start=" + start+"; end="+end);
    }

    FileDocumentManager manager = FileDocumentManager.getInstance();
    VirtualFile file = manager.getFile(document);
    myDocumentOrFile = file == null ? document : file;
    myId = counter.next();
    if (register) {
      registerInTree(start, end, greedyToLeft, greedyToRight, 0);
    }
  }

  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    getDocument().registerRangeMarker(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  protected boolean unregisterInTree() {
    if (!isValid()) return false;
    IntervalTreeImpl tree = myNode.getTree();
    tree.checkMax(true);
    boolean b = getDocument().removeRangeMarker(this);
    tree.checkMax(true);
    return b;
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
    RangeMarkerTree.RMNode node = myNode;
    return node == null ? -1 : node.intervalStart() + node.computeDeltaUpToRoot();
  }

  @Override
  public int getEndOffset() {
    RangeMarkerTree.RMNode node = myNode;
    return node == null ? -1 : node.intervalEnd() + node.computeDeltaUpToRoot();
  }

  void invalidate(@NotNull final Object reason) {
    setValid(false);
    RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;

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
    return file instanceof VirtualFile ? (DocumentEx)FileDocumentManager.getInstance().getDocument((VirtualFile)file) : (DocumentEx)file;
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
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isGreedyToLeft();
  }

  @Override
  public boolean isGreedyToRight() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isGreedyToRight();
  }

  public boolean isStickingToRight() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isStickingToRight();
  }

  @Override
  public final void documentChanged(@NotNull DocumentEvent e) {
    int oldStart = intervalStart();
    int oldEnd = intervalEnd();
    int docLength = getDocument().getTextLength();
    if (!isValid()) {
      LOG.error("Invalid range marker "+ (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                ". Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      return;
    }
    if (intervalStart() > intervalEnd() || intervalStart() < 0 || intervalEnd() > docLength - e.getNewLength() + e.getOldLength()) {
      LOG.error("RangeMarker" + (isGreedyToLeft() ? "[" : "(") + oldStart + ", " + oldEnd + (isGreedyToRight() ? "]" : ")") +
                " is invalid before update. Event = " + e + ". Doc length=" + docLength + "; "+getClass());
      invalidate(e);
      return;
    }
    changedUpdateImpl(e);
    if (isValid() && (intervalStart() > intervalEnd() || intervalStart() < 0 || intervalEnd() > docLength)) {
      LOG.error("Update failed. Event = " + e + ". " +
                "old doc length=" + docLength + "; real doc length = "+getDocument().getTextLength()+
                "; "+getClass()+"." +
                " After update: '"+this+"'");
      invalidate(e);
    }
  }

  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    if (!isValid()) return;

    TextRange newRange = applyChange(e, intervalStart(), intervalEnd(), isGreedyToLeft(), isGreedyToRight(), isStickingToRight());
    if (newRange == null) {
      invalidate(e);
      return;
    }

    setIntervalStart(newRange.getStartOffset());
    setIntervalEnd(newRange.getEndOffset());
  }

  protected void onReTarget(int startOffset, int endOffset, int destOffset) {}

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
        return new UnfairTextRange(intervalStart - oldLength, intervalEnd + newLength - oldLength);
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

  @NonNls
  public String toString() {
    return "RangeMarker" + (isGreedyToLeft() ? "[" : "(")
           + (isValid() ? "" : "invalid:") + getStartOffset() + "," + getEndOffset()
           + (isGreedyToRight() ? "]" : ")") + " " + getId();
  }

  public int setIntervalStart(int start) {
    if (start < 0) {
      LOG.error("Negative start: " + start);
    }
    return myNode.setIntervalStart(start);
  }

  public int setIntervalEnd(int end) {
    if (end < 0) {
      LOG.error("Negative end: "+end);
    }
    return myNode.setIntervalEnd(end);
  }

  @Override
  public boolean isValid() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isValid();
  }

  public boolean setValid(boolean value) {
    RangeMarkerTree.RMNode node = myNode;
    return node == null || node.setValid(value);
  }

  public int intervalStart() {
    RangeMarkerTree.RMNode node = myNode;
    if (node == null) {
      return -1;
    }
    return node.intervalStart();
  }

  public int intervalEnd() {
    RangeMarkerTree.RMNode node = myNode;
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
}
