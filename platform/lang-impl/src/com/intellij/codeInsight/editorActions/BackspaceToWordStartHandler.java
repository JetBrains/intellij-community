// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class BackspaceToWordStartHandler extends BackspaceHandler {
  public BackspaceToWordStartHandler(EditorActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (!handleBackspace(editor, caret, dataContext, true)) {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }
}
