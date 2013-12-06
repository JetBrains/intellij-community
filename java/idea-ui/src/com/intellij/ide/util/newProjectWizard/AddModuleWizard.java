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

/*
 * User: anna
 * Date: 09-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromTemplateMode;
import com.intellij.ide.util.newProjectWizard.modes.ImportMode;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;

public class AddModuleWizard extends AbstractProjectWizard {
  private static final String ADD_MODULE_TITLE = IdeBundle.message("title.add.module");
  private static final String NEW_PROJECT_TITLE = IdeBundle.message("title.new.project");
  private ProjectImportProvider[] myImportProviders;
  private final ModulesProvider myModulesProvider;
  private WizardMode myWizardMode;

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(@Nullable final Project project, final @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, project, defaultPath);
    myModulesProvider = modulesProvider;
    initModuleWizard(project, defaultPath);
  }

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(Component parent, final Project project, @NotNull ModulesProvider modulesProvider) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, project, parent);
    myModulesProvider = modulesProvider;
    initModuleWizard(project, null);
  }

  /** Import mode */
  public AddModuleWizard(@Nullable Project project, String filePath, ProjectImportProvider... importProviders) {
    super(getImportWizardTitle(project, importProviders), project, filePath);
    myImportProviders = importProviders;
    myModulesProvider = DefaultModulesProvider.createForProject(project);
    initModuleWizard(project, filePath);
  }

  /** Import mode */
  public AddModuleWizard(Project project, Component dialogParent, String filePath, ProjectImportProvider... importProviders) {
    super(getImportWizardTitle(project, importProviders), project, dialogParent);
    myImportProviders = importProviders;
    myModulesProvider = DefaultModulesProvider.createForProject(project);
    initModuleWizard(project, filePath);
  }

  private static String getImportWizardTitle(Project project, ProjectImportProvider... providers) {
    StringBuilder builder = new StringBuilder("Import ");
    builder.append(project == null ? "Project" : "Module");
    if (providers.length == 1) {
      builder.append(" from ").append(providers[0].getName());
    }
    return builder.toString();
  }

  private void initModuleWizard(@Nullable final Project project, @Nullable final String defaultPath) {
    myWizardContext.addContextListener(new WizardContext.Listener() {
      public void buttonsUpdateRequested() {
        updateButtons();
      }

      public void nextStepRequested() {
        doNextAction();
      }
    });

    if (myImportProviders == null) {
      myWizardMode = new CreateFromTemplateMode();
      appendSteps(myWizardMode.getSteps(myWizardContext, myModulesProvider));
    }
    else {
      myWizardMode = new ImportMode(myImportProviders);
      StepSequence sequence = myWizardMode.getSteps(myWizardContext, DefaultModulesProvider.createForProject(project));
      appendSteps(sequence);
      for (ProjectImportProvider provider : myImportProviders) {
        provider.getBuilder().setFileToImport(defaultPath);
      }
      if (myImportProviders.length == 1) {
        final ProjectImportBuilder builder = myImportProviders[0].getBuilder();
        myWizardContext.setProjectBuilder(builder);
        builder.setUpdate(getWizardContext().getProject() != null);
      }
    }
    init();
  }

  private void appendSteps(@Nullable final StepSequence sequence) {
    if (sequence != null) {
      for (ModuleWizardStep step : sequence.getAllSteps()) {
        addStep(step);
      }
    }
  }

  @Override
  public StepSequence getSequence() {
    return myWizardMode.getSteps(myWizardContext, myModulesProvider);
  }

  @Nullable
  public static Sdk getMostRecentSuitableSdk(final WizardContext context) {
    if (context.getProject() == null) {
      @Nullable final ProjectBuilder projectBuilder = context.getProjectBuilder();
      return ProjectJdkTable.getInstance().findMostRecentSdk(new Condition<Sdk>() {
        public boolean value(Sdk sdk) {
          return projectBuilder == null || projectBuilder.isSuitableSdkType(sdk.getSdkType());
        }
      });
    }
    return null;
  }

  @NotNull
  public WizardContext getWizardContext() {
    return myWizardContext;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "NewModule_or_Project.wizard";
  }

  /**
   * Allows to ask current wizard to move to the desired step.
   *
   * @param filter  closure that allows to indicate target step - is called with each of registered steps and is expected
   *                to return <code>true</code> for the step to go to
   * @return        <code>true</code> if current wizard is navigated to the target step; <code>false</code> otherwise
   */
  public boolean navigateToStep(@NotNull Function<Step, Boolean> filter) {
    for (int i = 0, myStepsSize = mySteps.size(); i < myStepsSize; i++) {
      ModuleWizardStep step = mySteps.get(i);
      if (filter.fun(step) != Boolean.TRUE) {
        continue;
      }

      // Update current step.
      myCurrentStep = i;
      updateStep();
      return true;
    }
    return false;
  }

  @TestOnly
  public void commit() {
    commitStepData(getCurrentStepObject());
  }
}
