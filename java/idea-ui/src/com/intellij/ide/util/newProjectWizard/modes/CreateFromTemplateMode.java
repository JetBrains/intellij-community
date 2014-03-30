/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.newProjectWizard.TemplatesGroup;
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class CreateFromTemplateMode extends WizardMode {

  private SelectTemplateStep mySelectTemplateStep;

  public static MultiMap<TemplatesGroup, ProjectTemplate> getTemplatesMap(WizardContext context) {
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<TemplatesGroup, ProjectTemplate> groups = new MultiMap<TemplatesGroup, ProjectTemplate>();
    for (ProjectTemplatesFactory factory : factories) {
      for (String group : factory.getGroups()) {
        ProjectTemplate[] templates = factory.createTemplates(group, context);
        List<ProjectTemplate> values = Arrays.asList(templates);
        if (!values.isEmpty()) {
          Icon icon = factory.getGroupIcon(group);
          String parentGroup = factory.getParentGroup(group);
          TemplatesGroup templatesGroup = new TemplatesGroup(group, null, icon, factory.getGroupWeight(group), parentGroup, group, null);
          groups.putValues(templatesGroup, values);
        }
      }
    }
    return groups;
  }

  @NotNull
  @Override
  public String getDisplayName(WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.template.title", context.getPresentationName());
  }

  @NotNull
  @Override
  public String getDescription(WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.template.description", context.getPresentationName());
  }

  @Override
  public boolean isAvailable(WizardContext context) {
    return true;
  }

  @Nullable
  @Override
  protected StepSequence createSteps(@NotNull WizardContext context, @NotNull ModulesProvider modulesProvider) {
    MultiMap<TemplatesGroup, ProjectTemplate> map = getTemplatesMap(context);
    StepSequence sequence = new StepSequence();
    for (ProjectTemplate template : map.values()) {
      sequence.addStepsForBuilder(template.createModuleBuilder(), context, modulesProvider);
    }
    mySelectTemplateStep = new SelectTemplateStep(context, sequence, map);
    sequence.addCommonStep(mySelectTemplateStep);
    return sequence;
  }

  @Nullable
  @Override
  public AbstractModuleBuilder getModuleBuilder() {
    final ProjectTemplate template = mySelectTemplateStep.getSelectedTemplate();
    if (template == null) {
      return null;
    }
    return template.createModuleBuilder();
  }

  @Override
  public String getShortName() {
    return "Create from Template";
  }

  @Override
  public void onChosen(boolean enabled) {
  }
}
