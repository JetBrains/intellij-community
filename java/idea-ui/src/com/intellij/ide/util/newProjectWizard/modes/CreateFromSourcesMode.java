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

import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.ide.util.importProject.*;
import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CreateFromSourcesMode extends WizardMode {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private static final Icon ICON = IconLoader.getIcon("/addmodulewizard.png");
  private ProjectFromSourcesBuilder myProjectBuilder;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.description",
                                 ApplicationNamesInfo.getInstance().getProductName(), context.getPresentationName());
  }

  @Nullable
  protected StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    final ModuleInsight moduleInsight = new ModuleInsight(new DelegatingProgressIndicator());
    final ProjectFromSourcesBuilder projectBuilder = new ProjectFromSourcesBuilder(moduleInsight);
    myProjectBuilder = projectBuilder;
    
    final ProjectWizardStepFactory factory = ProjectWizardStepFactory.getInstance();
    final StepSequence sequence = new StepSequence();
    final Icon icon = context.isCreatingNewProject() ? NEW_PROJECT_ICON : ICON;
    sequence.addCommonStep(new ProjectNameStep(context, sequence, this));
    sequence.addCommonStep(factory.createSourcePathsStep(context, projectBuilder, icon, "reference.dialogs.new.project.fromCode.source"));
    sequence.addCommonStep(new LibrariesDetectionStep(projectBuilder, moduleInsight, icon, "reference.dialogs.new.project.fromCode.page1"));
    sequence.addCommonStep(new ModulesDetectionStep(projectBuilder, moduleInsight, icon, "reference.dialogs.new.project.fromCode.page2"));
    sequence.addCommonStep(factory.createProjectJdkStep(context));

    if (FacetDetectionStep.isEnabled(StdModuleTypes.JAVA)) {
      FacetDetectionStep facetDetectionStep = new FacetDetectionStep(icon, StdModuleTypes.JAVA) {
        protected List<ModuleDescriptor> getModuleDescriptors() {
          return projectBuilder.getModules();
        }
      };
      projectBuilder.addConfigurationUpdater(facetDetectionStep);
      sequence.addCommonStep(facetDetectionStep);
    }

    return sequence;
  }

  public boolean isAvailable(WizardContext context) {
    return context.getProject() == null;
  }

  public ProjectBuilder getModuleBuilder() {
    return myProjectBuilder;
  }

  @Nullable
  public JComponent getAdditionalSettings() {
    return null;
  }

  public void onChosen(final boolean enabled) {

  }

  public void dispose() {
    myProjectBuilder = null;
    super.dispose();
  }
}