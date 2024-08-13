// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cache for highlighters scheduled for removal.
 * You call {@link #recycleHighlighter} to put unused highlighter into the cache
 * and then call {@link #pickupHighlighterFromGarbageBin} (if there is a sudden need for fresh highlighter with specified offsets) to remove it from the cache to re-initialize and re-use.
 * In the end, remaining highlighters are disposed (while maintaining invariant that these should be synchronized with {@link HighlightInfoUpdater} internal data structures)
 * NOT THREAD-SAFE
 */
final class ManagedHighlighterRecycler {
  private final Long2ObjectMap<List<InvalidPsi>> incinerator = new Long2ObjectOpenHashMap<>();  // range -> list of highlighters in this range; these are managed highlighters (ones which are registered in HighlightInfoUpdaterImpl)
  @NotNull private final HighlightingSession myHighlightingSession;
  @NotNull private final HighlightInfoUpdaterImpl myHighlightInfoUpdater;

  /** do not instantiate, use {@link #runWithRecycler} instead */
  private ManagedHighlighterRecycler(@NotNull HighlightingSession session) {
    myHighlightingSession = session;
    myHighlightInfoUpdater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(session.getProject());
  }

  // return true if RH is successfully recycled, false if race condition intervened
  synchronized void recycleHighlighter(@NotNull PsiElement psiElement, @NotNull HighlightInfo info) {
    assert info.isFromHighlightVisitor() || info.isFromAnnotator() || info.isFromInspection() || info.isInjectionRelated(): info;
    assert info.highlighter != null;
    RangeHighlighterEx highlighter = info.highlighter;
    if (UpdateHighlightersUtil.LOG.isDebugEnabled()) {
      UpdateHighlightersUtil.LOG.debug("recycleHighlighter " + highlighter + HighlightInfoUpdaterImpl.currentProgressInfo());
    }
    long range = ((RangeMarkerImpl)highlighter).getScalarRange();
    incinerator.computeIfAbsent(range, __ -> new ArrayList<>()).add(new InvalidPsi(psiElement, info));
  }

  // null means no highlighter found in the cache
  @Nullable
  synchronized public InvalidPsi pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer) {
    long range = TextRangeScalarUtil.toScalarRange(startOffset, endOffset);
    List<InvalidPsi> collection = incinerator.get(range);
    if (collection != null) {
      for (int i = 0; i < collection.size(); i++) {
        InvalidPsi psi = collection.get(i);
        RangeHighlighterEx highlighter = psi.info().highlighter;
        if (highlighter.isValid() && highlighter.getLayer() == layer) {
          collection.remove(psi);
          if (collection.isEmpty()) {
            incinerator.remove(range);
          }
          if (UpdateHighlightersUtil.LOG.isDebugEnabled()) {
            UpdateHighlightersUtil.LOG.debug(
              "pickupHighlighterFromGarbageBin pickedup:" + highlighter + HighlightInfoUpdaterImpl.currentProgressInfo());
          }
          myHighlightInfoUpdater.removeFromDataAtomically(psi, highlighter, myHighlightingSession);
          return psi;
        }
      }
    }
    return null;
  }
  //
  @NotNull
  synchronized Collection<? extends InvalidPsi> forAllInGarbageBin() {
    return ContainerUtil.flatten(incinerator.values());
  }

  /**
   * - create {@link ManagedHighlighterRecycler},
   * - run {@code consumer} which usually calls {@link ManagedHighlighterRecycler#recycleHighlighter} and {@link HighlighterRecycler#pickupHighlighterFromGarbageBin}
   * - and then incinerate all remaining highlighters, or in the case of PCE, release them back to recyclable state
   */
  static void runWithRecycler(@NotNull HighlightingSession session, @NotNull Consumer<? super ManagedHighlighterRecycler> consumer) {
    ManagedHighlighterRecycler recycler = new ManagedHighlighterRecycler(session);
    consumer.accept(recycler);
    recycler.myHighlightInfoUpdater.incinerateAndRemoveFromDataAtomically(recycler.myHighlightingSession, recycler);
  }
}
