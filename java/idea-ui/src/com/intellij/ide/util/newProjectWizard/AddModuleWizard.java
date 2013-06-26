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
 * Date: 09-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromTemplateMode;
import com.intellij.ide.util.newProjectWizard.modes.ImportMode;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class AddModuleWizard extends AbstractWizard<ModuleWizardStep>
{
  private static final String ADD_MODULE_TITLE = IdeBundle.message("title.add.module");
  private static final String NEW_PROJECT_TITLE = IdeBundle.message("title.new.project");
  private final Project myCurrentProject;
  private ProjectImportProvider[] myImportProviders;
  private final ModulesProvider myModulesProvider;
  private WizardContext myWizardContext;
  private WizardMode myWizardMode;

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(@Nullable final Project project, final @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, project);
    myCurrentProject = project;
    myModulesProvider = modulesProvider;
    initModuleWizard(project, defaultPath);
  }

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing proj.
   */
  public AddModuleWizard(Component parent, final Project project, @NotNull ModulesProvider modulesProvider) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, parent);
    myCurrentProject = project;
    myModulesProvider = modulesProvider;
    initModuleWizard(project, null);
  }

  /** Import mode */
  public AddModuleWizard(@Nullable Project project, String filePath, ProjectImportProvider... importProviders) {
    super(getImportWizardTitle(project, importProviders), project);
    myCurrentProject = project;
    myImportProviders = importProviders;
    myModulesProvider = DefaultModulesProvider.createForProject(project);
    initModuleWizard(project, filePath);
  }

  /** Import mode */
  public AddModuleWizard(Project project, Component dialogParent, String filePath, ProjectImportProvider... importProviders) {
    super(getImportWizardTitle(project, importProviders), dialogParent);
    myCurrentProject = project;
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
    myWizardContext = new WizardContext(project);
    if (defaultPath != null) {
      myWizardContext.setProjectFileDirectory(defaultPath);
      myWizardContext.setProjectName(defaultPath.substring(FileUtil.toSystemIndependentName(defaultPath).lastIndexOf("/") + 1));
    }
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
  protected String addStepComponent(Component component) {
    if (component instanceof JComponent) {
      ((JComponent)component).setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 0, 0));
    }
    return super.addStepComponent(component);
  }

  protected void updateStep() {
    if (!mySteps.isEmpty()) {
      getCurrentStepObject().updateStep();
    }
    super.updateStep();
    myIcon.setIcon(null);
  }

  protected void dispose() {
    for (ModuleWizardStep step : mySteps) {
      step.disposeUIResources();
    }
    super.dispose();
  }

  protected final void doOKAction() {
    int idx = getCurrentStep();
    try {
      do {
        final ModuleWizardStep step = mySteps.get(idx);
        if (step != getCurrentStepObject()) {
          step.updateStep();
        }
        if (!commitStepData(step)) {
          return;
        }
        step.onStepLeaving();
        try {
          step._commit(true);
        }
        catch (CommitStepException e) {
          String message = e.getMessage();
          if (message != null) {
            Messages.showErrorDialog(getCurrentStepComponent(), message);
          }
          return;
        }
        if (!isLastStep(idx)) {
          idx = getNextStep(idx);
        } else {
          break;
        }
      } while (true);
    }
    finally {
      myCurrentStep = idx;
      updateStep();
    }
    super.doOKAction();
  }

  protected boolean commitStepData(final ModuleWizardStep step) {
    try {
      if (!step.validate()) {
        return false;
      }
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(myCurrentProject, e.getMessage(), e.getTitle());
      return false;
    }
    step.updateDataModel();
    return true;
  }

  public void doNextAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    if (!commitStepData(step)) {
      return;
    }
    step.onStepLeaving();
    super.doNextAction();
  }

  protected void doPreviousAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    step.onStepLeaving();
    super.doPreviousAction();
  }

  public void doCancelAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    step.onStepLeaving();
    super.doCancelAction();
  }

  private boolean isLastStep(int step) {
    return getNextStep(step) == step;
  }


  protected String getHelpID() {
    ModuleWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  protected final int getNextStep(final int step) {
    ModuleWizardStep nextStep = null;
    final StepSequence stepSequence = getSequence();
    if (stepSequence != null) {
      ModuleWizardStep current = mySteps.get(step);
      nextStep = stepSequence.getNextStep(current);
      while (nextStep != null && !nextStep.isStepVisible()) {
        nextStep = stepSequence.getNextStep(nextStep);
      }
    }
    return nextStep == null ? step : mySteps.indexOf(nextStep);
  }

  public StepSequence getSequence() {
    return getMode().getSteps(myWizardContext, myModulesProvider);
  }

  protected final int getPreviousStep(final int step) {
      ModuleWizardStep previousStep = null;
      final StepSequence stepSequence = getSequence();
      if (stepSequence != null) {
        previousStep = stepSequence.getPreviousStep(mySteps.get(step));
        while (previousStep != null && !previousStep.isStepVisible()) {
          previousStep = stepSequence.getPreviousStep(previousStep);
        }
      }
      return previousStep == null ? 0 : mySteps.indexOf(previousStep);
  }

  private WizardMode getMode() {
    return myWizardMode;
  }

  @NotNull
  public String getNewProjectFilePath() {
    if (myWizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT) {
      return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ProjectFileType.DOT_DEFAULT_EXTENSION;
    }
    else {
      return myWizardContext.getProjectFileDirectory();
    }
  }

  @NotNull
  public StorageScheme getStorageScheme() {
    return myWizardContext.getProjectStorageFormat();
  }

  @Nullable
  public static Sdk getNewProjectJdk(WizardContext context) {
    if (context.getProjectJdk() != null) {
      return context.getProjectJdk();
    }
    return getProjectSdkByDefault(context);
  }

  public static Sdk getProjectSdkByDefault(WizardContext context) {
    final Project project = context.getProject() == null ? ProjectManager.getInstance().getDefaultProject() : context.getProject();
    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectJdk != null) {
      return projectJdk;
    }
    return null;
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

  @Nullable
  public Sdk getNewProjectJdk() {
    return getNewProjectJdk(myWizardContext);
  }

  @NotNull
  public String getNewCompileOutput() {
    final String projectFilePath = myWizardContext.getProjectFileDirectory();
    @NonNls String path = myWizardContext.getCompilerOutputDirectory();
    if (path == null) {
      path = StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "out" : projectFilePath + "/out";
    }
    return path;
  }

  @NonNls
  public String getModuleFilePath() {
    return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ModuleFileType.DOT_DEFAULT_EXTENSION;
  }

  public ProjectBuilder getProjectBuilder() {
    return myWizardContext.getProjectBuilder();
  }

  public String getProjectName() {
    return myWizardContext.getProjectName();
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

  public ProjectImportProvider[] getImportProviders() {
    return myImportProviders;
  }

  @TestOnly
  public void doOk() {
    doOKAction();
  }

  @TestOnly
  public boolean isLast() {
    return isLastStep();
  }

  @TestOnly
  public void commit() {
    commitStepData(getCurrentStepObject());
  }
}
