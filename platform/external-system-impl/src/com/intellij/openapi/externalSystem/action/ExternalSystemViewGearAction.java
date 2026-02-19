// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public abstract class ExternalSystemViewGearAction extends ExternalSystemToggleAction {

  private ExternalProjectsViewImpl myView;


  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    return getView() != null;
  }

  @Override
  protected boolean doIsSelected(@NotNull AnActionEvent e) {
    final ExternalProjectsViewImpl view = getView();
    return view != null && isSelected(view);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final ExternalProjectsViewImpl view = getView();
    if (view != null){
      // es system id does not available in the action context, get it from the view
      ProjectSystemId systemId = view.getSystemId();
      ExternalSystemActionsCollector.trigger(getProject(e), systemId, this, e);
      setSelected(view, state);
    }
  }

  protected abstract boolean isSelected(@NotNull ExternalProjectsViewImpl view);

  protected abstract void setSelected(@NotNull ExternalProjectsViewImpl view, boolean value);

  protected @Nullable ExternalProjectsViewImpl getView() {
    return myView;
  }

  public void setView(ExternalProjectsViewImpl view) {
    myView = view;
  }
}
