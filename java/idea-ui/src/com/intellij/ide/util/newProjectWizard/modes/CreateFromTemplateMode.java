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
import com.intellij.platform.templates.ArchivedTemplatesFactory;
import com.intellij.platform.templates.LocalArchivedTemplate;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class CreateFromTemplateMode extends WizardMode {

  private SelectTemplateStep mySelectTemplateStep;

  public static MultiMap<TemplatesGroup, ProjectTemplate> getTemplatesMap(WizardContext context, boolean includeArchived) {
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<TemplatesGroup, ProjectTemplate> groups = new MultiMap<TemplatesGroup, ProjectTemplate>();
    for (ProjectTemplatesFactory factory : factories) {
      if (!includeArchived && (factory instanceof ArchivedTemplatesFactory)) continue;
      for (String group : factory.getGroups()) {
        ProjectTemplate[] templates = factory.createTemplates(group, context);
        List<ProjectTemplate> values = Arrays.asList(templates);
        if (!values.isEmpty()) {
          Icon icon = factory.getGroupIcon(group);
          TemplatesGroup templatesGroup = new TemplatesGroup(group, null, icon, factory.getGroupWeight(group));
          if (icon != null) {
            Collection<ProjectTemplate> collection = groups.remove(templatesGroup);
            groups.putValues(templatesGroup, values);
            if (collection != null) {
              groups.putValues(templatesGroup, collection);
            }
          }
          else {
            groups.putValues(templatesGroup, values);
          }
        }
      }
    }
    final MultiMap<TemplatesGroup, ProjectTemplate> sorted = new MultiMap<TemplatesGroup, ProjectTemplate>();
    // put single leafs under "Other"
    for (Map.Entry<TemplatesGroup, Collection<ProjectTemplate>> entry : groups.entrySet()) {
      Collection<ProjectTemplate> templates = entry.getValue();
      String name = entry.getKey().getName();
      if (templates.size() == 1 && !ProjectTemplatesFactory.CUSTOM_GROUP.equals(name) && !"Java".equals(name)) {

        if (!(templates.iterator().next() instanceof LocalArchivedTemplate)) {
          sorted.putValues(new TemplatesGroup(ProjectTemplatesFactory.OTHER_GROUP, null, null, -1), templates);
          continue;
        }
      }
      sorted.putValues(entry.getKey(), templates);
    }
    return sorted;
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
    MultiMap<TemplatesGroup, ProjectTemplate> map = getTemplatesMap(context, true);
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
