// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.ActivityScope;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import com.intellij.platform.lvcs.impl.ui.ActivityView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

@ApiStatus.Internal
public final class ShowSelectionHistoryAction extends ShowHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    VirtualFile f = ContainerUtil.getOnlyItem(getFiles(e));
    if (f == null) return;

    VcsSelection sel = Objects.requireNonNull(VcsSelectionUtil.getSelection(this, e));

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    if (ActivityView.isViewEnabled()) {
      ActivityView.showInDialog(p, gw, new ActivityScope.Selection(f, from, to));
    }
    else {
      LocalHistoryCounter.INSTANCE.logLocalHistoryOpened(LocalHistoryCounter.Kind.Selection);
      new SelectionHistoryDialog(p, gw, f, from, to).show();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    VcsSelection selection = VcsSelectionUtil.getSelection(this, e);
    if (selection == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else if (!e.getPlace().equals(ActionPlaces.ACTION_SEARCH)) {
      e.getPresentation().setText(selection.getActionName());
    }
  }

  @Override
  protected boolean isActionEnabled(@NotNull IdeaGateway gw, @NotNull Collection<VirtualFile> files) {
    VirtualFile singleFile = ContainerUtil.getOnlyItem(files);
    if (singleFile == null || singleFile.isDirectory()) return false;
    return super.isActionEnabled(gw, files);
  }
}
