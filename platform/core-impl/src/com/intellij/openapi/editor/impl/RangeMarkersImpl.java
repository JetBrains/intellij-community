// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEventDispatcher;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.RangeMarkers;
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy;
import com.intellij.openapi.editor.impl.marker.MarkerSpec;
import com.intellij.openapi.editor.impl.marker.PMarker;
import com.intellij.openapi.editor.impl.marker.PersistentMarkerPolicy;
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

@ApiStatus.Internal
public final class RangeMarkersImpl implements RangeMarkers {
  private final @Nullable RangeMarkerTree<RangeMarkerEx> myRangeMarkers;
  private final @Nullable RangeMarkerTree<RangeMarkerEx> myPersistentRangeMarkers;
  private final @NotNull DocumentImpl myDocument;

  RangeMarkersImpl(@NotNull DocumentEventDispatcher dispatcher, @NotNull DocumentImpl document) {
    if (RangeMarkers.Holder.USE_PMARKER_IMPLEMENTATION) {
      myRangeMarkers = null;
      myPersistentRangeMarkers = null;
    }
    else {
      myRangeMarkers = new RangeMarkerTree<>(dispatcher);
      myPersistentRangeMarkers = new PersistentRangeMarkerTree(dispatcher);
    }
    myDocument = document;
  }
  @Override
  public @NotNull RangeMarkerEx createRangeMarker(@NotNull DocumentEx hostDocument,
                                                  int startOffset,
                                                  int endOffset,
                                                  boolean surviveOnExternalChange) {
    if (myRangeMarkers == null) {
      MarkerSpec spec = new MarkerSpec(false, false, false,
                                       surviveOnExternalChange ? PersistentMarkerPolicy.INSTANCE : DefaultMarkerPolicy.INSTANCE);
      return SnapshotMarkerEngineImpl.INSTANCE.createRangeMarker(
        hostDocument,
        ((DocumentImpl)hostDocument).getCore().snapshot(),
        startOffset,
        endOffset,
        spec,
        false
      );
    }
    if (surviveOnExternalChange) {
      return new PersistentRangeMarker(hostDocument, startOffset, endOffset, true);
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

  @Override
  public boolean processDeliciousRangeMarkersOverlappingWith(int start,
                                                             int end,
                                                             byte tastePreference,
                                                             @NotNull Processor<? super RangeMarker> processor) {
    RangeMarkerTree<RangeMarkerEx> rangeMarkers = myRangeMarkers;
    if (rangeMarkers == null) {
      return SnapshotMarkerEngineImpl.INSTANCE.processRangeMarkersOverlappingWith(
        myDocument.getCore().snapshot(), start, end, tastePreference, processor
      );
    }

    RangeMarkerTree<RangeMarkerEx> persistentRangeMarkers = Objects.requireNonNull(myPersistentRangeMarkers);
    TextRange interval = new ProperTextRange(start, end);
    try (MarkupIterator<RangeMarkerEx> treeIterator =
           IntervalTreeImpl.mergingOverlappingIterator(rangeMarkers, interval,
                                                       persistentRangeMarkers, interval,
                                                       tastePreference, RangeMarker.BY_START_OFFSET)) {
      return ContainerUtil.process(treeIterator, processor);
    }
  }

  @Override
  public void restoreRangeMarkersFromFile(@NotNull VirtualFile source, @NotNull DocumentEx target, int tabSize) {
    RangeMarkerTree<RangeMarkerEx> rangeMarkers = myRangeMarkers;
    if (rangeMarkers != null) {
      RMTreeReference.getSaveRMTree(source, target, rangeMarkers, Objects.requireNonNull(myPersistentRangeMarkers), tabSize);
    }
  }

  @TestOnly
  @Override
  public int getRangeMarkersSize() {
    RangeMarkerTree<RangeMarkerEx> rangeMarkers = myRangeMarkers;
    return rangeMarkers == null ? 0 : rangeMarkers.size() + Objects.requireNonNull(myPersistentRangeMarkers).size();
  }

  @TestOnly
  @Override
  public int getRangeMarkersNodeSize() {
    RangeMarkerTree<RangeMarkerEx> rangeMarkers = myRangeMarkers;
    return rangeMarkers == null ? 0 : rangeMarkers.nodeSize() + Objects.requireNonNull(myPersistentRangeMarkers).nodeSize();
  }

  private @NotNull RangeMarkerTree<RangeMarkerEx> treeFor(@NotNull RangeMarkerEx rangeMarker) {
    return Objects.requireNonNull(rangeMarker instanceof PersistentRangeMarker ? myPersistentRangeMarkers : myRangeMarkers);
  }
  public static <E extends Throwable> void usePMarkerImplementationIn(@NotNull ThrowableRunnable<E> runnable) throws E {
    usePMarkerImplementationIn(true, runnable);
  }
  public static <E extends Throwable> void usePMarkerImplementationIn(boolean usePMarkerImpl, @NotNull ThrowableRunnable<E> runnable) throws E {
    boolean old = Holder.USE_PMARKER_IMPLEMENTATION;
    Holder.USE_PMARKER_IMPLEMENTATION = usePMarkerImpl;
    try {
      runnable.run();
    }
    finally {
      Holder.USE_PMARKER_IMPLEMENTATION = old;
    }
  }
}
