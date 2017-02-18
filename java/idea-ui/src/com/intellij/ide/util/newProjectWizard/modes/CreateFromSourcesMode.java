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

/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.importProject.FrameworkDetectionStep;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.importProject.RootsDetectionStep;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class CreateFromSourcesMode extends WizardMode {
  protected ProjectFromSourcesBuilderImpl myProjectBuilder;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.description",
                                 ApplicationNamesInfo.getInstance().getFullProductName(), context.getPresentationName());
  }

  @Nullable
  protected StepSequence createSteps(@NotNull final WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    final StepSequence sequence = new StepSequence();
    addSteps(context, modulesProvider, sequence, null);
    return sequence;
  }

  public void addSteps(WizardContext context, ModulesProvider modulesProvider, StepSequence sequence, String specific) {
    final ProjectFromSourcesBuilderImpl projectBuilder = new ProjectFromSourcesBuilderImpl(context, modulesProvider);
    myProjectBuilder = projectBuilder;

    final Icon icon = context.getStepIcon();
    if (context.isCreatingNewProject()) {
      addStep(sequence, new ProjectNameStep(context, this), specific);
    }
    addStep(sequence, new RootsDetectionStep(projectBuilder, context, sequence, icon, "reference.dialogs.new.project.fromCode.source"), specific);

    Set<String> detectorTypes = new HashSet<>();
    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      detectorTypes.add(detector.getDetectorId());
      for (ModuleWizardStep step : detector.createWizardSteps(projectBuilder, projectBuilder.getProjectDescriptor(detector), icon)) {
        sequence.addSpecificStep(detector.getDetectorId(), step);
      }
    }

    if (FrameworkDetectionStep.isEnabled()) {
      FrameworkDetectionStep frameworkDetectionStep = new FrameworkDetectionStep(icon, projectBuilder) {
        public List<ModuleDescriptor> getModuleDescriptors() {
          final List<ModuleDescriptor> moduleDescriptors = new ArrayList<>();
          for (ProjectDescriptor descriptor : projectBuilder.getSelectedDescriptors()) {
            moduleDescriptors.addAll(descriptor.getModules());
          }
          return moduleDescriptors;
        }
      };
      projectBuilder.addConfigurationUpdater(frameworkDetectionStep);
      sequence.addCommonFinishingStep(frameworkDetectionStep, detectorTypes);
    }
  }

  private static void addStep(StepSequence sequence, ModuleWizardStep step, String specific) {
    if (specific == null) {
      sequence.addCommonStep(step);
    }
    else {
      sequence.addSpecificStep(specific, step);
    }
  }

  public ProjectBuilder getModuleBuilder() {
    return myProjectBuilder;
  }

  public void onChosen(final boolean enabled) {
  }

  @Override
  public String getShortName() {
    return "Create from Sources";
  }

  public void dispose() {
    myProjectBuilder = null;
    super.dispose();
  }
}