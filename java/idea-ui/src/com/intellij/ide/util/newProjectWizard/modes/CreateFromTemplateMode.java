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
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class CreateFromTemplateMode extends WizardMode {

  private static final Condition<ProjectTemplate> TEMPLATE_CONDITION = new Condition<ProjectTemplate>() {
    @Override
    public boolean value(ProjectTemplate template) {
      return !(template instanceof DirectoryProjectGenerator);
    }
  };
  private SelectTemplateStep mySelectTemplateStep;

  public static MultiMap<String, ProjectTemplate> getTemplatesMap(WizardContext context) {
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<String, ProjectTemplate> groups = new MultiMap<String, ProjectTemplate>();
    for (ProjectTemplatesFactory factory : factories) {
      for (String group : factory.getGroups()) {
        ProjectTemplate[] templates = factory.createTemplates(group, context);
        List<ProjectTemplate> values = context.isCreatingNewProject() ? Arrays.asList(templates) : ContainerUtil.filter(templates,
                                                                                                                        TEMPLATE_CONDITION);
        if (!values.isEmpty()) {
          groups.putValues(group, values);
        }
      }
    }
    final MultiMap<String, ProjectTemplate> sorted = new MultiMap<String, ProjectTemplate>();
    // put single leafs under "Other"
    for (Map.Entry<String, Collection<ProjectTemplate>> entry : groups.entrySet()) {
      Collection<ProjectTemplate> templates = entry.getValue();
      if (templates.size() == 1 &&
          !ProjectTemplatesFactory.CUSTOM_GROUP.equals(entry.getKey())) {

        if (!(templates.iterator().next() instanceof ArchivedProjectTemplate)) {
          sorted.putValues(ProjectTemplatesFactory.OTHER_GROUP, templates);
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
  protected StepSequence createSteps(WizardContext context, @NotNull ModulesProvider modulesProvider) {
    MultiMap<String, ProjectTemplate> map = getTemplatesMap(context);
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
  public ModuleBuilder getModuleBuilder() {
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
