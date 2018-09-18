// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.CutProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CutAction extends AnAction implements DumbAware {
  public CutAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CutProvider provider = getAvailableCutProvider(e);
    if (provider == null) {
      return;
    }
    provider.performCut(e.getDataContext());
  }

  private static CutProvider getAvailableCutProvider(@NotNull AnActionEvent e) {
    CutProvider provider = PlatformDataKeys.CUT_PROVIDER.getData(e.getDataContext());
    Project project = e.getProject();
    if (project != null && DumbService.isDumb(project) && !DumbService.isDumbAware(provider)) {
      return null;
    }
    return provider;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CutProvider provider = getAvailableCutProvider(event);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(project != null && project.isOpen() && provider != null && provider.isCutEnabled(dataContext));
    if (event.getPlace().equals(ActionPlaces.EDITOR_POPUP) && provider != null) {
      presentation.setVisible(provider.isCutVisible(dataContext));
    }
    else {
      presentation.setVisible(true);
    }
  }
}
