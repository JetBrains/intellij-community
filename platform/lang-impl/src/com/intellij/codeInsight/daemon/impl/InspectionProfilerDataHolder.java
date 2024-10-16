// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Methods for storing CPU profiler data for inspection run session and retrieve it later during the next inspection pass
 * to optimize "run inspection tool-to-warning onscreen" latency
 */
class InspectionProfilerDataHolder {
  /**
   * after inspections completed, save their latencies (from corresponding {@link InspectionRunner.InspectionContext#holder})
   * to use later in {@link #sortByLatencies(PsiFile, List, HighlightInfoUpdaterImpl)}
   */
  static void saveStats(@NotNull PsiFile psiFile, @NotNull List<? extends InspectionRunner.InspectionContext> contexts,
                        @NotNull HighlightInfoUpdaterImpl highlightInfoUpdater) {
    if (!psiFile.getViewProvider().isPhysical()) {
      // ignore editor text fields/consoles etc
      return;
    }
    Map<Object, HighlightInfoUpdaterImpl.ToolLatencies> latencies = ContainerUtil.map2Map(contexts, context -> Pair.create(context.tool().getShortName(), new HighlightInfoUpdaterImpl.ToolLatencies(
                                                                 Math.max(0, context.holder().toolStamps.errorStamp - context.holder().toolStamps.initTimeStamp),
                                                                 Math.max(0, context.holder().toolStamps.warningStamp - context.holder().toolStamps.initTimeStamp),
                                                                 Math.max(0, context.holder().toolStamps.otherStamp - context.holder().toolStamps.initTimeStamp))));
    highlightInfoUpdater.saveLatencies(psiFile, latencies);
  }

  /**
   * rearrange contexts in 'init' according to their inspection tools statistics gathered earlier:
   * - first, contexts with inspection tools which produced errors in previous run, ordered by latency to the 1st created error
   * - second, contexts with inspection tools which produced warnings in previous run, ordered by latency to the 1st created warning
   * - last, contexts with inspection tools which produced all other problems in previous run, ordered by latency to the 1st created problem
   */
  static void sortByLatencies(@NotNull PsiFile psiFile, @NotNull List<InspectionRunner.InspectionContext> init,
                              @NotNull HighlightInfoUpdaterImpl highlightInfoUpdater) {
    init.sort((context1, context2) -> {
      String toolId1 = context1.tool().getShortName();
      String toolId2 = context2.tool().getShortName();
      return highlightInfoUpdater.compareLatencies(psiFile, toolId1, toolId2);
    });
  }
}
