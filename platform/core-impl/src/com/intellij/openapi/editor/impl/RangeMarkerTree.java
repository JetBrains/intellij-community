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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedInternalDocumentListener;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Getter;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class RangeMarkerTree<T extends RangeMarkerEx> extends IntervalTreeImpl<T> implements PrioritizedInternalDocumentListener {
  RangeMarkerTree(@NotNull Document document) {
    document.addDocumentListener(this);
  }
  RangeMarkerTree() {
  }

  @Override
  public void moveTextHappened(int start, int end, int newBase) {
    reTarget(start, end, newBase);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.RANGE_MARKER; // Need to make sure we invalidate all the stuff before someone (like LineStatusTracker) starts to modify highlights.
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    updateMarkersOnChange(event);
  }

  @Override
  protected int compareEqualStartIntervals(@NotNull IntervalTreeImpl.IntervalNode<T> i1, @NotNull IntervalTreeImpl.IntervalNode<T> i2) {
    RMNode o1 = (RMNode)i1;
    RMNode o2 = (RMNode)i2;
    boolean greedyL1 = o1.isGreedyToLeft();
    boolean greedyL2 = o2.isGreedyToLeft();
    if (greedyL1 != greedyL2) return greedyL1 ? -1 : 1;

    int o1Length = o1.intervalEnd() - o1.intervalStart();
    int o2Length = o2.intervalEnd() - o2.intervalStart();
    int d = o1Length - o2Length;
    if (d != 0) return d;

    boolean greedyR1 = o1.isGreedyToRight();
    boolean greedyR2 = o2.isGreedyToRight();
    if (greedyR1 != greedyR2) return greedyR1 ? -1 : 1;

    boolean stickyR1 = o1.isStickingToRight();
    boolean stickyR2 = o2.isStickingToRight();
    if (stickyR1 != stickyR2) return stickyR1 ? -1 : 1; 
                                     
    return 0;
  }

  void dispose(@NotNull Document document) {
    document.removeDocumentListener(this);
  }

  private static final int DUPLICATE_LIMIT = 30; // assertion: no more than DUPLICATE_LIMIT range markers are allowed to be registered at given (start, end)
  @NotNull
  @Override
  public RMNode<T> addInterval(@NotNull T interval, int start, int end, 
                               boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    ((RangeMarkerImpl)interval).setValid(true);
    RMNode<T> node = (RMNode<T>)super.addInterval(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);

    if (DEBUG && node.intervals.size() > DUPLICATE_LIMIT && !ApplicationInfoImpl.isInStressTest() && ApplicationManager.getApplication().isUnitTestMode()) {
      l.readLock().lock();
      try {
        String msg = errMsg(node);
        if (msg != null) {
          LOG.warn(msg);
        }
      }
      finally {
        l.readLock().unlock();
      }
    }
    return node;
  }
  private String errMsg(@NotNull RMNode<T> node) {
    System.gc();
    final AtomicInteger alive = new AtomicInteger();
    node.processAliveKeys(t -> {
      alive.incrementAndGet();
      return true;
    });
    if (alive.get() > DUPLICATE_LIMIT) {
      return "Too many range markers (" + alive + ") registered for interval "+node;
    }

    return null;
  }

  @NotNull
  @Override
  protected RMNode<T> createNewNode(@NotNull T key, int start, int end, 
                                    boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    return new RMNode<>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
  }

  @Override
  protected RMNode<T> lookupNode(@NotNull T key) {
    //noinspection unchecked
    return (RMNode<T>)((RangeMarkerImpl)key).myNode;
  }

  @Override
  protected void setNode(@NotNull T key, IntervalNode<T> intervalNode) {
    //noinspection unchecked
    ((RangeMarkerImpl)key).myNode = (RMNode)intervalNode;
  }

  static class RMNode<T extends RangeMarkerEx> extends IntervalTreeImpl.IntervalNode<T> {
    private static final byte EXPAND_TO_LEFT_FLAG = VALID_FLAG<<1;
    private static final byte EXPAND_TO_RIGHT_FLAG = EXPAND_TO_LEFT_FLAG<<1;
    private static final byte STICK_TO_RIGHT_FLAG = EXPAND_TO_RIGHT_FLAG<<1;

    RMNode(@NotNull RangeMarkerTree<T> rangeMarkerTree,
           @NotNull T key,
           int start,
           int end,
           boolean greedyToLeft,
           boolean greedyToRight,
           boolean stickingToRight) {
      super(rangeMarkerTree, key, start, end);
      setFlag(EXPAND_TO_LEFT_FLAG, greedyToLeft);
      setFlag(EXPAND_TO_RIGHT_FLAG, greedyToRight);
      setFlag(STICK_TO_RIGHT_FLAG, stickingToRight);
    }

    boolean isGreedyToLeft() {
      return isFlagSet(EXPAND_TO_LEFT_FLAG);
    }

    boolean isGreedyToRight() {
      return isFlagSet(EXPAND_TO_RIGHT_FLAG);
    }

    boolean isStickingToRight() {
      return isFlagSet(STICK_TO_RIGHT_FLAG);
    }

    @Override
    public String toString() {
      return (isGreedyToLeft() ? "[" : "(") + intervalStart() + "," + intervalEnd() + (isGreedyToRight() ? "]" : ")");
    }
  }

  private void updateMarkersOnChange(@NotNull DocumentEvent e) {
    try {
      l.writeLock().lock();
      if (size() == 0) return;
      checkMax(true);

      incModCount();

      List<IntervalNode<T>> affected = new SmartList<>();
      collectAffectedMarkersAndShiftSubtrees(getRoot(), e, affected);
      checkMax(false);

      if (!affected.isEmpty()) {
        // reverse direction to visit leaves first - it's cheaper to compute maxEndOf for them first
        for (int i = affected.size() - 1; i >= 0; i--) {
          IntervalNode<T> node = affected.get(i);
          // assumption: interval.getEndOffset() will never be accessed during remove()
          int startOffset = node.intervalStart();
          int endOffset = node.intervalEnd();
          removeNode(node);
          checkMax(false);
          node.clearDelta();   // we can do it because all the deltas up from the root to this node were cleared in the collectAffectedMarkersAndShiftSubtrees
          node.setParent(null);
          node.setLeft(null);
          node.setRight(null);
          node.setValid(true);
          assert node.intervalStart() == startOffset;
          assert node.intervalEnd() == endOffset;
        }
        checkMax(true);
        for (IntervalNode<T> node : affected) {
          List<Getter<T>> keys = node.intervals;
          if (keys.isEmpty()) continue; // collected away

          RangeMarkerImpl marker = null;
          for (int i = keys.size() - 1; i >= 0; i--) {
            Getter<T> key = keys.get(i);
            marker = (RangeMarkerImpl)key.get();
            if (marker != null) {
              if (!marker.isValid()) {
                // marker can become invalid on its own, e.g. FoldRegion
                node.removeIntervalInternal(i);
                marker = null;
                continue;
              }
              break;
            }
          }
          if (marker == null) continue; // node remains removed from the tree
          marker.documentChanged(e);
          if (marker.isValid()) {
            findOrInsertWithIntervals(node);
            assert marker.isValid();
          }
          else {
            node.setValid(false);
          }
        }
      }
      checkMax(true);

      IntervalNode<T> root = getRoot();
      assert root == null || root.maxEnd + root.delta <= e.getDocument().getTextLength();
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private void findOrInsertWithIntervals(IntervalNode<T> node) {
    IntervalNode<T> insertedNode = findOrInsert(node);
    // can change if two range become the one
    if (insertedNode != node) {
      // merge happened
      for (Getter<T> key : node.intervals) {
        T interval = key.get();
        if (interval != null) {
          insertedNode.addInterval(interval);
        }
      }
    }
  }

  // returns true if all deltas involved are still 0
  private boolean collectAffectedMarkersAndShiftSubtrees(@Nullable IntervalNode<T> root,
                                                         @NotNull DocumentEvent e,
                                                         @NotNull List<? super IntervalNode<T>> affected) {
    if (root == null) return true;
    boolean norm = pushDelta(root);

    int maxEnd = root.maxEnd;
    assert root.isValid();

    int offset = e.getOffset();
    int affectedEndOffset = offset + e.getOldLength();
    boolean hasAliveKeys = root.hasAliveKey(false);
    if (!hasAliveKeys) {
      // marker was garbage collected
      affected.add(root);
    }
    if (offset > maxEnd) {
      // no need to bother
    }
    else if (affectedEndOffset < root.intervalStart()) {
      // shift entire subtree
      int lengthDelta = e.getNewLength() - e.getOldLength();
      int newD = root.changeDelta(lengthDelta);
      norm &= newD == 0;
      IntervalNode<T> left = root.getLeft();
      if (left != null) {
        int newL = left.changeDelta(-lengthDelta);
        norm &= newL == 0;
      }
      norm &= pushDelta(root);
      norm &= collectAffectedMarkersAndShiftSubtrees(left, e, affected);
      correctMax(root, 0);
    }
    else {
      if (offset <= root.intervalEnd()) {
        // unlucky enough so that change affects the interval
        if (hasAliveKeys) affected.add(root); // otherwise we've already added it
        root.setValid(false);  //make invisible
      }

      norm &= collectAffectedMarkersAndShiftSubtrees(root.getLeft(), e, affected);
      norm &= collectAffectedMarkersAndShiftSubtrees(root.getRight(), e, affected);
      correctMax(root,0);
    }
    return norm;
  }

  // all intervals contained in (start, end) will be shifted by (newBase-start)
  // that's what happens when you "move" text in document, e.g. ctrl-shift-up/down the selection.
  private void reTarget(int start, int end, int newBase) {
    l.writeLock().lock();
    try {
      checkMax(true);

      List<IntervalNode<T>> affected = new ArrayList<>();
      collectNodesToRetarget(getRoot(), start, end, affected);
      if (affected.isEmpty()) return;
      // remove all first because findOrInsert can remove gced nodes which could interfere with not-yet-removed nodes
      for (IntervalNode<T> node : affected) {
        removeNode(node);
      }
      int shift = newBase - start;
      for (IntervalNode<T> node : affected) {
        node.setLeft(null);
        node.setRight(null);
        node.setParent(null);
        node.changeDelta(shift);
        node.setValid(true);
        pushDelta(node);

        List<Getter<T>> keys = node.intervals;
        if (keys.isEmpty()) continue; // collected away

        RangeMarkerImpl marker = null;
        for (int i = keys.size() - 1; i >= 0; i--) {
          Getter<T> key = keys.get(i);
          marker = (RangeMarkerImpl)key.get();
          if (marker != null) {
            if (marker.isValid()) break;
            node.removeIntervalInternal(i);
            marker = null;
          }
        }
        if (marker == null) continue;

        marker.onReTarget(start, end, newBase);

        if (marker.isValid()) {
          findOrInsertWithIntervals(node);
        }
      }
    }
    finally {
      checkMax(true);
      l.writeLock().unlock();
    }
  }

  private void collectNodesToRetarget(@Nullable IntervalNode<T> root,
                                      int start, int end,
                                      @NotNull List<? super IntervalNode<T>> affected) {
    if (root == null) return;
    pushDelta(root);

    int maxEnd = root.maxEnd;
    assert root.isValid();

    if (start > maxEnd) {
      // no need to bother
      return;
    }
    collectNodesToRetarget(root.getLeft(), start, end, affected);
    if (start <= root.intervalStart() && root.intervalEnd() <= end) {
      affected.add(root);
    }
    if (end < root.intervalStart()) {
      return;
    }
    collectNodesToRetarget(root.getRight(), start, end, affected);
  }
}
