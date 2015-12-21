/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.PrioritizedInternalDocumentListener;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.SweepProcessor;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Segment;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: cdr
 */
public class RangeMarkerTree<T extends RangeMarkerEx> extends IntervalTreeImpl<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeMarkerTree");
  private static final boolean DEBUG = LOG.isDebugEnabled() || ApplicationManager.getApplication() != null && (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal());

  private final PrioritizedDocumentListener myListener;
  private final Document myDocument;

  protected RangeMarkerTree(@NotNull Document document) {
    myDocument = document;
    myListener = new PrioritizedInternalDocumentListener() {
      @Override
      public int getPriority() {
        return EditorDocumentPriorities.RANGE_MARKER; // Need to make sure we invalidate all the stuff before someone (like LineStatusTracker) starts to modify highlights.
      }

      @Override
      public void beforeDocumentChange(DocumentEvent event) {}

      @Override
      public void documentChanged(DocumentEvent e) {
        updateMarkersOnChange(e);
      }

      @Override
      public void moveTextHappened(int start, int end, int newBase) {
        reTarget(start, end, newBase);
      }
    };

    document.addDocumentListener(myListener);
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

    return 0;
  }

  void dispose() {
    myDocument.removeDocumentListener(myListener);
  }

  private static final int DUPLICATE_LIMIT = 30; // assertion: no more than DUPLICATE_LIMIT range markers are allowed to be registered at given (start, end)
  @NotNull
  @Override
  public RMNode<T> addInterval(@NotNull T interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    interval.setValid(true);
    RMNode<T> node = (RMNode<T>)super.addInterval(interval, start, end, greedyToLeft, greedyToRight, layer);

    if (DEBUG && node.intervals.size() > DUPLICATE_LIMIT && !ApplicationInfoImpl.isInPerformanceTest() && ApplicationManager.getApplication().isUnitTestMode()) {
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
    node.processAliveKeys(new Processor<Object>() {
      @Override
      public boolean process(Object t) {
        alive.incrementAndGet();
        return true;
      }
    });
    if (alive.get() > DUPLICATE_LIMIT) {
      return "Too many range markers (" + alive + ") registered for interval "+node;
    }

    return null;
  }

  @NotNull
  @Override
  protected RMNode<T> createNewNode(@NotNull T key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    return new RMNode<T>(this, key, start, end, greedyToLeft, greedyToRight);
  }

  @Override
  protected void checkBelongsToTheTree(@NotNull T interval, boolean assertInvalid) {
    assert interval.getDocument() == myDocument;
    super.checkBelongsToTheTree(interval, assertInvalid);
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

    public RMNode(@NotNull RangeMarkerTree<T> rangeMarkerTree,
                  @NotNull T key,
                  int start,
                  int end,
                  boolean greedyToLeft,
                  boolean greedyToRight) {
      super(rangeMarkerTree, key, start, end);
      setFlag(EXPAND_TO_LEFT_FLAG, greedyToLeft);
      setFlag(EXPAND_TO_RIGHT_FLAG, greedyToRight);
    }

    boolean isGreedyToLeft() {
      return isFlagSet(EXPAND_TO_LEFT_FLAG);
    }

    boolean isGreedyToRight() {
      return isFlagSet(EXPAND_TO_RIGHT_FLAG);
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

      modCount++;
      List<IntervalNode<T>> affected = new SmartList<IntervalNode<T>>();
      collectAffectedMarkersAndShiftSubtrees(getRoot(), e, affected);
      checkMax(false);

      if (!affected.isEmpty()) {
        for (IntervalNode<T> node : affected) {
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
            RMNode<T> insertedNode = (RMNode)findOrInsert(node);
            // can change if two range become the one
            if (insertedNode != node) {
              // merge happened
              for (Getter<T> key : keys) {
                T interval = key.get();
                if (interval != null) {
                  insertedNode.addInterval(interval);
                }
              }
            }
            assert marker.isValid();
          }
          else {
            node.setValid(false);
          }
        }
      }
      checkMax(true);

      IntervalNode<T> root = getRoot();
      assert root == null || root.maxEnd + root.delta <= myDocument.getTextLength();
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // returns true if all deltas involved are still 0
  private boolean collectAffectedMarkersAndShiftSubtrees(@Nullable IntervalNode<T> root,
                                                         @NotNull DocumentEvent e,
                                                         @NotNull List<IntervalNode<T>> affected) {
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

  public boolean sweep(final int start, final int end, @NotNull SweepProcessor<T> sweepProcessor) {
    return sweep(new Generator<T>() {
      @Override
      public boolean generateInStartOffsetOrder(@NotNull Processor<T> processor) {
        return processOverlappingWith(start, end, processor);
      }
    }, sweepProcessor);
  }

  public interface Generator<T> {
    boolean generateInStartOffsetOrder(@NotNull Processor<T> processor);
  }

  public static <T extends Segment> boolean sweep(@NotNull Generator<T> generator, @NotNull final SweepProcessor<T> sweepProcessor) {
    final Queue<T> ends = new PriorityQueue<T>(5, new Comparator<T>() {
      @Override
      public int compare(@NotNull T o1, @NotNull T o2) {
        return o1.getEndOffset() - o2.getEndOffset();
      }
    });
    final List<T> starts = new ArrayList<T>();
    if (!generator.generateInStartOffsetOrder(new Processor<T>() {
      @Override
      public boolean process(T marker) {
        // decide whether previous marker ends here or new marker begins
        int start = marker.getStartOffset();
        while (true) {
          assert ends.size() == starts.size();
          T previous = ends.peek();
          if (previous != null) {
            int prevEnd = previous.getEndOffset();
            if (prevEnd <= start) {
              if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
              ends.remove();
              boolean removed = starts.remove(previous);
              assert removed;
              continue;
            }
          }
          break;
        }
        if (!sweepProcessor.process(start, marker, true, ends)) return false;
        starts.add(marker);
        ends.offer(marker);

        return true;
      }
    })) return false;

    while (!ends.isEmpty()) {
      assert ends.size() == starts.size();
      T previous = ends.remove();
      int prevEnd = previous.getEndOffset();
      if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
      boolean removed = starts.remove(previous);
      assert removed;
    }

    return true;
  }

  private void reTarget(int start, int end, int newBase) {
    l.writeLock().lock();
    try {
      checkMax(true);

      List<IntervalNode<T>> affected = new ArrayList<IntervalNode<T>>();
      collectNodesToRetarget(getRoot(), start, end, affected);
      if (affected.isEmpty()) return;

      int shift = newBase - start;
      for (IntervalNode<T> node : affected) {
        removeNode(node);
        node.setLeft(null);
        node.setRight(null);
        node.setParent(null);
        node.changeDelta(shift);
        node.setValid(true);
        pushDelta(node);
        IntervalNode<T> inserted = findOrInsert(node);
        if (inserted != node) {
          // the node already exists, reuse
          for (Getter<T> interval : node.intervals) {
            T t = interval.get();
            if (t != null) {
              inserted.addInterval(t);
            }
          }
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
                                      @NotNull List<IntervalNode<T>> affected) {
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
