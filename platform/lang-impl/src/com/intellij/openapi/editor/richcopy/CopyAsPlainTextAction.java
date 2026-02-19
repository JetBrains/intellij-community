// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actions.CopyAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CopyAsPlainTextAction extends EditorAction {
  public CopyAsPlainTextAction() {
    super(new CopyAction.Handler());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(e.getPresentation().isVisible() && !EditorUtil.contextMenuInvokedOutsideOfSelection(e));
  }

  @Override
  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    super.update(editor, presentation, dataContext);
    presentation.setVisible(editor.getSelectionModel().hasSelection(true) && CopyAsRichTextAction.isRichCopyPossible(editor));
  }
}
