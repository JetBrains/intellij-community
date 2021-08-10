// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ShowSelectionHistoryAction extends ShowHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    VirtualFile f = Objects.requireNonNull(getFile(e));
    VcsSelection sel = Objects.requireNonNull(getSelection(e));

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    new SelectionHistoryDialog(p, gw, f, from, to).show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    VcsSelection selection = getSelection(e);
    if (selection == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else if (!e.getPlace().equals(ActionPlaces.ACTION_SEARCH)) {
      e.getPresentation().setText(selection.getActionName());
    }
  }

  @Override
  protected boolean isEnabled(@NotNull IdeaGateway gw, @NotNull VirtualFile f) {
    return super.isEnabled(gw, f) && !f.isDirectory();
  }

  @Nullable
  private static VcsSelection getSelection(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null && EditorUtil.contextMenuInvokedOutsideOfSelection(e)) {
      Caret caret = editor.getCaretModel().getPrimaryCaret();
      return new VcsSelection(editor.getDocument(), new TextRange(caret.getOffset(), caret.getOffset()), VcsBundle.message("action.name.show.history.for.selection"));
    }
    return VcsSelectionUtil.getSelection(e.getDataContext());
  }
}
