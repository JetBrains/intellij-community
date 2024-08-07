// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.WalkingState;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

abstract class IntervalTreeImpl<T> extends RedBlackTree<T> implements IntervalTree<T> {
  static final Logger LOG = Logger.getInstance(IntervalTreeImpl.class);
  static final boolean DEBUG = LOG.isDebugEnabled() || ApplicationManager.getApplication() != null && ApplicationManager.getApplication().isUnitTestMode();
  private int keySize; // number of all intervals, counting all duplicates, some of them maybe gced
  final ReadWriteLock l = new ReentrantReadWriteLock();

  protected abstract int compareEqualStartIntervals(@NotNull IntervalNode<T> i1, @NotNull IntervalNode<T> i2);
  private final ReferenceQueue<T> myReferenceQueue = new ReferenceQueue<>();
  private int deadReferenceCount;

  static class IntervalNode<E> extends RedBlackTree.Node<E> implements MutableInterval {
    private volatile long myRange;
    private static final byte ATTACHED_TO_TREE_FLAG = COLOR_MASK <<1; // true if the node is inserted to the tree
    final List<Supplier<? extends E>> intervals;
    int maxEnd; // max of all intervalEnd()s among all children.
    int delta;  // delta of startOffset. getStartOffset() = myStartOffset + Sum of deltas up to root

    private volatile long cachedDeltaUpToRoot; // field (packed to long for atomicity) containing deltaUpToRoot, node modCount and allDeltasUpAreNull flag
    // These fields are packed inside cachedDeltaUpToRoot, as follows:
    //  private int modCount; // if it equals to the com.intellij.openapi.editor.impl.RedBlackTree.modCount then deltaUpToRoot can be used, otherwise it is expired
    //  private int deltaUpToRoot; // sum of all deltas up to the root (including this node.delta). Has valid value only if modCount == IntervalTreeImpl.this.modCount
    //  private boolean allDeltasUpAreNull;  // true if all deltas up the tree (including this node) are 0. Has valid value only if modCount == IntervalTreeImpl.this.modCount

    private final @NotNull IntervalTreeImpl<E> myTree;

    IntervalNode(@NotNull IntervalTreeImpl<E> tree, @NotNull E key, int start, int end) {
      // maxEnd == 0 so to not disrupt existing maxes
      myTree = tree;
      myRange = TextRangeScalarUtil.toScalarRange(start, end);
      intervals = new SmartList<>(tree.createGetter(key));
      setValid(true);
    }

    @Override
    public IntervalNode<E> getLeft() {
      return (IntervalNode<E>)left;
    }

    @Override
    public IntervalNode<E> getRight() {
      return (IntervalNode<E>)right;
    }

    @Override
    public IntervalNode<E> getParent() {
      return (IntervalNode<E>)parent;
    }

    @Override
    public boolean processAliveKeys(@NotNull Processor<? super E> processor) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < intervals.size(); i++) {
        Supplier<? extends E> interval = intervals.get(i);
        E key = interval.get();
        if (key != null && !processor.process(key)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean hasAliveKey(boolean purgeDead) {
      boolean hasAliveInterval = false;
      for (int i = intervals.size() - 1; i >= 0; i--) {
        Supplier<? extends E> interval = intervals.get(i);
        if (interval.get() != null) {
          hasAliveInterval = true;
          if (purgeDead) {
            continue;
          }
          else {
            break;
          }
        }
        if (purgeDead) {
          myTree.assertUnderWriteLock();
          removeIntervalInternal(i);
        }
      }
      return hasAliveInterval;
    }

    // removes the interval and the node, if node became empty
    // returns true if node was removed
    private boolean removeInterval(@NotNull E key) {
      myTree.checkBelongsToTheTree(key, true);
      myTree.assertUnderWriteLock();
      for (int i = intervals.size() - 1; i >= 0; i--) {
        Supplier<? extends E> interval = intervals.get(i);
        E t = interval.get();
        if (t == key) {
          removeIntervalInternal(i);
          if (intervals.isEmpty()) {
            myTree.removeNode(this);
            return true;
          }
          return false;
        }
      }
      assert false: "interval not found: "+key +"; "+ intervals;
      return false;
    }
    private boolean isAttachedToTree() {
      return isFlagSet(ATTACHED_TO_TREE_FLAG);
    }
    private void setAttachedToTree(boolean attached) {
      setFlag(ATTACHED_TO_TREE_FLAG, attached);
    }

    void removeIntervalInternal(int i) {
      intervals.remove(i);
      if (isAttachedToTree()) {   // for detached node, do not update tree node count
        assert myTree.keySize > 0 : myTree.keySize;
        myTree.keySize--;
      }
    }

    void addInterval(@NotNull E interval) {
      myTree.assertUnderWriteLock();
      intervals.add(myTree.createGetter(interval));
      if (isAttachedToTree()) { // for detached node, do not update tree node count
        myTree.keySize++;
        myTree.setNode(interval, this);
      }
    }

    void addIntervalsFrom(@NotNull IntervalNode<E> otherNode) {
      for (Supplier<? extends E> key : otherNode.intervals) {
        E interval = key.get();
        if (interval != null) addInterval(interval);
      }
    }

    int computeDeltaUpToRoot() {
      restart:
      while (true) { // have to restart on failure to update cached offsets in case of concurrent modification
        if (!isValid()) return 0;
        int treeModCount = myTree.getModCount();
        long packedOffsets = cachedDeltaUpToRoot;
        if (modCount(packedOffsets) == treeModCount) {
          return deltaUpToRoot(packedOffsets);
        }
        try {
          myTree.l.readLock().lock();

          IntervalNode<E> node = this;
          IntervalNode<E> treeRoot = myTree.getRoot();
          if (treeRoot == null) return delta; // someone modified the tree in the meantime
          int deltaUp = 0;
          boolean allDeltasAreNull = true;
          int height = 0;
          long path = 0; // path to this node from the root; 0 bit means we choose left subtree, 1 bit means we choose right subtree
          while (node != treeRoot) {
            long nodePackedOffsets = node.cachedDeltaUpToRoot;
            if (node.isValid() && modCount(nodePackedOffsets) == treeModCount) {
              deltaUp = deltaUpToRoot(nodePackedOffsets) - node.delta;
              allDeltasAreNull = allDeltasUpAreNull(nodePackedOffsets);
              break;
            }
            IntervalNode<E> parent = node.getParent();
            if (parent == null) {
              return deltaUp;  // can happen when remove node and explicitly set valid to true (e.g., in RangeMarkerTree)
            }
            path = (path << 1) |  (parent.getLeft() == node ? 0 : 1);
            node = parent;
            height++;
          }
          // the path to this node should fit to long
          assert height < 63 : height;

          // cache deltas in every node from the root down this
          while (true) {
            if (node.isValid()) {
              int nodeDelta = node.delta;
              deltaUp += nodeDelta;
              allDeltasAreNull &= nodeDelta == 0;
              if (!node.tryToSetCachedValues(deltaUp, allDeltasAreNull, treeModCount)) {
                continue restart;
              }
            }

            if (node == this) break;
            node = (path & 1) == 0 ? node.getLeft() : node.getRight();
            path >>= 1;
            if (node == null) return deltaUp; // can only happen in case of concurrent modification
          }

          assert deltaUp == 0 || !allDeltasAreNull;
          return deltaUp;
        }
        finally {
          myTree.l.readLock().unlock();
        }
      }
    }

    void changeDelta(int change) {
      if (change != 0) {
        setCachedValues(0, false, 0); // deltaUpToRoot is not valid anymore
        delta += change;
      }
    }
    void clearDelta() {
      if (delta != 0) {
        setCachedValues(0, false, 0); // deltaUpToRoot is not valid anymore
        delta = 0;
      }
    }

    public void setRange(long scalarRange) {
      myRange = scalarRange;
    }

    static final byte VALID_FLAG = ATTACHED_TO_TREE_FLAG << 1;
    @Override
    public boolean isValid() {
      return isFlagSet(VALID_FLAG);
    }

    @Override
    public boolean setValid(boolean value) {
      setFlag(VALID_FLAG, value);
      return value;
    }

    @Override
    public int intervalStart() {
      return TextRangeScalarUtil.startOffset(myRange);
    }

    @Override
    public int intervalEnd() {
      return TextRangeScalarUtil.endOffset(myRange);
    }

    public long toScalarRange() {
      return myRange;
    }

    public @NotNull IntervalTreeImpl<E> getTree() {
      return myTree;
    }

    /**
     * packing/unpacking cachedDeltaUpToRoot field parts
     * Bits layout:
     * XXXXXXXXNMMMMMMMM where
     * XXXXXXXX - 31bit int containing cached delta up to the root
     * N        - 1bit flag. If set, then all deltas up to root are null
     * MMMMMMMM - 32bit int containing this node modification count
     */
    private static final AtomicLongFieldUpdater<IntervalNode>
      cachedDeltaUpdater = AtomicLongFieldUpdater.newUpdater(IntervalNode.class, "cachedDeltaUpToRoot");

    private void setCachedValues(int deltaUpToRoot, boolean allDeltaUpToRootAreNull, int modCount) {
      cachedDeltaUpToRoot = packValues(deltaUpToRoot, allDeltaUpToRootAreNull, modCount);
    }

    private static long packValues(long deltaUpToRoot, boolean allDeltaUpToRootAreNull, int modCount) {
      return deltaUpToRoot << 33 | (allDeltaUpToRootAreNull ? 0x100000000L : 0) | modCount;
    }

    private boolean tryToSetCachedValues(int deltaUpToRoot, boolean allDeltasUpAreNull, int treeModCount) {
      if (myTree.getModCount() != treeModCount) return false;
      long newValue = packValues(deltaUpToRoot, allDeltasUpAreNull, treeModCount);
      long oldValue = cachedDeltaUpToRoot;
      return cachedDeltaUpdater.compareAndSet(this, oldValue, newValue);
    }

    private static boolean allDeltasUpAreNull(long packedOffsets) {
      return ((packedOffsets >> 32) & 1) != 0;
    }
    private static int modCount(long packedOffsets) {
      return (int)packedOffsets;
    }
    private static int deltaUpToRoot(long packedOffsets) {
      return (int)(packedOffsets >> 33);
    }

    // finds previous in the in-order traversal
    IntervalNode<E> previous() {
      IntervalNode<E> left = getLeft();
      if (left != null) {
        while (left.getRight() != null) {
          left = left.getRight();
        }
        return left;
      }
      IntervalNode<E> parent = getParent();
      IntervalNode<E> prev = this;
      while (parent != null) {
        if (parent.getRight() == prev) break;
        prev = parent;
        parent = parent.getParent();
      }
      return parent;
    }

    // finds next node in the in-order traversal
    IntervalNode<E> next() {
      IntervalNode<E> right = getRight();
      if (right != null) {
        while (right.getLeft() != null) {
          right = right.getLeft();
        }
        return right;
      }
      IntervalNode<E> parent = getParent();
      IntervalNode<E> prev = this;
      while (parent != null) {
        if (parent.getLeft() == prev) break;
        prev = parent;
        parent = parent.getParent();
      }
      return parent;
    }

    @Override
    public @NonNls String toString() {
      return "Node "+TextRangeScalarUtil.create(myRange) + ": "+intervals;
    }
  }

  private @NotNull Supplier<? extends T> createGetter(@NotNull T interval) {
    return keepIntervalOnWeakReference(interval)
           ? new WeakReferencedGetter<>(interval, myReferenceQueue)
           : new StaticSupplier<>(interval);
  }

  private static final class WeakReferencedGetter<T> extends WeakReference<T> implements Supplier<T> {
    private WeakReferencedGetter(@NotNull T referent, @NotNull ReferenceQueue<? super T> q) {
      super(referent, q);
    }

    @Override
    public @NonNls String toString() {
      return "wRef: " + get();
    }
  }
  private static final class StaticSupplier<T> implements Supplier<T> {
    private final T myT;

    private StaticSupplier(@NotNull T referent) {
      myT = referent;
    }

    @Override
    public T get() {
      return myT;
    }

    @Override
    public @NonNls String toString() {
      return "sRef: " + get();
    }
  }

  void assertUnderWriteLock() {
    if (DEBUG && !ApplicationManagerEx.isInStressTest()) {
      assert isAcquired(l.writeLock()) : l.writeLock();
    }
  }
  private static boolean isAcquired(@NotNull Lock l) {
    @NonNls String s = l.toString();
    return s.contains("Locked by thread");
  }

  private void pushDeltaFromRoot(@Nullable IntervalNode<T> node) {
    if (node != null) {
      long packedOffsets = node.cachedDeltaUpToRoot;
      if (IntervalNode.allDeltasUpAreNull(packedOffsets) && node.isValid() && IntervalNode.modCount(packedOffsets) == getModCount()) return;
      pushDeltaFromRoot(node.getParent());
      pushDelta(node);
    }
  }

  protected boolean keepIntervalOnWeakReference(@NotNull T interval) {
    return true;
  }

  protected abstract @NotNull IntervalNode<T> createNewNode(@NotNull T key, int start, int end,
                                                            boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer);
  protected abstract IntervalNode<T> lookupNode(@NotNull T key);
  protected abstract void setNode(@NotNull T key, @Nullable IntervalNode<T> node);

  private int compareNodes(@NotNull IntervalNode<T> i1, int delta1, @NotNull IntervalNode<T> i2, int delta2, @NotNull List<? super IntervalNode<T>> invalid) {
    if (!i2.hasAliveKey(false)) {
      invalid.add(i2); //gced
    }
    int start1 = i1.intervalStart() + delta1;
    int start2 = i2.intervalStart() + delta2;
    if (start1 != start2) return start1 - start2;
    return compareEqualStartIntervals(i1, i2);
  }

  protected IntervalNode<T> getRoot() {
    return (IntervalNode<T>)root;
  }

  @Override
  public boolean processAll(@NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return process(getRoot(), getModCount(), processor);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean process(@Nullable IntervalNode<T> root,
                          int modCountBefore,
                          @NotNull Processor<? super T> processor) {
    if (root == null) return true;

    WalkingState.TreeGuide<IntervalNode<T>> guide = IntervalTreeGuide.getGuide();
    return WalkingState.processAll(root, guide, node -> {
      if (!node.processAliveKeys(processor)) return false;
      if (getModCount() != modCountBefore) throw new ConcurrentModificationException();
      return true;
    });
  }

  @Override
  public boolean processOverlappingWith(int start, int end, @NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return processOverlappingWith(getRoot(), start, end, getModCount(), 0, processor);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean processOverlappingWith(@Nullable IntervalNode<T> root,
                                         int start,
                                         int end,
                                         int modCountBefore,
                                         int deltaUpToRootExclusive,
                                         @NotNull Processor<? super T> processor) {
    if (root == null) {
      return true;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (start > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processOverlappingWith(root.getLeft(), start, end, modCountBefore, delta, processor)) return false;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, start) <= Math.min(myEndOffset, end);
    if (overlaps) {
      if (!root.processAliveKeys(processor)) return false;
      if (getModCount() != modCountBefore) throw new ConcurrentModificationException();
    }

    if (end < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processOverlappingWith(root.getRight(), start, end, modCountBefore, delta, processor);
  }

  @Override
  public boolean processOverlappingWithOutside(int start, int end, @NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return processOverlappingWithOutside(getRoot(), start, end, getModCount(), 0, processor);
    }
    finally {
      l.readLock().unlock();
    }
  }
  private boolean processOverlappingWithOutside(@Nullable IntervalNode<T> root,
                                                int start,
                                                int end,
                                                int modCountBefore,
                                                int deltaUpToRootExclusive,
                                                @NotNull Processor<? super T> processor) {
    if (root == null) {
      return true;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    int rootMaxEnd = maxEndOf(root, deltaUpToRootExclusive);
    int rootStartOffset = root.intervalStart() + delta;
    int rootEndOffset = root.intervalEnd() + delta;

    if (!processOverlappingWithOutside(root.getLeft(), start, end, modCountBefore, delta, processor)) return false;

    boolean toProcess = rootStartOffset < start || rootEndOffset > end;
    if (toProcess) {
      if (!root.processAliveKeys(processor)) return false;
      if (getModCount() != modCountBefore) throw new ConcurrentModificationException();
    }

    if (rootStartOffset >= start && rootMaxEnd <= end) return true; // cant intersect outside

    return processOverlappingWithOutside(root.getRight(), start, end, modCountBefore, delta, processor);
  }


  @Override
  public boolean processContaining(int offset, @NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return processContaining(getRoot(), offset, getModCount(), 0, processor);
    }
    finally {
      l.readLock().unlock();
    }
  }
  private boolean processContaining(@Nullable IntervalNode<T> root,
                                    int offset,
                                    int modCountBefore,
                                    int deltaUpToRootExclusive,
                                    @NotNull Processor<? super T> processor) {
    if (root == null) {
      return true;
    }
    assert root.isValid();
    int delta = deltaUpToRootExclusive + root.delta;
    if (offset > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processContaining(root.getLeft(), offset, modCountBefore, delta, processor)) return false;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = myStartOffset <= offset && offset < myEndOffset;

    if (overlaps) {
      if (!root.processAliveKeys(processor)) return false;
      if (getModCount() != modCountBefore) throw new ConcurrentModificationException();
    }

    if (offset < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processContaining(root.getRight(), offset, modCountBefore, delta, processor);
  }

  @NotNull
  MarkupIterator<T> overlappingIterator(@NotNull TextRange rangeInterval) {
    l.readLock().lock();

    try {
      int startOffset = rangeInterval.getStartOffset();
      int endOffset = rangeInterval.getEndOffset();
      IntervalNode<T> firstOverlap = findMinOverlappingWith(getRoot(), rangeInterval, getModCount(), 0);
      if (firstOverlap == null) {
        l.readLock().unlock();
        //noinspection unchecked
        return MarkupIterator.EMPTY;
      }
      int firstOverlapDelta = firstOverlap.computeDeltaUpToRoot();
      int firstOverlapStart = firstOverlap.intervalStart() + firstOverlapDelta;
      int modCountBefore = getModCount();

      return new MarkupIterator<T>() {
        private IntervalNode<T> currentNode = firstOverlap;
        private int deltaUpToRootExclusive = firstOverlapDelta-firstOverlap.delta;
        private int indexInCurrentList;
        private T current;

        @Override
        public boolean hasNext() {
          if (current != null) return true;
          if (currentNode == null) return false;

          if (getModCount() != modCountBefore) throw new ConcurrentModificationException();

          if (nextInterval()) {
            return true;
          }

          while (true) {
            currentNode = nextNode(currentNode);
            if (currentNode == null) {
              return false;
            }
            if (overlaps(currentNode, rangeInterval, deltaUpToRootExclusive)) {
              assert currentNode.intervalStart() + deltaUpToRootExclusive + currentNode.delta >= firstOverlapStart;
              indexInCurrentList = 0;
              if (nextInterval()) {
                return true;
              }
            }
          }
        }

        private boolean nextInterval() {
          List<Supplier<? extends T>> intervals = currentNode.intervals;
          while (indexInCurrentList < intervals.size()) {
            T t = intervals.get(indexInCurrentList).get();
            indexInCurrentList++;
            if (t != null) {
              current = t;
              return true;
            }
          }
          indexInCurrentList = 0;
          return false;
        }

        @Override
        public T next() {
          if (!hasNext()) throw new NoSuchElementException();
          T t = current;
          current = null;
          return t;
        }

        @Override
        public T peek() {
          if (!hasNext()) throw new NoSuchElementException();
          return current;
        }

        @Override
        public void remove() {
          throw new IncorrectOperationException();
        }

        @Override
        public void dispose() {
          l.readLock().unlock();
        }

        // next node in in-order traversal
        private IntervalNode<T> nextNode(@NotNull IntervalNode<T> root) {
          assert root.isValid() : root;
          int delta = deltaUpToRootExclusive + root.delta;
          int myMaxEnd = maxEndOf(root, deltaUpToRootExclusive);
          if (startOffset > myMaxEnd) return null; // tree changed

          // try to go right down
          IntervalNode<T> right = root.getRight();
          if (right != null) {
            int rightMaxEnd = maxEndOf(right, delta);
            if (startOffset <= rightMaxEnd) {
              int rightDelta = delta + right.delta;
              IntervalNode<T> left;
              while ((left = right.getLeft()) != null && startOffset <= maxEndOf(left, rightDelta)) {
                right = left;
                rightDelta += right.delta;
              }
              deltaUpToRootExclusive = rightDelta - right.delta;
              return right;
            }
          }

          // go up
          while (true) {
            IntervalNode<T> parent = root.getParent();
            if (parent == null) return null;
            if (parent.intervalStart() + deltaUpToRootExclusive > endOffset) return null; // can't move right
            deltaUpToRootExclusive -= parent.delta;

            if (parent.getLeft() == root) {
              return parent;
            }

            root = parent;
          }
        }
      };
    }
    catch (RuntimeException | Error e) {
      l.readLock().unlock();
      throw e;
    }
  }

  private boolean overlaps(@Nullable IntervalNode<T> root, @NotNull TextRange rangeInterval, int deltaUpToRootExclusive) {
    if (root == null) return false;
    int delta = root.delta + deltaUpToRootExclusive;
    int start = root.intervalStart() + delta;
    int end = root.intervalEnd() + delta;
    return rangeInterval.intersects(start, end);
  }

  @NotNull
  IntervalNode<T> findOrInsert(@NotNull IntervalNode<T> node) {
    assertUnderWriteLock();
    node.setRed();
    node.setParent(null);
    node.setValid(true);
    node.maxEnd = 0;
    node.clearDelta();
    node.setLeft(null);
    node.setRight(null);

    List<IntervalNode<T>> gced = new SmartList<>();
    if (root == null) {
      root = node;
    }
    else {
      IntervalNode<T> current = getRoot();
      while (true) {
        pushDelta(current);
        int compResult = compareNodes(node, 0, current, 0, gced);
        if (compResult == 0) {
          return current;
        }
        if (compResult < 0) {
          if (current.getLeft() == null) {
            current.setLeft(node);
            break;
          }
          current = current.getLeft();
        }
        else /*if (compResult > 0)*/ {
          if (current.getRight() == null) {
            current.setRight(node);
            break;
          }
          current = current.getRight();
        }
      }
      node.setParent(current);
    }
    node.setCachedValues(0, true, getModCount());
    correctMaxUp(node);
    onInsertNode();
    keySize += node.intervals.size();
    insertCase1(node);
    node.setAttachedToTree(true);
    verifyProperties();

    deleteNodes(gced);
    return node;
  }

  private void deleteNodes(@NotNull List<? extends IntervalNode<T>> collectedAway) {
    if (collectedAway.isEmpty()) return;
    try {
      l.writeLock().lock();
      for (IntervalNode<T> node : collectedAway) {
        removeNode(node);
      }
    }
    finally {
      l.writeLock().unlock();
    }
  }

  public @NotNull IntervalTreeImpl.IntervalNode<T> addInterval(@NotNull T interval, int start, int end,
                                                               boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    l.writeLock().lock();
    try {
      if (firingRemove) {
        throw new IncorrectOperationException("Must not add range marker from within removed() listener");
      }
      checkMax(true);
      processReferenceQueue();
      incModCount();
      IntervalNode<T> newNode = createNewNode(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
      IntervalNode<T> insertedNode = findOrInsert(newNode);
      if (insertedNode == newNode) {
        setNode(interval, insertedNode);
      }
      else {
        // merged
        insertedNode.addInterval(interval);
      }
      checkMax(true);
      checkBelongsToTheTree(interval, true);
      return insertedNode;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // returns true if all markers are valid
  void checkMax(boolean assertInvalid) {
    if (VERIFY) {
      doCheckMax(assertInvalid);
    }
  }

  private void doCheckMax(boolean assertInvalid) {
    try {
      l.readLock().lock();

      AtomicBoolean allValid = new AtomicBoolean(true);
      int[] keyCounter = new int[1];
      int[] nodeCounter = new int[1];
      LongSet ids = new LongOpenHashSet(keySize);
      checkMax(getRoot(), 0, assertInvalid, allValid, keyCounter, nodeCounter, ids, true);
      if (assertInvalid) {
        assert nodeSize() == nodeCounter[0] : "node size: "+ nodeSize() +"; actual: "+nodeCounter[0];
        assert keySize == keyCounter[0] : "key size: "+ keySize +"; actual: "+keyCounter[0];
        assert keySize >= nodeSize() : keySize + "; "+nodeSize();
      }
    }
    finally {
      l.readLock().unlock();
    }
  }

  private static final class IntTrinity {
    private final int minStart;
    private final int maxStart;
    private final int maxEnd;

    private IntTrinity(int minStart, int maxStart, int maxEnd) {
      this.minStart = minStart;
      this.maxStart = maxStart;
      this.maxEnd = maxEnd;
    }
  }

  // returns real (minStart, maxStart, maxEnd)
  private @NotNull IntTrinity checkMax(@Nullable IntervalNode<T> root,
                              int deltaUpToRootExclusive,
                              boolean assertInvalid,
                              @NotNull AtomicBoolean allValid,
                              int @NotNull [] keyCounter,
                              int @NotNull [] nodeCounter,
                              @NotNull LongSet ids,
                              boolean allDeltasUpAreNull) {
    if (root == null) return new IntTrinity(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);
    long packedOffsets = root.cachedDeltaUpToRoot;
    if (IntervalNode.modCount(packedOffsets) == getModCount()) {
      assert IntervalNode.allDeltasUpAreNull(packedOffsets) == (root.delta == 0 && allDeltasUpAreNull);
      assert IntervalNode.deltaUpToRoot(packedOffsets) == root.delta + deltaUpToRootExclusive;
    }
    T liveInterval = null;
    for (int i = root.intervals.size() - 1; i >= 0; i--) {
      T t = root.intervals.get(i).get();
      if (t == null) continue;
      liveInterval = t;
      checkBelongsToTheTree(t, false);
      if (((RangeMarkerImpl)t).isValid()) {
        long id = ((RangeMarkerImpl)t).getId();
        boolean added = ids.add(id);
        assert added : t +"\nids:"+ids+"; id="+id+"\n; root.intervals="+root.intervals;
      }
    }
    if (assertInvalid && liveInterval != null) {
      checkBelongsToTheTree(liveInterval, true);
    }

    keyCounter[0]+= root.intervals.size();
    nodeCounter[0]++;
    int delta = deltaUpToRootExclusive + (root.isValid() ? root.delta : 0);
    IntTrinity l = checkMax(root.getLeft(), delta, assertInvalid, allValid, keyCounter, nodeCounter, ids, root.delta == 0 && allDeltasUpAreNull);
    int minLeftStart = l.minStart;
    int maxLeftStart = l.maxStart;
    int maxLeftEnd = l.maxEnd;
    IntTrinity r = checkMax(root.getRight(), delta, assertInvalid, allValid, keyCounter, nodeCounter, ids, root.delta == 0 && allDeltasUpAreNull);
    int maxRightEnd = r.maxEnd;
    int minRightStart = r.minStart;
    int maxRightStart = r.maxStart;
    if (!root.isValid()) {
      allValid.set(false);
      if (assertInvalid) assert false : root;
      return new IntTrinity(Math.min(minLeftStart, minRightStart), Math.max(maxLeftStart, maxRightStart), Math.max(maxRightEnd, maxLeftEnd));
    }
    IntervalNode<T> parent = root.getParent();
    if (parent != null && assertInvalid && root.hasAliveKey(false)) {
      int c = compareNodes(root, delta, parent, delta - root.delta, new SmartList<>());
      assert c != 0;
      assert c < 0 && parent.getLeft() == root || c > 0 && parent.getRight() == root;
    }
    assert delta + root.maxEnd == Math.max(maxLeftEnd, Math.max(maxRightEnd, delta + root.intervalEnd()));
    int myStartOffset = delta + root.intervalStart();
    assert maxLeftStart <= myStartOffset;
    assert minRightStart >= myStartOffset;
    assert myStartOffset >= 0;
    assert minLeftStart == Integer.MAX_VALUE || minLeftStart <= myStartOffset;
    assert maxRightStart == Integer.MIN_VALUE || maxRightStart >= myStartOffset;
    int minStart = Math.min(minLeftStart, myStartOffset);
    int maxStart = Math.max(myStartOffset, Math.max(maxLeftStart, maxRightStart));
    assert minStart <= maxStart;
    return new IntTrinity(minStart, maxStart, root.maxEnd + delta);
  }

  @Override
  protected @NotNull Node<T> maximumNode(@NotNull Node<T> n) {
    IntervalNode<T> root = (IntervalNode<T>)n;
    pushDelta(root.getParent());
    pushDelta(root);
    while (root.getRight() != null) {
      root = root.getRight();
      pushDelta(root);
    }
    return root;
  }

  private void checkBelongsToTheTree(@NotNull T interval, boolean assertInvalid) {
    IntervalNode<T> root = lookupNode(interval);
    if (root == null) return;
    //noinspection NumberEquality
    assert root.getTree() == this : root.getTree() + " ("+root.getTree().getClass()+")"+
                                    "; this: "+this + "("+getClass()+")";
    if (!VERIFY) return;

    if (assertInvalid) {
      assert !root.intervals.isEmpty();
      boolean contains = false;
      for (int i = root.intervals.size() - 1; i >= 0; i--) {
        T key = root.intervals.get(i).get();
        if (key == null) continue;
        contains |= key == interval;
        IntervalNode<T> node = lookupNode(key);
        assert node == root : node;
        //noinspection NumberEquality
        assert node.getTree() == this : node;
      }

      assert contains : root.intervals + "; " + interval;
    }

    IntervalNode<T> e = root;
    while (e.getParent() != null) e = e.getParent();
    assert e == getRoot(); // assert the node belongs to our tree
  }

  @Override
  public boolean removeInterval(@NotNull T interval) {
    if (!((RangeMarkerEx)interval).isValid()) return false;
    l.writeLock().lock();
    try {
      incModCount();
      if (!((RangeMarkerEx)interval).isValid()) return false;
      checkBelongsToTheTree(interval, true);
      checkMax(true);
      processReferenceQueue();

      IntervalNode<T> node = lookupNode(interval);
      if (node == null) return false;

      beforeRemove(interval, node);

      node.removeInterval(interval);
      return true;
    }
    finally {
      setNode(interval, null);
      l.writeLock().unlock();
      fireAfterRemoved(interval);
    }
  }

  // run under the "write lock"
  void removeNode(@NotNull IntervalNode<T> node) {
    deleteNode(node);
    IntervalNode<T> parent = node.getParent();
    correctMaxUp(parent);
  }

  @Override
  protected void deleteNode(@NotNull Node<T> n) {
    assertUnderWriteLock();
    IntervalNode<T> node = (IntervalNode<T>)n;
    pushDeltaFromRoot(node);
    assertAllDeltasAreNull(node);
    super.deleteNode(n);

    keySize -= node.intervals.size();
    assert keySize >= 0 : keySize;
    node.setAttachedToTree(false);
  }

  @Override
  public int size() {
    return keySize;
  }

  // returns true if all deltas involved are still 0
  void pushDelta(@Nullable IntervalNode<T> root) {
    if (root == null || !root.isValid()) return;
    IntervalNode<T> parent = root.getParent();
    assertAllDeltasAreNull(parent);
    int delta = root.delta;
    root.setCachedValues(0, true, 0);
    if (delta == 0) {
      root.setCachedValues(0, true, getModCount());
    }
    else {
      root.setRange(TextRangeScalarUtil.shift(root.toScalarRange(), delta, delta));
      root.maxEnd += delta;
      root.delta = 0;
      //noinspection NonShortCircuitBooleanExpression
      incDelta(root.getLeft(), delta);
      incDelta(root.getRight(), delta);
    }
  }

  // returns true if all deltas involved are still 0
  private void incDelta(@Nullable IntervalNode<T> root, int delta) {
    if (root == null) return;
    if (root.isValid()) {
      root.changeDelta(delta);
    }
    else {
      incDelta(root.getLeft(), delta);
      incDelta(root.getRight(), delta);
    }
  }

  @Override
  protected @NotNull IntervalNode<T> swapWithMaxPred(@NotNull Node<T> root, @NotNull Node<T> maxPred) {
    checkMax(false);
    IntervalNode<T> a = (IntervalNode<T>)root;
    IntervalNode<T> d = (IntervalNode<T>)maxPred;
    boolean aColor = a.isBlack();
    boolean dColor = d.isBlack();
    assert !a.isValid() || a.delta == 0 : a.delta;
    for (IntervalNode<T> n = a.getLeft(); n != null; n = n.getRight()) {
      assert !n.isValid() || n.delta == 0 : n.delta;
    }
    swapNodes(a, d);

    // set range of the key to be deleted, so it won't disrupt maxes
    a.setValid(false);
    a.setColor(dColor);
    d.setColor(aColor);
    correctMaxUp(a);

    checkMax(false);
    assert a.delta == 0 : a.delta;
    assert d.delta == 0 : d.delta;
    return a;
  }
  private void swapNodes(@NotNull IntervalNode<T> n1, @NotNull IntervalNode<T> n2) {
    IntervalNode<T> l1 = n1.getLeft();
    IntervalNode<T> r1 = n1.getRight();
    IntervalNode<T> p1 = n1.getParent();
    IntervalNode<T> l2 = n2.getLeft();
    IntervalNode<T> r2 = n2.getRight();
    IntervalNode<T> p2 = n2.getParent();

    if (p1 != null) {
      if (p1.getLeft() == n1) p1.setLeft(n2); else p1.setRight(n2);
    }
    else {
      root = n2;
    }
    if (p2 != null) {
      if (p2.getLeft() == n2) p2.setLeft(p2 == n1 ? l2 : n1); else p2.setRight(p2 == n1 ? r2 : n1);
    }
    else {
      root = n1;
    }
    n1.setParent(p2 == n1 ? n2 : p2);
    n2.setParent(p1);

    n1.setLeft(l2);
    n2.setLeft(l1 == n2 ? n1 : l1);
    if (l1 != null) l1.setParent(n2 == l1 ? p1 : n2);
    if (r1 != null) r1.setParent(n2);
    n1.setRight(r2);
    n2.setRight(r1);
    if (l2 != null) l2.setParent(n1);
    if (r2 != null) r2.setParent(n1);
  }

  // returns real max endOffset of all intervals below
  private int maxEndOf(@Nullable IntervalNode<T> node, int deltaUpToRootExclusive) {
    if (node == null) {
      return 0;
    }
    if (node.isValid()) {
      return node.maxEnd + node.delta + deltaUpToRootExclusive;
    }
    // since node is invalid, ignore node.delta
    return Math.max(maxEndOf(node.getLeft(), deltaUpToRootExclusive), maxEndOf(node.getRight(), deltaUpToRootExclusive));
  }

  // max of n.left's maxEnd, n.right's maxEnd and its own interval endOffset
  void correctMax(@NotNull IntervalNode<T> node, int deltaUpToRoot) {
    if (!node.isValid()) return;
    int realMax = Math.max(Math.max(maxEndOf(node.getLeft(), deltaUpToRoot), maxEndOf(node.getRight(), deltaUpToRoot)),
                           deltaUpToRoot + node.intervalEnd());
    node.maxEnd = realMax - deltaUpToRoot;
  }

  private void correctMaxUp(@Nullable IntervalNode<T> node) {
    int delta = node == null ? 0 : node.computeDeltaUpToRoot();
    assert delta == 0 : delta;
    while (node != null) {
      if (node.isValid()) {
        int d = node.delta;
        correctMax(node, delta);
        delta -= d;
      }
      node = node.getParent();
    }
    assert delta == 0 : delta;
  }

  @Override
  protected void rotateRight(@NotNull Node<T> n) {
    checkMax(false);
    IntervalNode<T> node1 = (IntervalNode<T>)n;
    IntervalNode<T> node2 = node1.getLeft();
    IntervalNode<T> node3 = node1.getRight();

    IntervalNode<T> parent = node1.getParent();
    int deltaUp = parent == null ? 0 : parent.computeDeltaUpToRoot();
    pushDelta(node1);
    pushDelta(node2);
    pushDelta(node3);

    super.rotateRight(node1);

    if (node3 != null) {
      correctMax(node3, deltaUp);
    }
    correctMax(node1, deltaUp);
    correctMax(node2, deltaUp);
    assertAllDeltasAreNull(node1);
    assertAllDeltasAreNull(node2);
    assertAllDeltasAreNull(node3);
    checkMax(false);
  }

  @Override
  protected void rotateLeft(@NotNull Node<T> n) {
    checkMax(false);
    IntervalNode<T> node1 = (IntervalNode<T>)n;
    IntervalNode<T> node2 = node1.getLeft();
    IntervalNode<T> node3 = node1.getRight();

    IntervalNode<T> parent = node1.getParent();
    int deltaUp = parent == null ? 0 : parent.computeDeltaUpToRoot();
    pushDelta(node1);
    pushDelta(node2);
    pushDelta(node3);
    checkMax(false);
    super.rotateLeft(node1);

    if (node2 != null) {
      correctMax(node2, deltaUp);
    }
    correctMax(node1, deltaUp);
    correctMax(node3, deltaUp);
    assertAllDeltasAreNull(node1);
    assertAllDeltasAreNull(node2);
    assertAllDeltasAreNull(node3);

    checkMax(false);
  }

  @Override
  protected void replaceNode(@NotNull Node<T> node, Node<T> child) {
    IntervalNode<T> myNode = (IntervalNode<T>)node;
    pushDelta(myNode);
    pushDelta((IntervalNode<T>)child);

    super.replaceNode(node, child);
    if (child != null && myNode.isValid()) {
      ((IntervalNode<T>)child).changeDelta(myNode.delta);
      //todo correct max up to root??
    }
  }

  private void assertAllDeltasAreNull(@Nullable IntervalNode<T> node) {
    if (node == null) return;
    if (!node.isValid()) return;
    assert node.delta == 0;
    long packedOffsets = node.cachedDeltaUpToRoot;
    assert IntervalNode.modCount(packedOffsets) != getModCount() || IntervalNode.allDeltasUpAreNull(packedOffsets);
  }

  private IntervalNode<T> findMinOverlappingWith(@Nullable IntervalNode<T> root,
                                                 @NotNull TextRange interval,
                                                 int modCountBefore,
                                                 int deltaUpToRootExclusive) {
    if (root == null) {
      return null;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (interval.getStartOffset() > maxEndOf(root, deltaUpToRootExclusive)) {
      return null; // right of the rightmost interval in the subtree
    }

    IntervalNode<T> inLeft = findMinOverlappingWith(root.getLeft(), interval, modCountBefore, delta);
    if (inLeft != null) return inLeft;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, interval.getStartOffset()) <= Math.min(myEndOffset, interval.getEndOffset());
    if (overlaps) return root;
    if (getModCount() != modCountBefore) throw new ConcurrentModificationException();

    if (interval.getEndOffset() < myStartOffset) {
      return null; // left of the root, cant be in the right subtree
    }

    return findMinOverlappingWith(root.getRight(), interval, modCountBefore, delta);
  }

  void changeData(@NotNull T interval, int start, int end,
                  boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    try {
      l.writeLock().lock();

      IntervalNode<T> node = lookupNode(interval);
      if (node == null) return;
      int before = size();
      boolean nodeRemoved = node.removeInterval(interval);
      assert nodeRemoved || !node.intervals.isEmpty();

      IntervalNode<T> insertedNode = addInterval(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
      assert node != insertedNode;

      int after = size();
      // can be gced
      assert before >= after : before +";" + after;
      checkBelongsToTheTree(interval, true);
      checkMax(true);
    }
    finally {
      l.writeLock().unlock();
    }
  }


  // called under write lock
  private void processReferenceQueue() {
    int dead = 0;
    while (myReferenceQueue.poll() != null) {
      dead++;
    }

    deadReferenceCount += dead;
    if (deadReferenceCount > Math.max(1, size() / 3)) {
      purgeDeadNodes();
      deadReferenceCount = 0;
    }
  }

  private void purgeDeadNodes() {
    assertUnderWriteLock();
    List<IntervalNode<T>> gced = new SmartList<>();
    collectGced(getRoot(), gced);
    deleteNodes(gced);
    checkMax(true);
  }

  @Override
  public void clear() {
    List<T> toRemove = new ArrayList<>();
    processAll(t -> toRemove.add(t));
    for (T t : toRemove) {
      removeInterval(t);
    }
    l.writeLock().lock();
    try {
      // make sure to avoid clearing when there are markers created between "removeInterval" and write lock start
      if (nodeSize() == 0) {
        super.clear();
        keySize = 0;
      }
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private void collectGced(@Nullable IntervalNode<T> root, @NotNull List<? super IntervalNode<T>> gced) {
    if (root == null) return;
    if (!root.hasAliveKey(true)) {
      gced.add(root);
    }
    collectGced(root.getLeft(), gced);
    collectGced(root.getRight(), gced);
  }

  private boolean firingRemove; // accessed under l.writeLock() only

  void fireBeforeRemoved(@NotNull T marker) {
  }
  void fireAfterRemoved(@NotNull T marker) {
  }
  void fireAfterRemoved(@NotNull List<? extends T> markers) {
    for (T marker : markers) {
      fireAfterRemoved(marker);
    }
  }

  // must be called under l.writeLock()
  void beforeRemove(@NotNull T marker, @NotNull IntervalNode<T> node) {
    if (firingRemove) {
      throw new IncorrectOperationException("must not remove range markers from within beforeRemove() listener");
    }
    firingRemove = true;
    try {
      fireBeforeRemoved(marker);
    }
    finally {
      firingRemove = false;
    }
  }

  private static class IntervalTreeGuide<T extends MutableInterval> implements WalkingState.TreeGuide<IntervalNode<T>> {
    private static final IntervalTreeGuide<?> INSTANCE = new IntervalTreeGuide<>();
    private static @NotNull <T> WalkingState.TreeGuide<IntervalNode<T>> getGuide() {
      //noinspection unchecked,rawtypes
      return (WalkingState.TreeGuide)INSTANCE;
    }
    @Override
    public IntervalNode<T> getNextSibling(@NotNull IntervalNode<T> element) {
      IntervalNode<T> parent = element.getParent();
      if (parent == null) return null;
      return parent.getLeft() == element ? parent.getRight() : null;
    }

    @Override
    public IntervalNode<T> getPrevSibling(@NotNull IntervalNode<T> element) {
      IntervalNode<T> parent = element.getParent();
      if (parent == null) return null;
      return parent.getRight() == element ? parent.getLeft() : null;
    }

    @Override
    public IntervalNode<T> getFirstChild(@NotNull IntervalNode<T> element) {
      IntervalNode<T> left = element.getLeft();
      return left == null ? element.getRight() : left;
    }

    @Override
    public IntervalNode<T> getParent(@NotNull IntervalNode<T> element) {
      return element.getParent();
    }
  }

  public int maxHeight() {
    return maxHeight(root);
  }

  private int maxHeight(@Nullable Node<T> root) {
    return root == null ? 0 : 1 + Math.max(maxHeight(root.left), maxHeight(root.right));
  }

  // combines iterators for two trees in one using the specified comparator
  static @NotNull <T> MarkupIterator<T> mergingOverlappingIterator(@NotNull IntervalTreeImpl<T> tree1,
                                                          @NotNull TextRange tree1Range,
                                                          @NotNull IntervalTreeImpl<T> tree2,
                                                          @NotNull TextRange tree2Range,
                                                          @NotNull Comparator<? super T> comparator) {
    MarkupIterator<T> exact = tree1.overlappingIterator(tree1Range);
    MarkupIterator<T> lines = tree2.overlappingIterator(tree2Range);
    return MarkupIterator.mergeIterators(exact, lines, comparator);
  }

  T findRangeMarkerAfter(@NotNull T marker) {
    l.readLock().lock();
    try {
      IntervalNode<T> node = lookupNode(marker);

      boolean foundMarker = false;
      while (node != null) {
        List<Supplier<? extends T>> intervals = node.intervals;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < intervals.size(); i++) {
          Supplier<? extends T> interval = intervals.get(i);
          T m = interval.get();
          if (m == null) continue;
          if (m == marker) {
            foundMarker = true;
          }
          else if (foundMarker) {
            // found next to marker
            return m;
          }
        }
        node = node.next();
        foundMarker = true; // protection against sudden removal of marker
      }
      return null;
    }
    finally {
      l.readLock().unlock();
    }
  }

  T findRangeMarkerBefore(@NotNull T marker) {
    l.readLock().lock();
    try {
      IntervalNode<T> node = lookupNode(marker);

      boolean foundMarker = false;
      while (node != null) {
        List<Supplier<? extends T>> intervals = node.intervals;
        for (int i = intervals.size() - 1; i >= 0; i--) {
          Supplier<? extends T> interval = intervals.get(i);
          T m = interval.get();
          if (m == null) continue;
          if (m == marker) {
            foundMarker = true;
          }
          else if (foundMarker) {
            // found next to marker
            return m;
          }
        }
        node = node.previous();
        foundMarker = true; // protection against sudden removal of marker
      }
      return null;
    }
    finally {
      l.readLock().unlock();
    }
  }

  String dumpState() {
    StringBuilder b = new StringBuilder("[\n");
    processAll(i -> {
      b.append(' ').append(i).append('\n');
      return true;
    });
    return b.append("]").toString();
  }
}
