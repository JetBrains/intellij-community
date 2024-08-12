// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class CodeFoldingPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, DumbAware {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    int[] ghp = {Pass.UPDATE_ALL};
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    registrar.registerTextEditorHighlightingPass(this, serializeCodeInsightPasses ? ghp : null, null, false, Pass.UPDATE_FOLDING);
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    return new CodeFoldingPass(editor, file);
  }
}
