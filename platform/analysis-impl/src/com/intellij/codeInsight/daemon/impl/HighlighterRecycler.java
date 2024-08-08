// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cache for highlighters scheduled for removal.
 * You call {@link #recycleHighlighter} to put unused highlighter into the cache
 * and then call {@link #pickupHighlighterFromGarbageBin} (if there is a sudden need for fresh highlighter with specified offsets) to remove it from the cache to re-initialize and re-use.
 * In the end, call {@link #incinerateObsoleteHighlighters} to finally remove highlighters left in the cache that nobody picked up and reused.
 * NOT THREAD-SAFE
 */
final class HighlighterRecycler implements HighlighterRecyclerPickup {
  private final Long2ObjectMap<List<RangeHighlighterEx>> incinerator = new Long2ObjectOpenHashMap<>();
  private static final Key<ProgressIndicator> BEING_RECYCLED_KEY = Key.create("RECYCLED_KEY"); // set when the highlighter is just recycled, but not yet transferred to EDT to change its attributes. used to prevent double recycling the same RH
  @NotNull private final HighlightingSession myHighlightingSession;

  /** do not instantiate, use {@link #runWithRecycler} instead */
  private HighlighterRecycler(@NotNull HighlightingSession session) {
    myHighlightingSession = session;
  }

  // return true if RH is successfully recycled, false if race condition intervened
  synchronized boolean recycleHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (!highlighter.isValid()) {
      return false;
    }
    UserDataHolderEx h = (UserDataHolderEx)highlighter;
    ProgressIndicator oldIndicator = h.getUserData(BEING_RECYCLED_KEY);
    ProgressIndicator myIndicator = myHighlightingSession.getProgressIndicator();
    boolean replaced;
    if (oldIndicator != myIndicator && !myIndicator.isCanceled() && (oldIndicator == null || oldIndicator.isCanceled())) {
      replaced = h.replace(BEING_RECYCLED_KEY, oldIndicator, myIndicator);
    }
    else {
      ProgressManager.checkCanceled(); // two sessions are going to overlap and fight for recycling highlighters, cancel one of them
      replaced = false;
    }
    if (UpdateHighlightersUtil.LOG.isDebugEnabled()) {
      UpdateHighlightersUtil.LOG.debug("recycleHighlighter success="+replaced+": "+highlighter+
                                       (oldIndicator!=myIndicator && oldIndicator != null? "; oldIndicator="+System.identityHashCode(oldIndicator) + (oldIndicator.isCanceled() ? "X" : "V") : "")+
                                       "; myIndicator="+System.identityHashCode(myIndicator) + (myIndicator.isCanceled() ? "X" : "V"));
    }
    if (replaced) {
      long range = ((RangeMarkerImpl)highlighter).getScalarRange();
      incinerator.computeIfAbsent(range, __ -> new ArrayList<>()).add(highlighter);
      return true;
    }
    return false;
  }

  // null means no highlighter found in the cache
  @Override
  @Nullable
  synchronized public RangeHighlighterEx pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer) {
    long range = TextRangeScalarUtil.toScalarRange(startOffset, endOffset);
    List<RangeHighlighterEx> collection = incinerator.get(range);
    if (collection != null) {
      for (int i = 0; i < collection.size(); i++) {
        RangeHighlighterEx highlighter = collection.get(i);
        if (highlighter.isValid() && highlighter.getLayer() == layer) {
          collection.remove(i);
          if (collection.isEmpty()) {
            incinerator.remove(range);
          }
          highlighter.putUserData(BEING_RECYCLED_KEY, null);
          return highlighter;
        }
      }
    }
    return null;
  }
  //
  @NotNull
  synchronized Collection<? extends RangeHighlighter> forAllInGarbageBin() {
    return ContainerUtil.flatten(incinerator.values());
  }

  // mark all remaining highlighters as not "recycled", to avoid double creation
  synchronized void releaseHighlighters() {
    if (HighlightInfoUpdaterImpl.LOG.isDebugEnabled()) {
      if (!incinerator.isEmpty()) {
        HighlightInfoUpdaterImpl.LOG.debug("releaseHighlighters: (" + incinerator.size() + ")" + "; progress=" + System.identityHashCode(ProgressManager.getGlobalProgressIndicator())+(ProgressManager.getGlobalProgressIndicator().isCanceled() ? "X" : "V"));
      }
    }
    for (RangeHighlighter highlighter : forAllInGarbageBin()) {
      boolean replaced = ((UserDataHolderEx)highlighter).replace(BEING_RECYCLED_KEY, myHighlightingSession.getProgressIndicator(), null);
      if (HighlightInfoUpdaterImpl.LOG.isDebugEnabled()) {
        HighlightInfoUpdaterImpl.LOG.debug("HighlightersRecycler.releaseHighlighter: "+highlighter+"; replaced="+replaced);
      }
    }
  }

  static boolean isBeingRecycled(@NotNull RangeHighlighter highlighter) {
    ProgressIndicator indicator = highlighter.getUserData(BEING_RECYCLED_KEY);
    return indicator != null && !indicator.isCanceled();
  }

  @Override
  public @Nullable RangeHighlighterEx pickupFileLevelRangeHighlighter(int fileTextLength) {
    return pickupHighlighterFromGarbageBin(0, fileTextLength, DaemonCodeAnalyzerEx.ANY_GROUP);
  }

  // dispose all highlighters (still) stored in this recycler, if possible
  synchronized private boolean incinerateObsoleteHighlighters() {
    boolean changed = false;
    // do not remove obsolete highlighters if we are in "essential highlighting only" mode, because otherwise all inspection-produced results would be gone

    ObjectIterator<Long2ObjectMap.Entry<List<RangeHighlighterEx>>> iterator = incinerator.long2ObjectEntrySet().iterator();
    while (iterator.hasNext()) {
      Long2ObjectMap.Entry<List<RangeHighlighterEx>> entry = iterator.next();
      List<RangeHighlighterEx> list = entry.getValue();
      changed |= list.removeIf(highlighter -> {
        boolean shouldRemove = tryIncinerate(highlighter);
        return shouldRemove;
      });
      if (list.isEmpty()) {
        iterator.remove();
      }
    }
    return changed;
  }

  boolean tryIncinerate(@NotNull RangeHighlighterEx highlighter) {
    boolean shouldRemove = UpdateHighlightersUtil.shouldRemoveHighlighter(highlighter, myHighlightingSession);
    if (HighlightInfoUpdaterImpl.LOG.isDebugEnabled()) {
      HighlightInfoUpdaterImpl.LOG.debug("tryIncinerate " + highlighter + "; shouldRemove:" + shouldRemove);
    }
    if (shouldRemove) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      UpdateHighlightersUtil.disposeWithFileLevelIgnoreErrors(highlighter, info, myHighlightingSession);
      ((UserDataHolderEx)highlighter).replace(BEING_RECYCLED_KEY, myHighlightingSession.getProgressIndicator(), null);
    }
    return shouldRemove;
  }

  /**
   * - create {@link HighlighterRecycler},
   * - run {@code consumer} which usually calls {@link HighlighterRecycler#recycleHighlighter(RangeHighlighterEx)} and {@link HighlighterRecyclerPickup#pickupHighlighterFromGarbageBin(int, int, int)}
   * - and then incinerate all remaining highlighters, or in the case of PCE, release them back to recyclable state
   */
  static boolean runWithRecycler(@NotNull HighlightingSession session, @NotNull Processor<? super HighlighterRecycler> consumer) {
    HighlighterRecycler recycler = new HighlighterRecycler(session);
    try {
      boolean result = consumer.process(recycler);
      recycler.incinerateObsoleteHighlighters();
      return result;
    }
    finally {
      recycler.releaseHighlighters();
    }
  }
  synchronized boolean isEmpty() {
    return incinerator.isEmpty();
  }
  synchronized boolean remove(@NotNull RangeHighlighterEx highlighter) {
    List<RangeHighlighterEx> list = incinerator.get(TextRangeScalarUtil.toScalarRange(highlighter.getTextRange()));
    if (list != null) {
      return list.remove(highlighter);
    }
    return false;
  }
}
interface HighlighterRecyclerPickup {
  @Nullable RangeHighlighterEx pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer);
  @Nullable RangeHighlighterEx pickupFileLevelRangeHighlighter(int fileTextLength);
  HighlighterRecyclerPickup EMPTY = new HighlighterRecyclerPickup() {
    @Override
    public @Nullable RangeHighlighterEx pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer) {
      return null;
    }

    @Override
    public @Nullable RangeHighlighterEx pickupFileLevelRangeHighlighter(int fileTextLength) {
      return null;
    }
  };
}
