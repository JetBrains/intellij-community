// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

final class EscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  EscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void doExecute(@NotNull Editor editor, Caret caret, DataContext dataContext) {
    if (HintManagerImpl.getInstanceImpl().hideHints(HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_ANY_KEY, true, false)) {
      return;
    }
    myOriginalHandler.execute(editor, caret, dataContext);
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    return hintManager.isEscapeHandlerEnabled() || myOriginalHandler.isEnabled(editor, caret, dataContext);
  }
}
