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
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AddModuleWizard extends AbstractWizard<ModuleWizardStep> {
  private static final String ADD_MODULE_TITLE = IdeBundle.message("title.add.module");
  private static final String NEW_PROJECT_TITLE = IdeBundle.message("title.new.project");
  private final Project myCurrentProject;
  private WizardContext myWizardContext;
  private ProjectCreateModeStep myRootStep;


  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing proj.
   */
  public AddModuleWizard(final Project project, final ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, project);
    myCurrentProject = project;
    initModuleWizard(project, modulesProvider, defaultPath);
  }

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing proj.
   */
  public AddModuleWizard(Component parent, final Project project, ModulesProvider modulesProvider) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, parent);
    myCurrentProject = project;
    initModuleWizard(project, modulesProvider, null);
  }

  private void initModuleWizard(final Project project, final ModulesProvider modulesProvider, @Nullable final String defaultPath) {
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

    myRootStep = new ProjectCreateModeStep(defaultPath, myWizardContext){
      protected void update() {
        updateButtons();
      }
    };
    addStep(myRootStep);
    for (WizardMode mode : myRootStep.getModes()) {
      appendSteps(mode.getSteps(myWizardContext, modulesProvider));
    }
    init();
  }

  private void appendSteps(@Nullable final StepSequence sequence) {
    if (sequence != null) {
      final List<ModuleWizardStep> commonSteps = sequence.getCommonSteps();
      for (ModuleWizardStep step : commonSteps) {
        addStep(step);
      }
      for (String type : sequence.getTypes()) {
        appendSteps(sequence.getSpecificSteps(type));
      }
    }
  }


  protected void updateStep() {
    final ModuleWizardStep currentStep = getCurrentStepObject();
    currentStep.updateStep();

    super.updateStep();

    updateButtons();

    final JButton nextButton = getNextButton();
    final JButton finishButton = getFinishButton();
    final boolean isLastStep = isLastStep(getCurrentStep());

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (!isShowing()) {
          return;
        }
        final JComponent preferredFocusedComponent = currentStep.getPreferredFocusedComponent();
        if (preferredFocusedComponent != null) {
          preferredFocusedComponent.requestFocus();
        }
        else {
          if (isLastStep) {
            finishButton.requestFocus();
          }
          else {
            nextButton.requestFocus();
          }
        }
        getRootPane().setDefaultButton(isLastStep ? finishButton : nextButton);
      }
    });
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

  private boolean commitStepData(final ModuleWizardStep step) {
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

  protected void doNextAction() {
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

  private void updateButtons() {
    final boolean isLastStep = isLastStep(getCurrentStep());
    getNextButton().setEnabled(!isLastStep);
    getFinishButton().setEnabled(isLastStep);
    getRootPane().setDefaultButton(isLastStep ? getFinishButton() : getNextButton());
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

  protected final int getNextStep(int step) {
    ModuleWizardStep nextStep = null;
    final StepSequence stepSequence = getMode().getSteps(myWizardContext, null);
    if (stepSequence != null) {
      if (myRootStep == mySteps.get(step)) {
        return mySteps.indexOf(stepSequence.getCommonSteps().get(0));
      }
      nextStep = stepSequence.getNextStep(mySteps.get(step));
      while (nextStep != null && !nextStep.isStepVisible()) {
        nextStep = stepSequence.getNextStep(nextStep);
      }
    }
    return nextStep == null ? step : mySteps.indexOf(nextStep);
  }

  protected final int getPreviousStep(final int step) {
    ModuleWizardStep previousStep = null;
    final StepSequence stepSequence = getMode().getSteps(myWizardContext, null);
    if (stepSequence != null) {
      previousStep = stepSequence.getPreviousStep(mySteps.get(step));
      while (previousStep != null && !previousStep.isStepVisible()) {
        previousStep = stepSequence.getPreviousStep(previousStep);
      }
    }
    return previousStep == null ? 0 : mySteps.indexOf(previousStep);
  }

  private WizardMode getMode() {
    return myRootStep.getMode();
  }

  @NotNull
  public String getNewProjectFilePath() {
    if (myWizardContext.getProjectStorageFormat() == StorageScheme.DEFAULT) {
      return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ProjectFileType.DOT_DEFAULT_EXTENSION;
    }
    else {
      new File(myWizardContext.getProjectFileDirectory() + File.separator + ".idea").mkdirs();
      return myWizardContext.getProjectFileDirectory();
    }
  }

  @Nullable
  public static Sdk getNewProjectJdk(WizardContext context) {
    if (context.getProjectJdk() != null) {
      return context.getProjectJdk();
    }
    final Project project = context.getProject() == null ? ProjectManager.getInstance().getDefaultProject() : context.getProject();
    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    if (projectJdk != null) {
      return projectJdk;
    }
    if (context.getProject() == null) {
      @Nullable final ProjectBuilder projectBuilder = context.getProjectBuilder();
      return ProjectJdkTable.getInstance().findMostRecentSdk(new Condition<Sdk>() {
        public boolean value(Sdk sdk) {
          return projectBuilder == null || projectBuilder.isSuitableSdk(sdk);
        }
      });
    }
    return null;
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
}
