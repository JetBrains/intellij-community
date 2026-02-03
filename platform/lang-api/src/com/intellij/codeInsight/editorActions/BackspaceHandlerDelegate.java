// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * This extension allows to modify the behaviour of 'Backspace' action in editor (usually bound to 'Backspace' key).
 */
public abstract class BackspaceHandlerDelegate {
  public static final ExtensionPointName<BackspaceHandlerDelegate> EP_NAME =
    new ExtensionPointName<>("com.intellij.backspaceHandlerDelegate");

  /**
   * Invoked before the default processing is performed.
   */
  public abstract void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor);

  /**
   * Invoked after basic 'Backspace' processing has been performed.
   *
   * @return {@code true} if no further processing (in particular, deleting matching brace/quote and post-processing
   *         defined by other extensions) should be performed.
   * @see BackspaceHandler#handleBackspace(Editor, Caret, DataContext, boolean)
   */
  public abstract boolean charDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor);
}
