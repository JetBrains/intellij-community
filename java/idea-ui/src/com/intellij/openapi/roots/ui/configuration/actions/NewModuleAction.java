/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 5, 2004
 */
public class NewModuleAction extends AnAction implements DumbAware {
  public NewModuleAction() {
    super(ProjectBundle.message("module.new.action"), ProjectBundle.message("module.new.action.description"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    Object dataFromContext = prepareDataFromContext(e);
    final AddModuleWizard wizard = new AddModuleWizard(project, new DefaultModulesProvider(project), null);

    wizard.show();

    if (wizard.isOK()) {
      final ProjectBuilder builder = wizard.getProjectBuilder();
      if (builder instanceof ModuleBuilder) {
        final ModuleBuilder moduleBuilder = (ModuleBuilder)builder;
        if (moduleBuilder.getName() == null) {
          moduleBuilder.setName(wizard.getProjectName());
        }
        if (moduleBuilder.getModuleFilePath() == null) {
          moduleBuilder.setModuleFilePath(wizard.getModuleFilePath());
        }
      }
      if (!builder.validate(project, project)) {
        return;
      }
      if (builder instanceof ModuleBuilder) {
        Module module = ((ModuleBuilder) builder).commitModule(project, null);
        if (module != null) {
          processCreatedModule(module, dataFromContext);
        }
      }
      else {
        builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
        if (builder.isOpenProjectSettingsAfter()) {
          ModulesConfigurator.showDialog(project, null, null, true);
        }
      }
    }
  }

  @Nullable
  protected Object prepareDataFromContext(final AnActionEvent e) {
    return null;
  }

  protected void processCreatedModule(final Module module, final Object dataFromContext) {
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }
}
