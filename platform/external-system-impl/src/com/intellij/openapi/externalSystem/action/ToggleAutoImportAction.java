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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 10/20/2014
 */
public class ToggleAutoImportAction extends ExternalSystemToggleAction {

  public ToggleAutoImportAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.refresh.project.auto.text"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.refresh.project.auto.description"));
  }

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    if (getSystemId(e) == null) return false;

    return ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext()) != null;
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    if (!super.isVisible(e)) return false;
    if (getSystemId(e) == null) return false;

    return ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext()) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final ExternalProjectSettings projectSettings = getProjectSettings(e);

    return projectSettings != null && projectSettings.isUseAutoImport();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final ExternalProjectSettings projectSettings = getProjectSettings(e);
    if (projectSettings != null) {
      if (state != projectSettings.isUseAutoImport()) {
        projectSettings.setUseAutoImport(state);
        ExternalSystemApiUtil.getSettings(getProject(e), getSystemId(e)).getPublisher()
          .onUseAutoImportChange(state, projectSettings.getExternalProjectPath());
      }
    }
  }

  @Nullable
  private ExternalProjectSettings getProjectSettings(AnActionEvent e) {
    final ProjectNode projectNode = ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext());
    if (projectNode == null || projectNode.getData() == null) return null;
    final AbstractExternalSystemSettings externalSystemSettings = ExternalSystemApiUtil.getSettings(getProject(e), getSystemId(e));
    return externalSystemSettings.getLinkedProjectSettings(projectNode.getData().getLinkedExternalProjectPath());
  }
}
