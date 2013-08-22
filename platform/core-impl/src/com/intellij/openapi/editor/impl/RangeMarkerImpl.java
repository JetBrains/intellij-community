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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RangeMarkerImpl extends UserDataHolderBase implements RangeMarkerEx, MutableInterval {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeMarkerImpl");

  protected final DocumentEx myDocument;
  protected RangeMarkerTree.RMNode<RangeMarkerEx> myNode;

  private final long myId;
  private static final StripedIDGenerator counter = new StripedIDGenerator();

  protected RangeMarkerImpl(@NotNull DocumentEx document, int start, int end, boolean register) {
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

    myDocument = document;
    myId = counter.next();
    if (register) {
      registerInTree(start, end, greedyToLeft, greedyToRight, 0);
    }
  }

  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    myDocument.registerRangeMarker(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  protected boolean unregisterInTree() {
    if (!isValid()) return false;
    IntervalTreeImpl tree = myNode.getTree();
    tree.checkMax(true);
    boolean b = myDocument.removeRangeMarker(this);
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
    return intervalStart() + (node == null ? 0 : node.computeDeltaUpToRoot());
  }

  @Override
  public int getEndOffset() {
    RangeMarkerTree.RMNode node = myNode;
    return intervalEnd() + (node == null ? 0 : node.computeDeltaUpToRoot());
  }

  public void invalidate(@NotNull final Object reason) {
    setValid(false);
    RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;

    if (node != null) {
      node.processAliveKeys(new Processor<RangeMarkerEx>() {
        @Override
        public boolean process(RangeMarkerEx markerEx) {
          myNode.getTree().reportInvalidation(markerEx, reason);
          return true;
        }
      });
    }
  }

  @Override
  @NotNull
  public DocumentEx getDocument() {
    return myDocument;
  }

  // fake method to simplify setGreedyToLeft/right methods. overridden in RangeHighlighter
  public int getLayer() {
    return 0;
  }

  @Override
  public void setGreedyToLeft(final boolean greedy) {
    if (!isValid() || greedy == isGreedyToLeft()) return;

    myNode.getTree().changeData(this, getStartOffset(), getEndOffset(), greedy, isGreedyToRight(), getLayer());
  }

  @Override
  public void setGreedyToRight(final boolean greedy) {
    if (!isValid() || greedy == isGreedyToRight()) return;
    myNode.getTree().changeData(this, getStartOffset(), getEndOffset(), isGreedyToLeft(), greedy, getLayer());
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

  @Override
  public final void documentChanged(DocumentEvent e) {
    int oldStart = intervalStart();
    int oldEnd = intervalEnd();
    int docLength = myDocument.getTextLength();
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
                "old doc length=" + docLength + "; real doc length = "+myDocument.getTextLength()+
                "; "+getClass()+"." +
                " After update: '"+this+"'");
      invalidate(e);
    }
  }

  protected void changedUpdateImpl(DocumentEvent e) {
    if (!isValid()) return;

    // Process if one point.
    if (intervalStart() == intervalEnd()) {
      processIfOnePoint(e);
      return;
    }

    final int offset = e.getOffset();
    final int oldLength = e.getOldLength();
    final int newLength = e.getNewLength();

    // changes after the end.
    if (intervalEnd() < offset || !isGreedyToRight() && intervalEnd() == offset) {
      return;
    }

    // changes before start
    if (intervalStart() > offset + oldLength || !isGreedyToLeft() && intervalStart() == offset + oldLength) {
      setIntervalStart(intervalStart() + newLength - oldLength);
      setIntervalEnd(intervalEnd() + newLength - oldLength);
      return;
    }

    // Changes inside marker's area. Expand/collapse.
    if (intervalStart() <= offset && intervalEnd() >= offset + oldLength) {
      setIntervalEnd(intervalEnd() + newLength - oldLength);
      return;
    }

    // At this point we either have (myStart xor myEnd inside changed area) or whole area changed.

    // Replacing prefix or suffix...
    if (intervalStart() >= offset && intervalStart() <= offset + oldLength && intervalEnd() > offset + oldLength) {
      setIntervalEnd(intervalEnd() + newLength - oldLength);
      setIntervalStart(offset + newLength);
      return;
    }

    if (intervalEnd() >= offset && intervalEnd() <= offset + oldLength && intervalStart() < offset) {
      setIntervalEnd(offset);
      return;
    }

    invalidate(e);
  }

  private void processIfOnePoint(DocumentEvent e) {
    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int oldEnd = offset + oldLength;
    if (offset < intervalStart() && intervalStart() < oldEnd) {
      invalidate(e);
      return;
    }

    if (offset == intervalStart() && oldLength == 0 && isGreedyToRight()) {
      setIntervalEnd(intervalEnd() + e.getNewLength());
      return;
    }

    if (intervalStart() > oldEnd || intervalStart() == oldEnd  && oldLength > 0) {
      setIntervalStart(intervalStart() + e.getNewLength() - oldLength);
      setIntervalEnd(intervalEnd() + e.getNewLength() - oldLength);
    }
  }

  @NonNls
  public String toString() {
    return "RangeMarker" + (isGreedyToLeft() ? "[" : "(") + (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + (
      isGreedyToRight() ? "]" : ")") + " " + getId();
  }

  @Override
  public int setIntervalStart(int start) {
    return myNode.setIntervalStart(start);
  }

  @Override
  public int setIntervalEnd(int end) {
    return myNode.setIntervalEnd(end);
  }

  @Override
  public boolean isValid() {
    RangeMarkerTree.RMNode node = myNode;
    return node != null && node.isValid();
  }

  @Override
  public boolean setValid(boolean value) {
    RangeMarkerTree.RMNode node = myNode;
    return node == null || node.setValid(value);
  }

  @Override
  public int intervalStart() {
    RangeMarkerTree.RMNode node = myNode;
    if (node == null) {
      return -1;
    }
    return node.intervalStart();
  }

  @Override
  public int intervalEnd() {
    RangeMarkerTree.RMNode node = myNode;
    if (node == null) {
      return -1;
    }
    return node.intervalEnd();
  }
}
