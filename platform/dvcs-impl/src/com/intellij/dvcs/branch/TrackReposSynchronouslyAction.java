// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

public abstract class TrackReposSynchronouslyAction extends ToggleAction implements DumbAware {

  protected TrackReposSynchronouslyAction() {
    super(DvcsBundle.message("sync.setting"), DvcsBundle.message("sync.setting.description", VcsBundle.message("vcs.generic.name")), null);
  }

  protected abstract @NotNull DvcsSyncSettings getSettings(@NotNull AnActionEvent e);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return getSettings(e).getSyncSetting() == DvcsSyncSettings.Value.SYNC;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    getSettings(e).setSyncSetting(state ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
  }
}
