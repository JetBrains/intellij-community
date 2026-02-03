// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiFile;
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
}
