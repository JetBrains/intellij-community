// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

final class LocalInspectionsPassFactory implements MainHighlightingPassFactory,
                                                   TextEditorHighlightingPassFactoryRegistrar,
                                                   PossiblyDumbAware {

  private static final Logger LOG = Logger.getInstance(LocalInspectionsPassFactory.class);

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    int[] ghp = {Pass.UPDATE_ALL};
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    registrar.registerTextEditorHighlightingPass(this, serializeCodeInsightPasses ? ghp : null,
                                                 serializeCodeInsightPasses ? null : ghp, true, Pass.LOCAL_INSPECTIONS);
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), file, Pass.LOCAL_INSPECTIONS);
    if (textRange == null){
      return new ProgressableTextEditorHighlightingPass.EmptyPass(file.getProject(), editor.getDocument());
    }
    TextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();
    return new LocalInspectionsPass(file, editor.getDocument(), textRange, visibleRange, true, HighlightInfoUpdater.getInstance(file.getProject()), true);
  }

  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    TextRange textRange = file.getTextRange();
    LOG.assertTrue(textRange != null, "textRange is null for " + file + " (" + PsiUtilCore.getVirtualFile(file) + ")");
    return new LocalInspectionsPass(file, document, textRange, TextRange.EMPTY_RANGE, true, HighlightInfoUpdater.EMPTY, true);
  }

  @Override
  public boolean isDumbAware() {
    return Registry.is("ide.dumb.aware.inspections");
  }
}
