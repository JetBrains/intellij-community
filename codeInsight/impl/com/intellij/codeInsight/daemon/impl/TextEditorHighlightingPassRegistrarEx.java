/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 * Date: 21-Jun-2006
 */
public abstract class TextEditorHighlightingPassRegistrarEx extends TextEditorHighlightingPassRegistrar {
  public static TextEditorHighlightingPassRegistrarEx getInstanceEx(Project project) {
    return (TextEditorHighlightingPassRegistrarEx)getInstance(project);
  }

  public abstract void modifyHighlightingPasses(final List<TextEditorHighlightingPass> passes,
                                                                        final PsiFile psiFile,
                                                                        final Editor editor);

  public abstract boolean needAdditionalIntentionsPass();

  @Nullable
  public abstract int[] getPostHighlightingPasses();
}
