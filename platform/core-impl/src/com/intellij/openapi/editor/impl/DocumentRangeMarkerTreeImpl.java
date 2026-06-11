// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEventDispatcher;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.DocumentRangeMarkerTree;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class DocumentRangeMarkerTreeImpl implements DocumentRangeMarkerTree {
  private static final Logger LOG = Logger.getInstance(DocumentRangeMarkerTreeImpl.class);
  private static final List<RangeMarker> GUARDED_IN_PROGRESS = new ArrayList<>(0);

  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers;
  private final RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers;
  private final AtomicReference<List<RangeMarker>> myCachedGuardedBlocks;

  DocumentRangeMarkerTreeImpl(@NotNull DocumentEventDispatcher dispatcher) {
    myRangeMarkers = new RangeMarkerTree<>(dispatcher);
    myPersistentRangeMarkers = new PersistentRangeMarkerTree(dispatcher);
    myCachedGuardedBlocks = new AtomicReference<>();
  }

  @Override
  public @NotNull RangeMarkerEx createRangeMarker(
    @NotNull DocumentEx hostDocument,
    int startOffset,
    int endOffset,
    boolean surviveOnExternalChange
  ) {
    return surviveOnExternalChange
           ? new PersistentRangeMarker(hostDocument, startOffset, endOffset, true)
           : new RangeMarkerImpl(hostDocument, startOffset, endOffset, true, false);
  }

  @Override
  public void registerRangeMarker(
    @NotNull RangeMarkerEx rangeMarker,
    int start,
    int end,
    boolean greedyToLeft,
    boolean greedyToRight,
    int layer
  ) {
    treeFor(rangeMarker).addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, false, layer);
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return treeFor(rangeMarker).removeInterval(rangeMarker);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    TextRange interval = new ProperTextRange(start, end);
    try(MarkupIterator<RangeMarkerEx> iterator = IntervalTreeImpl.mergingOverlappingIterator(
      myRangeMarkers, interval, myPersistentRangeMarkers, interval, (byte)0, RangeMarker.BY_START_OFFSET
    )) {
      return ContainerUtil.process(iterator, processor);
    }
  }

  @Override
  public void restoreRangeMarkersFromFile(@NotNull VirtualFile source, @NotNull DocumentEx target, int tabSize) {
    RMTreeReference.getSaveRMTree(source, target, myRangeMarkers, myPersistentRangeMarkers, tabSize);
  }

  @Override
  public @NotNull RangeMarkerEx createGuardedBlock(@NotNull DocumentEx hostDocument, int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    GuardedBlock block = new GuardedBlock(hostDocument, startOffset, endOffset);
    myCachedGuardedBlocks.set(null);
    return block;
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    if (!GuardedBlock.isGuarded(block)) {
      throw new IllegalArgumentException("range markers is not a guarded block");
    }
    block.dispose();
    myCachedGuardedBlocks.set(null);
  }

  @Override
  public @NotNull List<RangeMarker> getGuardedBlocks() {
    List<RangeMarker> cachedBlocks = myCachedGuardedBlocks.get();
    if (cachedBlocks != null && cachedBlocks != GUARDED_IN_PROGRESS) {
      return cachedBlocks;
    }
    if (myCachedGuardedBlocks.compareAndSet(null, GUARDED_IN_PROGRESS)) {
      List<RangeMarker> blocks = collectGuardedBlocks();
      if (!myCachedGuardedBlocks.compareAndSet(GUARDED_IN_PROGRESS, blocks)) {
        // another thread created or removed a block, force recalculation
        myCachedGuardedBlocks.set(null);
      }
      return blocks;
    }
    // another thread is already collecting the result, return without commiting
    return collectGuardedBlocks();
  }

  @Override
  public @Nullable RangeMarkerEx getOffsetGuard(int offset) {
    Ref<RangeMarkerEx> blockRef = new Ref<>();
    myPersistentRangeMarkers.processContaining(offset, GuardedBlock.processor(block -> {
      blockRef.set(block);
      return false;
    }));
    return blockRef.get();
  }

  @Override
  public @Nullable RangeMarkerEx getRangeGuard(int start, int end) {
    Ref<RangeMarkerEx> blockRef = new Ref<>();
    myPersistentRangeMarkers.processOverlappingWith(
      start,
      end,
      GuardedBlock.processor(block -> {
        if (rangesIntersect(start, end, true, true,
                            block.getStartOffset(), block.getEndOffset(), block.isGreedyToLeft(), block.isGreedyToRight())) {
          blockRef.set(block);
          return false;
        }
        return true;
      })
    );
    return blockRef.get();
  }

  @TestOnly
  @Override
  public int getRangeMarkersSize() {
    return myRangeMarkers.size() + myPersistentRangeMarkers.size();
  }

  @TestOnly
  @Override
  public int getRangeMarkersNodeSize() {
    return myRangeMarkers.nodeSize() + myPersistentRangeMarkers.nodeSize();
  }

  private @NotNull @UnmodifiableView List<RangeMarker> collectGuardedBlocks() {
    List<RangeMarker> blocks = new ArrayList<>();
    myPersistentRangeMarkers.processAll(GuardedBlock.processor(block -> {
      blocks.add(block);
      return true;
    }));
    // prevent the users from being misled that modifying this list affects actual guarded blocks
    return Collections.unmodifiableList(blocks);
  }

  private RangeMarkerTree<RangeMarkerEx> treeFor(@NotNull RangeMarkerEx rangeMarker) {
    return (rangeMarker instanceof PersistentRangeMarker) ? myPersistentRangeMarkers : myRangeMarkers;
  }

  @SuppressWarnings("SameParameterValue")
  private static boolean rangesIntersect(
    int start0, int end0, boolean start0Inclusive, boolean end0Inclusive,
    int start1, int end1, boolean start1Inclusive, boolean end1Inclusive
  ) {
    if (start0 > start1 || start0 == start1 && !start0Inclusive) {
      if (end1 == start0) {
        return start0Inclusive && end1Inclusive;
      }
      return end1 > start0;
    }
    if (end0 == start1) {
      return start1Inclusive && end0Inclusive;
    }
    return end0 > start1;
  }
}
