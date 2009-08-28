/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 * Date: 21-Jun-2006
 */
public abstract class TextEditorHighlightingPassRegistrarEx extends TextEditorHighlightingPassRegistrar {

  public static TextEditorHighlightingPassRegistrarEx getInstanceEx(Project project) {
    return (TextEditorHighlightingPassRegistrarEx)getInstance(project);
  }

  @NotNull public abstract List<TextEditorHighlightingPass> instantiatePasses(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull int[] passesToIgnore);
}
