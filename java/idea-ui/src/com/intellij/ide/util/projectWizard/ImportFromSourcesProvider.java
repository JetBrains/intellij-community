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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.importProject.FrameworkDetectionStep;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.importProject.RootsDetectionStep;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class ImportFromSourcesProvider extends ProjectImportProvider {

  public ImportFromSourcesProvider() {
    super(new ProjectFromSourcesBuilderImpl(null, null)); // fake
  }

  @Nullable
  @Override
  public String getFileSample() {
    return JavaUiBundle.message("directory.with.existing.sources");
  }

  @Override
  public void addSteps(@NotNull StepSequence sequence, @NotNull WizardContext context, @NotNull String id) {
    var modulesProvider = DefaultModulesProvider.createForProject(context.getProject());
    var projectBuilder = new ProjectFromSourcesBuilderImpl(context, modulesProvider);
    addSteps(sequence, getName(), projectBuilder);
    myBuilder = projectBuilder;
  }

  private static void addSteps(
    @NotNull StepSequence sequence,
    @NotNull String specific,
    @NotNull ProjectFromSourcesBuilderImpl projectBuilder
  ) {
    var context = projectBuilder.getContext();
    var icon = context.getStepIcon();
    if (context.isCreatingNewProject()) {
      sequence.addSpecificStep(specific, new ProjectNameStep(context));
    }
    sequence.addSpecificStep(specific, new RootsDetectionStep(projectBuilder, context, sequence, icon,
                                                              "reference.dialogs.new.project.fromCode.source"));

    var detectorTypes = new HashSet<>();
    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      detectorTypes.add(detector.getDetectorId());
      for (ModuleWizardStep step : detector.createWizardSteps(projectBuilder, projectBuilder.getProjectDescriptor(detector), icon)) {
        sequence.addSpecificStep(detector.getDetectorId(), step);
      }
    }

    if (FrameworkDetectionStep.isEnabled()) {
      FrameworkDetectionStep frameworkDetectionStep = new FrameworkDetectionStep(icon, projectBuilder) {
        @Override
        public List<ModuleDescriptor> getModuleDescriptors() {
          final List<ModuleDescriptor> moduleDescriptors = new ArrayList<>();
          for (ProjectDescriptor descriptor : projectBuilder.getSelectedDescriptors()) {
            moduleDescriptors.addAll(descriptor.getModules());
          }
          return moduleDescriptors;
        }
      };
      projectBuilder.addConfigurationUpdater(frameworkDetectionStep);
      sequence.addCommonFinishingStep(frameworkDetectionStep, types -> ContainerUtil.intersects(types, detectorTypes));
    }
  }
}
