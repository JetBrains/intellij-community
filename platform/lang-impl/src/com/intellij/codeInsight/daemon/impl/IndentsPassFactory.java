// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IndentsPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.BEFORE, Pass.UPDATE_FOLDING, false, false);
  }

  @Override
  public @Nullable TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    if (!IndentsPassFilterUtils.shouldRunIndentsPass(editor)) return null;
    return new IndentsPass(psiFile.getProject(), editor, psiFile);
  }
}
