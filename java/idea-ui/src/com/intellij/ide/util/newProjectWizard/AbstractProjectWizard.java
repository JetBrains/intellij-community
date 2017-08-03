/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepWithSubSteps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 19.09.13
 */
public abstract class AbstractProjectWizard extends AbstractWizard<ModuleWizardStep> {
  protected final WizardContext myWizardContext;
  @Nullable
  private WizardDelegate myDelegate;

  public AbstractProjectWizard(String title, Project project, String defaultPath) {
    super(title, project);
    myWizardContext = initContext(project, defaultPath, getDisposable());
    myWizardContext.setWizard(this);
  }

  public AbstractProjectWizard(String title, Project project, Component dialogParent) {
    super(title, dialogParent);
    myWizardContext = initContext(project, null, getDisposable());
    myWizardContext.setWizard(this);
  }

  @Override
  protected String addStepComponent(Component component) {
    if (component instanceof JComponent) {
      ((JComponent)component).setBorder(JBUI.Borders.empty());
    }
    return super.addStepComponent(component);
  }

  public abstract StepSequence getSequence();

  private static WizardContext initContext(@Nullable Project project, @Nullable String defaultPath, Disposable parentDisposable) {
    WizardContext context = new WizardContext(project, parentDisposable);
    if (defaultPath != null) {
      context.setProjectFileDirectory(defaultPath, true);
      context.setProjectName(defaultPath.substring(FileUtil.toSystemIndependentName(defaultPath).lastIndexOf("/") + 1));
    }
   return context;
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

  public ProjectBuilder getProjectBuilder() {
    return myWizardContext.getProjectBuilder();
  }

  public String getProjectName() {
    return myWizardContext.getProjectName();
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

  @Override
  protected void updateStep() {
    if (!mySteps.isEmpty()) {
      getCurrentStepObject().updateStep();
    }
    super.updateStep();
    myIcon.setIcon(null);
  }

  @Override
  protected void dispose() {
    StepSequence sequence = getSequence();
    if (sequence != null) {
      for (ModuleWizardStep step : sequence.getAllSteps()) {
        step.disposeUIResources();
      }
    }
    super.dispose();
  }

  @Override
  protected final void doOKAction() {
    if (!doFinishAction()) return;
    
    super.doOKAction();
  }

  @VisibleForTesting
  public boolean doFinishAction() {
    if (myDelegate != null) {
      myDelegate.doFinishAction();
      return true;
    }
    int idx = getCurrentStep();
    try {
      do {
        final ModuleWizardStep step = mySteps.get(idx);
        if (step != getCurrentStepObject()) {
          step.updateStep();
        }
        if (!commitStepData(step)) {
          return false;
        }
        step.onStepLeaving();
        try {
          step._commit(true);
        }
        catch (CommitStepException e) {
          handleCommitException(e);
          return false;
        }
        if (!isLastStep(idx)) {
          idx = getNextStep(idx);
        }
        else {
          for (ModuleWizardStep wizardStep : mySteps) {
            try {
              wizardStep.onWizardFinished();
            }
            catch (CommitStepException e) {
              handleCommitException(e);
              return false;
            }
          }
          break;
        }
      } while (true);
    }
    finally {
      myCurrentStep = idx;
      updateStep();
    }
    return true;
  }

  private void handleCommitException(CommitStepException e) {
    String message = e.getMessage();
    if (message != null) {
      Messages.showErrorDialog(getCurrentStepComponent(), message);
    }
  }

  protected boolean commitStepData(final ModuleWizardStep step) {
    try {
      if (!step.validate()) {
        return false;
      }
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(myContentPanel, e.getMessage(), e.getTitle());
      return false;
    }
    step.updateDataModel();
    return true;
  }

  @Override
  public void doNextAction() {
    if (myDelegate != null) {
      myDelegate.doNextAction();
      return;
    }
    final ModuleWizardStep step = getCurrentStepObject();
    if (!commitStepData(step)) {
      return;
    }
    step.onStepLeaving();
    super.doNextAction();
  }


  @Override
  protected String getHelpID() {
    ModuleWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  @TestOnly
  public boolean isLast() {
    return isLastStep();
  }

  @NonNls
  public String getModuleFilePath() {
    return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ModuleFileType.DOT_DEFAULT_EXTENSION;
  }

  @Override
  protected void doPreviousAction() {
    if (myDelegate != null) {
      myDelegate.doPreviousAction();
      return;
    }
    final ModuleWizardStep step = getCurrentStepObject();
    step.onStepLeaving();
    if (step instanceof StepWithSubSteps) {
      ((StepWithSubSteps)step).doPreviousAction();
    }
    super.doPreviousAction();
  }

  @Override
  protected boolean canGoNext() {
    return myDelegate != null ? myDelegate.canProceed() : super.canGoNext();
  }

  @Override
  public void doCancelAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    step.onStepLeaving();
    super.doCancelAction();
  }

  protected boolean isLastStep() {
    return isLastStep(getCurrentStep());
  }

  private boolean isLastStep(int step) {
    return getNextStep(step) == step && !isStepWithNotCompletedSubSteps(mySteps.get(step));
  }

  @Override
  protected final int getNextStep(final int step) {
    ModuleWizardStep nextStep = null;
    final StepSequence stepSequence = getSequence();
    if (stepSequence != null) {
      ModuleWizardStep current = mySteps.get(step);
      if (isStepWithNotCompletedSubSteps(current)) {
        return step;
      }

      nextStep = stepSequence.getNextStep(current);
      while (nextStep != null && !nextStep.isStepVisible()) {
        nextStep = stepSequence.getNextStep(nextStep);
      }
    }
    return nextStep == null ? step : mySteps.indexOf(nextStep);
  }

  @Override
  protected final int getPreviousStep(final int step) {
      ModuleWizardStep previousStep = null;
      final StepSequence stepSequence = getSequence();
      if (stepSequence != null) {
        final ModuleWizardStep current = mySteps.get(step);
        if (isNotFirstSubStepInStep(current)) {
          return step;
        }

        previousStep = stepSequence.getPreviousStep(current);
        while (previousStep != null && !previousStep.isStepVisible()) {
          previousStep = stepSequence.getPreviousStep(previousStep);
        }
      }
      return previousStep == null ? 0 : mySteps.indexOf(previousStep);
  }

  private static boolean isStepWithNotCompletedSubSteps(ModuleWizardStep current) {
    return current instanceof StepWithSubSteps && !((StepWithSubSteps)current).isLast();
  }

  private static boolean isNotFirstSubStepInStep(ModuleWizardStep current) {
    return current instanceof StepWithSubSteps && !((StepWithSubSteps)current).isFirst();
  }

  public void setDelegate(@Nullable WizardDelegate delegate) {
    myDelegate = delegate;
  }
}
