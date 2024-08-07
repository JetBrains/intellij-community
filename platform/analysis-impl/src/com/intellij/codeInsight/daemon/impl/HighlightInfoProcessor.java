// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// IMPL class hardcoding logic to react to errors/warnings found during highlighting
// DO NOT USE directly
@ApiStatus.Obsolete
public abstract class HighlightInfoProcessor {
  // HInfos for visible part of file/block are produced.
  // Will remove all range-highlighters from there and replace them with passed infos
  public void highlightsInsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                     @Nullable Editor editor,
                                                     @NotNull List<? extends HighlightInfo> infos,
                                                     @NotNull TextRange priorityRange,
                                                     @NotNull TextRange restrictRange, int groupId) {}
  public void highlightsOutsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                      @Nullable Editor editor,
                                                      @NotNull List<? extends HighlightInfo> infos,
                                                      @NotNull TextRange priorityRange,
                                                      @NotNull TextRange restrictedRange, int groupId) {}

  // new HInfo became available during highlighting.
  // Incrementally add this HInfo in EDT iff there were nothing there before.
  public void infoIsAvailable(@NotNull HighlightingSession session,
                              @NotNull HighlightInfo info,
                              @NotNull TextRange priorityRange,
                              @NotNull TextRange restrictedRange,
                              int groupId) {}

  // this range is over.
  // Can queue to EDT to remove abandoned bijective highlighters from this range. All the rest abandoned highlighters have to wait until *AreProduced().
  public void allHighlightsForRangeAreProduced(@NotNull HighlightingSession session,
                                               long elementRange,
                                               @Nullable List<? extends HighlightInfo> infos){}

  public void progressIsAdvanced(@NotNull HighlightingSession highlightingSession, @Nullable Editor editor, double progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
  }


  private static final HighlightInfoProcessor EMPTY = new HighlightInfoProcessor() { };
  public static @NotNull HighlightInfoProcessor getEmpty() {
    return EMPTY;
  }
}
