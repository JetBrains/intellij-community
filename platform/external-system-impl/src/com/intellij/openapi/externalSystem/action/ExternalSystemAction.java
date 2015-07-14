/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 10/20/2014
 */
public abstract class ExternalSystemAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation p = e.getPresentation();
    final boolean visible = isVisible(e);
    p.setVisible(visible);
    p.setEnabled(visible && isEnabled(e));
  }

  protected boolean isEnabled(AnActionEvent e) {
    return hasProject(e) && getSystemId(e) != null;
  }

  protected boolean isVisible(AnActionEvent e) {
    return true;
  }

  protected Project getProject(AnActionEvent e) {
    return CommonDataKeys.PROJECT.getData(e.getDataContext());
  }

  protected ProjectSystemId getSystemId(AnActionEvent e) {
    return ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(e.getDataContext());
  }

  protected boolean hasProject(AnActionEvent e) {
    return getProject(e) != null;
  }

  protected void setText(String message) {
    getTemplatePresentation().setText(message);
  }

  protected void setDescription(String message) {
    getTemplatePresentation().setDescription(message);
  }

  protected void setText(AnActionEvent e, String message) {
    e.getPresentation().setText(message);
  }
}