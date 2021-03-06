// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public abstract class BackspaceHandlerDelegate {
  public static final ExtensionPointName<BackspaceHandlerDelegate> EP_NAME =
    ExtensionPointName.create("com.intellij.backspaceHandlerDelegate");

  public abstract void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor);

  /**
   * @return true iff this delegate handled the character removal, and no further backspace handler should be invoked.
   * @see com.intellij.codeInsight.editorActions.BackspaceHandler#handleBackspace(com.intellij.openapi.editor.Editor, com.intellij.openapi.editor.Caret, com.intellij.openapi.actionSystem.DataContext, boolean)
   */
  public abstract boolean charDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor);
}
