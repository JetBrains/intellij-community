// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class ContributedReferenceHighlightingPassFactory implements TextEditorHighlightingPassFactory,
                                                                   TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    boolean serialized = ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    int[] runAfterCompletionOf = serialized ? new int[]{Pass.UPDATE_ALL} : null;
    int[] runAfterStartingOf = serialized ? null : new int[]{Pass.UPDATE_ALL};
    registrar.registerTextEditorHighlightingPass(new ContributedReferenceHighlightingPassFactory(),
                                                 runAfterCompletionOf, runAfterStartingOf, false, -1);
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), file, Pass.UPDATE_ALL);
    if (textRange == null) {
      return new ProgressableTextEditorHighlightingPass.EmptyPass(file.getProject(), editor.getDocument());
    }

    if (Registry.is("annotate.hyperlinks.in.general.pass")) {
      return new ProgressableTextEditorHighlightingPass.EmptyPass(file.getProject(), editor.getDocument());
    }

    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();
    return new ContributedReferenceHighlightingPass(file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true,
                                                    visibleRange, editor, new DefaultHighlightInfoProcessor());
  }
}
