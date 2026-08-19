// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEventDispatcher;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.RangeMarkerStorage;
import com.intellij.openapi.editor.impl.marker.MarkerSpec;
import com.intellij.openapi.editor.impl.marker.PMarker;
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class RangeMarkerStorageImpl implements RangeMarkerStorage {
  private final @NotNull RangeMarkerTree<RangeMarkerEx> myRangeMarkers;
  private final @NotNull RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers;
  private final @NotNull DocumentImpl myDocument;

  RangeMarkerStorageImpl(@NotNull DocumentEventDispatcher dispatcher, @NotNull DocumentImpl document) {
    myRangeMarkers = new RangeMarkerTree<>(dispatcher);
    myPersistentRangeMarkers = new PersistentRangeMarkerTree(dispatcher);
    myDocument = document;
  }
  private static class Holder {
    private static boolean USE_PMARKER_IMPLEMENTATION = Registry.is("editor.range.marker.use.pmarker.internal");
  }
  @Override
  public @NotNull RangeMarkerEx createRangeMarker(@NotNull DocumentEx hostDocument,
                                                  int startOffset,
                                                  int endOffset,
                                                  boolean surviveOnExternalChange) {
    if (surviveOnExternalChange) {
      return new PersistentRangeMarker(hostDocument, startOffset, endOffset, true);
    }
    if (Holder.USE_PMARKER_IMPLEMENTATION) {
      return SnapshotMarkerEngineImpl.INSTANCE.createRangeMarker(hostDocument, ((DocumentImpl)hostDocument).getCore().snapshot(), startOffset, endOffset, new MarkerSpec(false, false, false));
    }
    return new RangeMarkerImpl(hostDocument, startOffset, endOffset, true, false);
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
    if (rangeMarker instanceof PMarker) {
      return SnapshotMarkerEngineImpl.INSTANCE.removeRangeMarker((PMarker)rangeMarker, null);
    }
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
    // TODO remove when all implementations ported to SnapshotMarkerEngineImpl
    TextRange interval = new ProperTextRange(start, end);
    try (MarkupIterator<RangeMarkerEx> treeIterator =
           IntervalTreeImpl.mergingOverlappingIterator(myRangeMarkers, interval,
                                                       myPersistentRangeMarkers, interval,
                                                       tastePreference, RangeMarker.BY_START_OFFSET)) {
      if (!ContainerUtil.process(treeIterator, processor)) {
        return false;
      }
    }
    return SnapshotMarkerEngineImpl.INSTANCE.processRangeMarkersOverlappingWith(myDocument.getCore().snapshot(), start, end, tastePreference, processor);
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
  public static <E extends Throwable> void usePMarkerImplementationIn(@NotNull ThrowableRunnable<E> runnable) throws E {
    boolean old = Holder.USE_PMARKER_IMPLEMENTATION;
    Holder.USE_PMARKER_IMPLEMENTATION = true;
    try {
      runnable.run();
    }
    finally {
      Holder.USE_PMARKER_IMPLEMENTATION = old;
    }
  }
}
