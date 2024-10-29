// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SelectAllAction extends TextComponentEditorAction implements DumbAware {
  public SelectAllAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(CommonDataKeys.PROJECT.getData(dataContext),
                               () -> editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength()), IdeBundle.message("command.select.all"), null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Editor editor = TextComponentEditorAction.getEditorFromContext(event.getDataContext());
    presentation.setEnabled(editor != null);
  }
}
