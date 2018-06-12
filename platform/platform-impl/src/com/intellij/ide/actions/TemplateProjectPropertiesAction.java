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
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeUICustomization;

public class TemplateProjectPropertiesAction extends AnAction implements DumbAware {
  public TemplateProjectPropertiesAction() {
    String projectConceptName = StringUtil.capitalize(IdeUICustomization.getInstance().getProjectConceptName());
    getTemplatePresentation().setText(ActionsBundle.message("action.TemplateProjectProperties.text.template", CommonBundle.settingsTitle(), projectConceptName));
    getTemplatePresentation().setDescription(ActionsBundle.message("action.TemplateProjectProperties.description.template", projectConceptName));
  }

  public void actionPerformed(AnActionEvent e) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject, ShowSettingsUtilImpl.getConfigurableGroups(defaultProject, false));
  }
}
