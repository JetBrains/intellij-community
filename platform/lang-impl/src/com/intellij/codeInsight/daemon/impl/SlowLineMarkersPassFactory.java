// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class SlowLineMarkersPassFactory extends AbstractLineMarkersPassFactory {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();

    if (!serializeCodeInsightPasses) return;

    registrar.registerTextEditorHighlightingPass(new Factory(LineMarkersPass.Mode.SLOW),
                                                 new int[]{Pass.UPDATE_ALL}, null,
                                                 false, Pass.SLOW_LINE_MARKERS);
  }
}