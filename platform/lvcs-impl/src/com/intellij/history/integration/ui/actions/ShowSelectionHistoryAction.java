// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.notNull;

public class ShowSelectionHistoryAction extends ShowHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull VirtualFile f, @NotNull AnActionEvent e) {
    VcsSelection sel = Objects.requireNonNull(getSelection(e));

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    new SelectionHistoryDialog(p, gw, f, from, to).show();
  }

  @Override
  protected String getText(@NotNull AnActionEvent e) {
    VcsSelection sel = getSelection(e);
    return sel == null ? super.getText(e) : sel.getActionName();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (getSelection(e) == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      super.update(e);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull LocalHistoryFacade vcs, @NotNull IdeaGateway gw, @Nullable VirtualFile f, @NotNull AnActionEvent e) {
    return super.isEnabled(vcs, gw, f, e) && !Objects.requireNonNull(f).isDirectory() && getSelection(e) != null;
  }

  @Nullable
  private static VcsSelection getSelection(@NotNull AnActionEvent e) {
    return VcsSelectionUtil.getSelection(VcsContextWrapper.createCachedInstanceOn(e));
  }
}
