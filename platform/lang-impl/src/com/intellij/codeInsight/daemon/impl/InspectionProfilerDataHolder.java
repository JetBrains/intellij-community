// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface InspectionProfilerDataHolder {
  void saveStats(@NotNull PsiFile file, @NotNull List<? extends InspectionRunner.InspectionContext> contexts, long totalHighlightingNanos);

  // rearrange contexts in 'init' according to their inspection tools statistics gathered earlier:
  // - first, contexts with inspection tools which produced errors in previous run, ordered by latency to the 1st created error
  // - second, contexts with inspection tools which produced warnings in previous run, ordered by latency to the 1st created warning
  // - last, contexts with inspection tools which produced all other problems in previous run, ordered by latency to the 1st created problem
  void sort(@NotNull PsiFile file, @NotNull List<? extends InspectionRunner.InspectionContext> init);

  void retrieveFavoriteElements(@NotNull PsiFile file, @NotNull List<? extends InspectionRunner.InspectionContext> init);

  static InspectionProfilerDataHolder getInstance(@NotNull Project project) {
    return project.getService(InspectionProfilerDataHolder.class);
  }
}
