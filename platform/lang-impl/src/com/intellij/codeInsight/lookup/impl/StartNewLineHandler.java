// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StartNewLineHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public StartNewLineHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    Runnable callOriginal = () -> {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
    };
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup != null &&
        lookup.getLookupStart() == lookup.getLookupOriginalStart() &&
        lookup.getLookupStart() == lookup.getTopLevelEditor().getCaretModel().getOffset()) {
      lookup.performGuardedChange(callOriginal);
      lookup.moveToCaretPosition();
    } else {
      callOriginal.run();
    }
  }
}
