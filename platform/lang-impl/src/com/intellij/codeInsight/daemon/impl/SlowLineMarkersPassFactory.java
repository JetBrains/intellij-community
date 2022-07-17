// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(file.getProject())).isSerializeCodeInsightPasses();

    LineMarkersPass.Mode myMode = serializeCodeInsightPasses ? LineMarkersPass.Mode.SLOW : LineMarkersPass.Mode.NONE;
    return LineMarkersPassFactory.createLineMarkersPass(file, editor, myMode, Pass.SLOW_LINE_MARKERS);
  }
}