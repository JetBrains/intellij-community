/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * User: anna
 * Date: 21-Jun-2006
 */
public abstract class TextEditorHighlitingPassRegistrarEx extends TextEditorHighlightingPassRegistrar {
  public abstract TextEditorHighlightingPass[] modifyHighlightingPasses(final List<TextEditorHighlightingPass> passes,
                                                                        final PsiFile psiFile,
                                                                        final Editor editor);

  public abstract boolean needAdditionalIntentionsPass();
}
