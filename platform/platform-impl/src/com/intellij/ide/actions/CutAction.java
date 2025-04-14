// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.CutProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.platform.ide.core.permissions.Permission;
import com.intellij.platform.ide.core.permissions.RequiresPermissions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.vfs.FilePermissionsKt.getProjectFilesWrite;

public class CutAction extends DumbAwareAction implements LightEditCompatible, RequiresPermissions {
  public CutAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CutProvider provider = getAvailableCutProvider(e);
    if (provider == null) {
      return;
    }
    provider.performCut(e.getDataContext());
  }

  @Override
  public @NotNull Collection<@NotNull Permission> getRequiredPermissions() {
    return Collections.singletonList(getProjectFilesWrite());
  }

  private static CutProvider getAvailableCutProvider(@NotNull AnActionEvent e) {
    CutProvider provider = e.getData(PlatformDataKeys.CUT_PROVIDER);
    Project project = e.getProject();
    if (project != null && provider != null && !DumbService.getInstance(project).isUsableInCurrentContext(provider)) {
      return null;
    }
    return provider;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    CopyAction.updateWithProvider(event, event.getData(PlatformDataKeys.CUT_PROVIDER), true, provider -> {
      boolean isEditorPopup = event.getPlace().equals(ActionPlaces.EDITOR_POPUP);
      event.getPresentation().setEnabled(provider.isCutEnabled(event.getDataContext()));
      event.getPresentation().setVisible(!isEditorPopup || provider.isCutVisible(event.getDataContext()));
    });
  }
}
