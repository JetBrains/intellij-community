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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import org.jetbrains.annotations.NotNull;

public class ShowStructureSettingsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    showDialog(project);
  }

  static void showDialog(Project project) {
    new SingleConfigurableEditor(project, ProjectStructureConfigurable.getInstance(project), SettingsDialog.DIMENSION_KEY) {
      @NotNull
      @Override
      protected DialogStyle getStyle() {
        return DialogStyle.COMPACT;
      }
    }.show();
  }
}