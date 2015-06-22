/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;

public class EditRunConfigurationsAction extends DumbAwareAction {
  public EditRunConfigurationsAction() {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(AllIcons.Actions.EditSource,0,2,2);
    icon.setIcon(EmptyIcon.ICON_18, 1);
    getTemplatePresentation().setIcon(icon);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null && project.isDisposed()) {
      return;
    }
    if (project == null) {
      //setup template project configurations
      project = ProjectManager.getInstance().getDefaultProject();
    }
    final EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }

  @Override
  public void update(final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabled(project == null || !DumbService.isDumb(project));
    if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
      presentation.setText(ExecutionBundle.message("edit.configuration.action"));
      presentation.setDescription(presentation.getText());
    }
  }
}
