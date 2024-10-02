// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class UnselectPreviousOccurrenceAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  private UnselectPreviousOccurrenceAction() {
    super(new Handler());
  }

  private static final class Handler extends SelectOccurrencesActionHandler {
    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return editor.getCaretModel().supportsMultipleCarets();
    }

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (editor.getCaretModel().getCaretCount() > 1) {
        editor.getCaretModel().removeCaret(editor.getCaretModel().getPrimaryCaret());
      }
      else {
        editor.getSelectionModel().removeSelection();
      }
      getAndResetNotFoundStatus(editor);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }
}
