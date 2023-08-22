// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public abstract class TextEditorHighlightingPassRegistrarEx extends TextEditorHighlightingPassRegistrar {
  public static TextEditorHighlightingPassRegistrarEx getInstanceEx(Project project) {
    return (TextEditorHighlightingPassRegistrarEx)getInstance(project);
  }

  public abstract @NotNull List<@NotNull TextEditorHighlightingPass> instantiatePasses(@NotNull PsiFile psiFile,
                                                                                       @NotNull Editor editor,
                                                                                       int @NotNull [] passesToIgnore);
  public abstract @NotNull List<@NotNull TextEditorHighlightingPass> instantiateMainPasses(@NotNull PsiFile psiFile,
                                                                                           @NotNull Document document,
                                                                                           @NotNull HighlightInfoProcessor highlightInfoProcessor);
  public abstract @NotNull Iterable<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories();
}
