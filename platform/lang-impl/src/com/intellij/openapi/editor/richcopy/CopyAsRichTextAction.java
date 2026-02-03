// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CopyAsRichTextAction extends DumbAwareAction {
  public CopyAsRichTextAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    p.setVisible(!RichCopySettings.getInstance().isEnabled() &&
                 (e.isFromActionToolbar() || (editor != null && editor.getSelectionModel().hasSelection(true))) &&
                 (editor == null || isRichCopyPossible(editor)));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RichCopySettings settings = RichCopySettings.getInstance();
    boolean savedValue = settings.isEnabled();
    try {
      settings.setEnabled(true);
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY).actionPerformed(e);
    }
    finally {
      settings.setEnabled(savedValue);
    }
  }

  static boolean isRichCopyPossible(@NotNull Editor editor) {
    // ideally, we'd also want to check for the presence of PsiFile (CopyHandler won't work without it), but it might be more expensive
    return FileDocumentManager.getInstance().getFile(editor.getDocument()) != null;
  }
}
