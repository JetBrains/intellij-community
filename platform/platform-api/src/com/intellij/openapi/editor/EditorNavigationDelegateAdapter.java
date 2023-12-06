// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

public class EditorNavigationDelegateAdapter implements EditorNavigationDelegate {

  @Override
  public @NotNull Result navigateToLineEnd(@NotNull Editor editor, @NotNull DataContext dataContext) {
    return Result.CONTINUE;
  }
}
