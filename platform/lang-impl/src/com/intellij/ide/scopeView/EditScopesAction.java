// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.scopeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EditScopesAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    ProjectView view = project == null ? null : ProjectView.getInstance(project);
    if (view != null) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, 
                                                        c -> ConfigurableWrapper.cast(ScopeChooserConfigurable.class, c) != null, 
                                                        c -> {
        AbstractProjectViewPane pane = view.getCurrentProjectViewPane();
        if (pane instanceof ScopeViewPane) {
          NamedScopeFilter filter = ((ScopeViewPane)pane).getFilter(pane.getSubId());
          ScopeChooserConfigurable configurable = ConfigurableWrapper.cast(ScopeChooserConfigurable.class, c);
          if (configurable != null && filter != null) configurable.selectNodeInTree(filter.getScope().getScopeId());
        }
      });
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    ProjectView view = project == null ? null : ProjectView.getInstance(project);
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace())) {
      event.getPresentation().setEnabledAndVisible(view != null && view.getCurrentViewId().equals(ScopeViewPane.ID));
    }
    else {
      event.getPresentation().setEnabledAndVisible(view != null && view.getProjectViewPaneById(ScopeViewPane.ID) != null);
    }
    if (ActionPlaces.TOOLWINDOW_POPUP.equals(event.getPlace())) {
      event.getPresentation().setIcon(null);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
