// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public abstract class ExternalSystemAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();
    final boolean visible = isVisible(e);
    p.setVisible(visible);
    p.setEnabled(visible && isEnabled(e));
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return hasProject(e) && getSystemId(e) != null;
  }

  protected boolean isVisible(@NotNull AnActionEvent e) {
    return true;
  }

  protected Project getProject(@NotNull AnActionEvent e) {
    return e.getProject();
  }

  protected ProjectSystemId getSystemId(@NotNull AnActionEvent e) {
    return e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID);
  }

  protected boolean hasProject(@NotNull AnActionEvent e) {
    return getProject(e) != null;
  }

  protected void setText(@NlsActions.ActionText String message) {
    getTemplatePresentation().setText(message);
  }

  protected void setDescription(@NlsActions.ActionDescription String message) {
    getTemplatePresentation().setDescription(message);
  }

  protected void setText(@NotNull AnActionEvent e, @NlsActions.ActionText String message) {
    e.getPresentation().setText(message);
  }
}