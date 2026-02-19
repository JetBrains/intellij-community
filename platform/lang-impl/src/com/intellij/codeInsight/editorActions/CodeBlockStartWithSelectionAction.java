// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CodeBlockStartWithSelectionAction extends EditorAction {
  public CodeBlockStartWithSelectionAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        CodeBlockUtil.moveCaretToCodeBlockStart(project, editor, true);
      }
    }
  }
}
