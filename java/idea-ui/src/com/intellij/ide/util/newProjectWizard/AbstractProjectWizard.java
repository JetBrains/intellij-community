// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.newProjectWizard;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.projectWizard.NewProjectWizardCollector;
import com.intellij.ide.projectWizard.ProjectSettingsStep;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.templates.TemplateModuleBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Paths;

/**
 * @author Dmitry Avdeev
 */
public abstract class AbstractProjectWizard extends AbstractWizard<ModuleWizardStep> {
  protected final WizardContext myWizardContext;
  @Nullable
  private WizardDelegate myDelegate;

  public AbstractProjectWizard(@Nls String title, @Nullable Project project, String defaultPath) {
    super(title, project);
    myWizardContext = initContext(project, defaultPath, getDisposable());
    myWizardContext.putUserData(AbstractWizard.KEY, this);
  }

  public AbstractProjectWizard(@NlsContexts.DialogTitle String title, @Nullable Project project, Component dialogParent) {
    super(title, dialogParent);
    myWizardContext = initContext(project, null, getDisposable());
    myWizardContext.putUserData(AbstractWizard.KEY, this);
  }

  @Override
  protected String addStepComponent(@NotNull Component component) {
    if (component instanceof JComponent) {
      ((JComponent)component).setBorder(JBUI.Borders.empty());
    }
    return super.addStepComponent(component);
  }

  public abstract StepSequence getSequence();

  private static WizardContext initContext(@Nullable Project project, @Nullable String defaultPath, Disposable parentDisposable) {
    WizardContext context = new WizardContext(project, parentDisposable);
    if (defaultPath == null && project != null) {
      defaultPath = project.getBasePath();
    }
    if (defaultPath != null) {
      context.setProjectFileDirectory(Paths.get(defaultPath), true);
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

  @Nullable
  public ProjectBuilder getBuilder(Project project) {
    final ProjectBuilder builder = getProjectBuilder();
    if (builder instanceof ModuleBuilder) {
      final ModuleBuilder moduleBuilder = (ModuleBuilder)builder;
      if (moduleBuilder.getName() == null) {
        moduleBuilder.setName(getProjectName());
      }
      if (moduleBuilder.getModuleFilePath() == null) {
        moduleBuilder.setModuleFilePath(getModuleFilePath());
      }
    }
    if (builder == null || !builder.validate(project, project)) {
      return null;
    }
    return builder;
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
    if (isNewWizard()) {
      NewProjectWizardCollector.logNext(myWizardContext, -1);
      NewProjectWizardCollector.logScreen(myWizardContext, 2);
    }
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
    if (isNewWizard()) {
      NewProjectWizardCollector.logPrev(myWizardContext, -1);
    }
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

  @Override
  protected boolean isLastStep() {
    return isLastStep(getCurrentStep());
  }

  private boolean isLastStep(int step) {
    if (AbstractWizard.isNewWizard()
        && mySteps.get(getNextStep(step)) instanceof ProjectSettingsStep
        && myWizardContext.getProjectBuilder() instanceof TemplateModuleBuilder) {
      return true;
    }
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

  @NotNull
  public WizardContext getWizardContext() {
    return myWizardContext;
  }

  @Override
  protected void doHelpAction() {
    super.doHelpAction();
    if (isNewWizard()) {
      NewProjectWizardCollector.logHelpNavigation(myWizardContext);
    }
  }
}
