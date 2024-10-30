// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.ProgressableTextEditorHighlightingPass.EmptyPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class GeneralHighlightingPassFactory implements MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, DumbAware {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    int[] uf = {Pass.UPDATE_FOLDING};
    registrar.registerTextEditorHighlightingPass(new GeneralHighlightingPassFactory(),
                                                 null,
                                                 serializeCodeInsightPasses ? null : uf, false, Pass.UPDATE_ALL);
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, Pass.UPDATE_ALL);
    if (textRange == null) {
      return new EmptyPass(psiFile.getProject(), editor.getDocument());
    }
    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(psiFile).getVisibleRange();
    return new GeneralHighlightingPass(psiFile, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true, visibleRange, editor, true,
                                       true, true, HighlightInfoUpdater.getInstance(psiFile.getProject()));
  }

  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    // no applying to the editor - for read-only analysis only
    return new GeneralHighlightingPass(file, document, 0, file.getTextLength(),
                                       true, new ProperTextRange(0, document.getTextLength()), null, true, true,
                                       true, HighlightInfoUpdater.EMPTY);
  }
}
