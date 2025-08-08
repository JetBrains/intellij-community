// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.DocumentEventUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@ApiStatus.Internal
public class RangeMarkerTree<T extends RangeMarkerEx> extends IntervalTreeImpl<T> implements PrioritizedDocumentListener {
  public RangeMarkerTree(@NotNull Document document) {
    //noinspection deprecation: no need to unregister because RMT life cycle is the same as document's
    document.addDocumentListener(this);
  }

  RangeMarkerTree() {
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.RANGE_MARKER; // Need to make sure we invalidate all the stuff before someone (like LineStatusTracker) starts to modify highlights.
  }

  @Override
  protected int compareEqualStartIntervals(@NotNull IntervalTreeImpl.IntervalNode<T> i1, @NotNull IntervalTreeImpl.IntervalNode<T> i2) {
    RMNode<?> o1 = (RMNode<?>)i1;
    RMNode<?> o2 = (RMNode<?>)i2;
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

  @ApiStatus.Internal
  public void dispose(@NotNull Document document) {
    document.removeDocumentListener(this);
  }

  private static final int DUPLICATE_LIMIT = 30; // assertion: no more than DUPLICATE_LIMIT range markers are allowed to be registered at given (start, end)
  @Override
  public @NotNull RMNode<T> addInterval(@NotNull T interval, int start, int end,
                                        boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    ((RangeMarkerImpl)interval).setValid(true);
    RMNode<T> node = (RMNode<T>)super.addInterval(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);

    if (DEBUG && node.intervals.size() > DUPLICATE_LIMIT && !ApplicationManagerEx.isInStressTest() && ApplicationManager.getApplication().isUnitTestMode()) {
      runUnderWriteLock(() -> {
        String msg = errMsg(node);
        if (msg != null) {
          LOG.warn(msg);
        }
        return null;
      });
    }
    return node;
  }
  private @NonNls String errMsg(@NotNull RMNode<T> node) {
    System.gc();
    AtomicInteger alive = new AtomicInteger();
    node.processAliveKeys(t -> {
      alive.incrementAndGet();
      return true;
    });
    if (alive.get() > DUPLICATE_LIMIT) {
      return "Too many range markers (" + alive + ") registered for interval "+node;
    }

    return null;
  }

  @Override
  protected @NotNull RMNode<T> createNewNode(@NotNull T key, int start, int end,
                                             boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    return new RMNode<>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
  }

  @Override
  protected RMNode<T> lookupNode(@NotNull T key) {
    //noinspection unchecked
    return (RMNode<T>)((RangeMarkerImpl)key).myNode;
  }

  @Override
  public void setNode(@NotNull T key, IntervalNode<T> intervalNode) {
    //noinspection unchecked
    ((RangeMarkerImpl)key).myNode = (RMNode<RangeMarkerEx>)intervalNode;
  }

  @ApiStatus.Internal
  public static class RMNode<T extends RangeMarkerEx> extends IntervalTreeImpl.IntervalNode<T> {
    private static final byte EXPAND_TO_LEFT_FLAG = VALID_FLAG<<1;
    private static final byte EXPAND_TO_RIGHT_FLAG = EXPAND_TO_LEFT_FLAG<<1;
    protected static final byte STICK_TO_RIGHT_FLAG = EXPAND_TO_RIGHT_FLAG << 1;

    public RMNode(@NotNull RangeMarkerTree<T> rangeMarkerTree,
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

    public void onRemoved() {}

    @Override
    public String toString() {
      return (isGreedyToLeft() ? "[" : "(") + intervalStart() + "," + intervalEnd() + (isGreedyToRight() ? "]" : ")");
    }

    // return a list of invalidated range markers
    @NotNull List<T> invalidate() {
      setValid(false);
      IntervalTreeImpl<T> tree = getTree();
      tree.assertUnderWriteLock();
      List<T> invalidated = new ArrayList<>(intervals.size());
      processAliveKeys(markerEx -> {
        tree.beforeRemove(markerEx, this);
        tree.setNode(markerEx, null);
        invalidated.add(markerEx);
        return true;
      });
      return invalidated;
    }

    private void invalidateUnderLock() {
      List<T> toInvalidate = Collections.emptyList();
      try {
        toInvalidate = getTree().runUnderWriteLock(() -> invalidate());
      }
      finally {
        getTree().fireAfterRemoved(toInvalidate);
      }
    }
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    List<T> toInvalidate = Collections.emptyList();
    try {
      toInvalidate = runUnderWriteLock(() -> {
        List<T> ret = Collections.emptyList();
        if (size() != 0) {
          ret = updateMarkersOnChange(e);
          if (DocumentEventUtil.isMoveInsertion(e)) {
            ret = ContainerUtil.concat(ret, reTargetMarkersOnChange(e));
          }
          IntervalNode<T> root = getRoot();
          assert root == null || root.maxEnd + root.delta <= e.getDocument().getTextLength() : "Root: "+root+"; root.maxEnd="+root.maxEnd+"; root.delta="+root.delta+"; e.getDocument().getTextLength()="+e.getDocument().getTextLength()+"; event: "+e;
        }
        return ret;
      });
    }
    finally {
      fireAfterRemoved(toInvalidate);
    }
  }

  // return invalidated markers
  private @NotNull List<T> updateMarkersOnChange(@NotNull DocumentEvent e) {
    checkMax(true);

    incModCount();

    List<IntervalNode<T>> affected = new SmartList<>();
    int start = e.getOffset();
    int oldLength = e.getOldLength();
    int newLength = e.getNewLength();
    collectAffectedMarkersAndShiftSubtrees(getRoot(), start, start + oldLength, newLength - oldLength, affected);
    checkMax(false);

    return updateAffectedNodes(e, 0, affected);
  }

  // return invalidated markers
  private @NotNull List<T> updateAffectedNodes(@NotNull DocumentEvent e,
                                               int reTargetShift,
                                               @NotNull List<? extends IntervalNode<T>> affected) {
    if (affected.isEmpty()) {
      return Collections.emptyList();
    }
    List<T> invalidated = new ArrayList<>(affected.size());
    // reverse direction to visit leaves first - it's cheaper to compute maxEndOf for them first
    for (int i = affected.size() - 1; i >= 0; i--) {
      IntervalNode<T> node = affected.get(i);
      // assumption: interval.getEndOffset() will never be accessed during remove()
      int startOffset = node.intervalStart();
      int endOffset = node.intervalEnd();
      removeNode(node);
      checkMax(false);
      node.setParent(null);
      node.setLeft(null);
      node.setRight(null);
      node.setValid(true);
      if (reTargetShift == 0) {
        // we can do it because all the deltas up from the root to this node were cleared in the collectAffectedMarkersAndShiftSubtrees
        node.clearDelta();
        assert node.intervalStart() == startOffset;
        assert node.intervalEnd() == endOffset;
      }
      else {
        node.changeDelta(reTargetShift);
        pushDelta(node);
      }
    }
    checkMax(true);
    for (IntervalNode<T> node : affected) {
      RangeMarkerImpl marker = getAnyNodeMarker(node, invalidated);
      if (marker == null) continue; // node remains removed from the tree

      if (reTargetShift == 0) {
        marker.onDocumentChanged(e);
      }
      else {
        marker.onReTarget(e);
      }

      if (marker.isValid()) {
        findOrInsertWithIntervals(node);
      }
      else {
        node.setValid(false);
        ((RMNode<?>)node).onRemoved();
      }
      if (!node.isValid()) {
        node.processAliveKeys(t->invalidated.add(t));
      }
    }
    checkMax(true);
    return invalidated;
  }

  private static @Nullable <T extends RangeMarkerEx> RangeMarkerImpl getAnyNodeMarker(@NotNull IntervalNode<T> node, @NotNull List<? super T> invalidated) {
    List<Supplier<? extends T>> keys = node.intervals;
    for (int i = keys.size() - 1; i >= 0; i--) {
      Supplier<? extends T> key = keys.get(i);
      T t = key.get();
      RangeMarkerImpl marker = (RangeMarkerImpl)t;
      if (marker != null) {
        if (marker.isValid()) return marker;
        // marker can become invalid on its own, e.g., FoldRegion
        node.removeIntervalInternal(i);
        invalidated.add(t);
      }
    }
    return null;
  }

  private void findOrInsertWithIntervals(@NotNull IntervalNode<T> node) {
    IntervalNode<T> insertedNode = findOrInsert(node);
    // can change if two ranges become the one
    if (insertedNode != node) {
      // merge happened
      insertedNode.addIntervalsFrom(node);
    }
  }

  // returns true if all deltas involved are still 0
  @ApiStatus.Internal
  public void collectAffectedMarkersAndShiftSubtrees(@Nullable IntervalNode<T> root,
                                              int start, int end, int lengthDelta,
                                              @NotNull List<? super IntervalNode<T>> affected) {
    if (root == null) return;
    pushDelta(root);

    int maxEnd = root.maxEnd;
    assert root.isValid();

    boolean hasAliveKeys = root.hasAliveKey(false);
    if (!hasAliveKeys) {
      // marker was garbage collected
      affected.add(root);
    }
    if (start > maxEnd) {
      // no need to bother
    }
    else if (end < root.intervalStart()) {
      // shift entire subtree
      root.changeDelta(lengthDelta);
      IntervalNode<T> left = root.getLeft();
      if (left != null) {
        left.changeDelta(-lengthDelta);
      }
      pushDelta(root);
      collectAffectedMarkersAndShiftSubtrees(left, start, end, lengthDelta, affected);
      correctMax(root, 0);
    }
    else {
      if (start <= root.intervalEnd()) {
        // unlucky enough so that the change does affect the interval
        if (hasAliveKeys) {
          affected.add(root); // otherwise, we've already added it
        }
        root.setValid(false);  //make invisible
      }

      collectAffectedMarkersAndShiftSubtrees(root.getLeft(), start, end, lengthDelta, affected);
      collectAffectedMarkersAndShiftSubtrees(root.getRight(), start, end, lengthDelta, affected);
      correctMax(root, 0);
    }
  }

  // All intervals contained in (e.getMoveOffset(), e.getMoveOffset() + e.getNewLength())
  // will be shifted by (e.getOffset() - e.getMoveOffset()).
  // That's what happens when you "move" text in the document, e.g. ctrl-shift-up/down the selection.
  private @NotNull List<T> reTargetMarkersOnChange(@NotNull DocumentEvent e) {
    checkMax(true);

    List<IntervalNode<T>> affected = new SmartList<>();

    int moveStart = e.getMoveOffset();
    int moveEnd = moveStart + e.getNewLength();
    collectNodesToRetarget(getRoot(), moveStart, moveEnd, affected);

    return updateAffectedNodes(e, e.getOffset() - e.getMoveOffset(), affected);
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

  @Override
  void beforeRemove(@NotNull T markerEx, @NotNull IntervalNode<T> node) {
    super.beforeRemove(markerEx, node);
    ((RangeMarkerImpl)markerEx).storeOffsetsBeforeDying(node);
  }

  void copyRangeMarkersTo(@NotNull DocumentImpl document, int tabSize) {
    List<RangeMarkerEx> oldMarkers = new ArrayList<>(size());
    processAll(r -> oldMarkers.add(r));
    for (RangeMarkerEx r : oldMarkers) {
      TextRange newRange = ((RangeMarkerImpl)r).reCalcTextRangeAfterReload(document, tabSize);
      RMNode<RangeMarkerEx> node = ((RangeMarkerImpl)r).myNode;
      if (node == null) continue;
      int startOffset = newRange.getStartOffset();
      int endOffset = newRange.getEndOffset();
      if (r.isValid() && TextRange.isProperRange(startOffset, endOffset) && endOffset <= document.getTextLength()) {
        document.registerRangeMarker(r, startOffset, endOffset, r.isGreedyToLeft(), r.isGreedyToRight(), 0);
      }
      else {
        node.invalidateUnderLock();
      }
    }
  }
}
