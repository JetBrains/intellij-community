// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.projectImport.ImportChooserStep;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

public class AddModuleWizard extends AbstractProjectWizard {

  private final @NotNull List<ProjectImportProvider> myImportProviders;
  private final @NotNull StepSequence myStepSequence;

  /**
   * @param project if null, the wizard will start creating a new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(
    @Nullable Project project,
    @NotNull String filePath,
    ProjectImportProvider... importProviders
  ) {
    super(getImportWizardTitle(project, importProviders), project, filePath);
    myImportProviders = Arrays.asList(importProviders);
    myStepSequence = createStepSequence(myImportProviders, myWizardContext);
    initModuleWizard(filePath);
  }

  /**
   * @param project if null, the wizard will start creating a new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(
    @Nullable Project project,
    @NotNull Component dialogParent,
    @NotNull String filePath,
    ProjectImportProvider... importProviders
  ) {
    super(getImportWizardTitle(project, importProviders), project, dialogParent);
    myImportProviders = Arrays.asList(importProviders);
    myStepSequence = createStepSequence(myImportProviders, myWizardContext);
    initModuleWizard(filePath);
  }

  private static @NlsContexts.DialogTitle String getImportWizardTitle(Project project, ProjectImportProvider... providers) {
    int isProject = project == null ? 0 : 1;
    if (providers.length != 1) {
      return JavaUiBundle.message("module.wizard.dialog.title", isProject, 0, null);
    }
    return JavaUiBundle.message("module.wizard.dialog.title", isProject, 1, providers[0].getName());
  }

  private void initModuleWizard(@NotNull String defaultPath) {
    myWizardContext.addContextListener(new WizardContext.Listener() {
      @Override
      public void buttonsUpdateRequested() {
        updateButtons();
      }

      @Override
      public void nextStepRequested() {
        proceedToNextStep();
      }
    });

    for (ModuleWizardStep step : myStepSequence.getAllSteps()) {
      addStep(step);
    }
    for (ProjectImportProvider provider : myImportProviders) {
      provider.getBuilder().setFileToImport(defaultPath);
    }
    if (myImportProviders.size() == 1) {
      var builder = myImportProviders.get(0).getBuilder();
      myWizardContext.setProjectBuilder(builder);
      builder.setUpdate(getWizardContext().getProject() != null);
    }
    init();
  }

  private static @NotNull StepSequence createStepSequence(
    @NotNull List<ProjectImportProvider> importProviders,
    @NotNull WizardContext context
  ) {
    var stepSequence = new StepSequence();
    if (importProviders.size() > 1) {
      stepSequence.addCommonStep(new ImportChooserStep(importProviders, stepSequence, context));
    }
    for (ProjectImportProvider provider : importProviders) {
      provider.addSteps(stepSequence, context, provider.getId());
    }
    if (importProviders.size() == 1) {
      stepSequence.setType(importProviders.get(0).getId());
    }
    return stepSequence;
  }

  @Override
  public StepSequence getSequence() {
    return myStepSequence;
  }

  public static @Nullable Sdk getMostRecentSuitableSdk(final WizardContext context) {
    if (context.getProject() == null) {
      List<Sdk> sdks = Arrays.asList(ProjectJdkTable.getInstance().getAllJdks());

      ProjectBuilder builder = context.getProjectBuilder();
      if (builder != null) {
        sdks = ContainerUtil.filter(sdks, sdk -> builder.isSuitableSdkType(sdk.getSdkType()));
      }

      Map<SdkTypeId, List<Sdk>> sdksByType = sdks.stream().collect(groupingBy(Sdk::getSdkType, mapping(Function.identity(), toList())));
      Map.Entry<SdkTypeId, List<Sdk>> pair = ContainerUtil.getFirstItem(sdksByType.entrySet());
      if (pair != null) {
        return pair.getValue().stream().max(pair.getKey().versionComparator()).orElse(null);
      }
    }

    return null;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "NewModule_or_Project.wizard";
  }

  @TestOnly
  public void commit() {
    commitStepData(getCurrentStepObject());
  }
}