// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEventDispatcher;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Storage for guarded blocks. Uses [RangeMarkerStorageImpl] for them
final class GuardedBlocksImpl implements GuardedBlocks {
  private static final Logger LOG = Logger.getInstance(GuardedBlocksImpl.class);

  @NotNull private final RangeMarkerStorageImpl myRangeMarkerStorage;
  private final CachedBlocks myCachedGuardedBlocks;

  GuardedBlocksImpl(@NotNull RangeMarkerStorageImpl rangeMarkerStorage, @NotNull DocumentEventDispatcher dispatcher) {
    myRangeMarkerStorage = rangeMarkerStorage;
    myCachedGuardedBlocks = new CachedBlocks(dispatcher);
  }

  // store cached guarded blocks in a separate class to avoid strong-referencing the whole GuardedBlocksImpl(and thus retaining the document) via PrioritizedDocumentListener
  private static class CachedBlocks implements PrioritizedDocumentListener {
    private List<RangeMarker> myBlocks;

    CachedBlocks(@NotNull DocumentEventDispatcher dispatcher) {
      dispatcher.addDocumentListener(this);
    }
    @Override
    public int getPriority() {
      return EditorDocumentPriorities.RANGE_MARKER;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      DocumentImpl document = (DocumentImpl)event.getDocument();
      if (SnapshotMarkerEngineImpl.INSTANCE.hasInvalidatedMarkers(document.getCore().snapshot())) {
        myBlocks = null;
      }
    }
  }

  @Override
  public @NotNull RangeMarkerEx createGuardedBlock(@NotNull DocumentEx hostDocument, int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    RangeMarkerEx block;
    if (RangeMarkerStorageImpl.Holder.USE_PMARKER_IMPLEMENTATION) {
      block = SnapshotGuardedBlock.create((DocumentImpl)hostDocument, startOffset, endOffset);
    }
    else {
      block = new GuardedBlock(hostDocument, startOffset, endOffset);
    }
    myCachedGuardedBlocks.myBlocks = null;
    return block;
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    if (!GuardedBlock.isGuard(block)) {
      throw new IllegalArgumentException("range marker is not a guarded block: "+block);
    }
    block.dispose();
    myCachedGuardedBlocks.myBlocks = null;
  }

  @Override
  public @NotNull @UnmodifiableView List<RangeMarker> getGuardedBlocks() {
    List<RangeMarker> cachedBlocks = myCachedGuardedBlocks.myBlocks;
    if (cachedBlocks != null) {
      return cachedBlocks;
    }
    List<RangeMarker> blocks = collectAllGuardedBlocks();
    myCachedGuardedBlocks.myBlocks = blocks;
    return blocks;
  }

  @Override
  public @Nullable RangeMarker getOffsetGuard(int offset) {
    return getRangeGuard(offset, offset);
  }

  @Override
  public @Nullable RangeMarker getRangeGuard(int start, int end) {
    Ref<RangeMarker> blockRef = new Ref<>();
    myRangeMarkerStorage.processDeliciousRangeMarkersOverlappingWith(start, end, GuardedBlock.GUARD_BLOCK_FLAVOR_FLAG, block -> {
      if (rangesIntersect(start, end, true, true,
                          block.getStartOffset(), block.getEndOffset(),
                          block.isGreedyToLeft(),
                          block.isGreedyToRight())) {
        blockRef.set(block);
        return false;
      }
      return true;
    });
    return blockRef.get();
  }

  private @NotNull @UnmodifiableView List<RangeMarker> collectAllGuardedBlocks() {
    List<RangeMarker> blocks = new ArrayList<>();
    myRangeMarkerStorage.processDeliciousRangeMarkersOverlappingWith(0, Integer.MAX_VALUE, GuardedBlock.GUARD_BLOCK_FLAVOR_FLAG, block -> blocks.add(block));
    // prevent the users from being misled that modifying this list affects actual guarded blocks
    return Collections.unmodifiableList(blocks);
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
