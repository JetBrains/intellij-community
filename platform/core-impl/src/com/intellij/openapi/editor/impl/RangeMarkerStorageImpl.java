// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEventDispatcher;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.RangeMarkerStorage;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class RangeMarkerStorageImpl implements RangeMarkerStorage {
  private final @NotNull RangeMarkerTree<RangeMarkerEx> myRangeMarkers;
  private final @NotNull RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers;

  RangeMarkerStorageImpl(@NotNull DocumentEventDispatcher dispatcher) {
    myRangeMarkers = new RangeMarkerTree<>(dispatcher);
    myPersistentRangeMarkers = new PersistentRangeMarkerTree(dispatcher);
  }

  @Override
  public @NotNull RangeMarkerEx createRangeMarker(@NotNull DocumentEx hostDocument,
                                                  int startOffset,
                                                  int endOffset,
                                                  boolean surviveOnExternalChange) {
    return surviveOnExternalChange
           ? new PersistentRangeMarker(hostDocument, startOffset, endOffset, true)
           : new RangeMarkerImpl(hostDocument, startOffset, endOffset, true, false);
  }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    treeFor(rangeMarker).addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, false, layer);
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return treeFor(rangeMarker).removeInterval(rangeMarker);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    return processDeliciousRangeMarkersOverlappingWith(start, end, (byte)0, processor);
  }

  boolean processDeliciousRangeMarkersOverlappingWith(int start,
                                                      int end,
                                                      byte tastePreference,
                                                      @NotNull Processor<? super RangeMarker> processor) {
    TextRange interval = new ProperTextRange(start, end);
    try (MarkupIterator<RangeMarkerEx> iterator =
           IntervalTreeImpl.mergingOverlappingIterator(myRangeMarkers, interval,
                                                       myPersistentRangeMarkers, interval,
                                                       tastePreference,
                                                       RangeMarker.BY_START_OFFSET)) {
      return ContainerUtil.process(iterator, processor);
    }
  }

  @Override
  public void restoreRangeMarkersFromFile(@NotNull VirtualFile source, @NotNull DocumentEx target, int tabSize) {
    RMTreeReference.getSaveRMTree(source, target, myRangeMarkers, myPersistentRangeMarkers, tabSize);
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

  private @NotNull RangeMarkerTree<RangeMarkerEx> treeFor(@NotNull RangeMarkerEx rangeMarker) {
    return (rangeMarker instanceof PersistentRangeMarker) ? myPersistentRangeMarkers : myRangeMarkers;
  }
}
