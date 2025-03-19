// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class SlowLineMarkersPassFactory implements TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this,
                                                 new int[]{Pass.UPDATE_ALL}, null,
                                                 false, Pass.SLOW_LINE_MARKERS);
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(psiFile.getProject())).isSerializeCodeInsightPasses();

    LineMarkersPass.Mode myMode = serializeCodeInsightPasses ? LineMarkersPass.Mode.SLOW : LineMarkersPass.Mode.NONE;
    return LineMarkersPassFactory.createLineMarkersPass(psiFile, editor, myMode, Pass.SLOW_LINE_MARKERS);
  }
}