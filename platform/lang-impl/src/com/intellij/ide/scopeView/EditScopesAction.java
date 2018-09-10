// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.scopeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class EditScopesAction extends AnAction implements DumbAware {
  public EditScopesAction() {
    super(AllIcons.Ide.LocalScope);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    ProjectView view = project == null ? null : ProjectView.getInstance(project);
    if (view != null) {
      ScopeChooserConfigurable configurable = new ScopeChooserConfigurable(project);
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
        AbstractProjectViewPane pane = view.getCurrentProjectViewPane();
        if (pane instanceof ScopeViewPane) {
          NamedScopeFilter filter = ((ScopeViewPane)pane).getFilter(pane.getSubId());
          if (filter != null) configurable.selectNodeInTree(filter.getScope().getName());
        }
      });
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    ProjectView view = project == null ? null : ProjectView.getInstance(project);
    event.getPresentation().setEnabledAndVisible(view != null && view.getProjectViewPaneById(ScopeViewPane.ID) != null);
  }
}
