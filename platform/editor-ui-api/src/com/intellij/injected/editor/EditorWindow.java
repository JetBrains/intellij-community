// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiFile;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface EditorWindow extends UserDataHolderEx, Editor {
  boolean isValid();

  @NotNull
  PsiFile getInjectedFile();

  @NotNull
  LogicalPosition hostToInjected(@NotNull LogicalPosition hPos);

  @NotNull
  LogicalPosition injectedToHost(@NotNull LogicalPosition pos);

  @NotNull
  Editor getDelegate();

  @NotNull
  @Override
  DocumentWindow getDocument();

  /**
   * The scope of this editor, as {@link Editor#getCoroutineScope()} defines it.
   * <p>
   * The scope can be cancelled before the scope of the {@link #getDelegate() host editor}.
   * Work that must live as long as the host editor uses the scope of the host editor.
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  @Override
  default @NotNull CoroutineScope getCoroutineScope() {
    return getDelegate().getCoroutineScope();
  }
}
