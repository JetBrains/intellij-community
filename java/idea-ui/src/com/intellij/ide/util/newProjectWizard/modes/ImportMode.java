// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ImportChooserStep;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class ImportMode extends WizardMode {
  private ProjectImportBuilder myBuilder;
  private final ProjectImportProvider[] myProviders;

  public ImportMode() {
    this(Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER));
  }
  public ImportMode(ProjectImportProvider[] providers) {
    myProviders = providers;
  }

  @Override
  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.import.title", context.getPresentationName());
  }

  @Override
  @NotNull
  public String getDescription(final WizardContext context) {
    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    return ProjectBundle.message("project.new.wizard.import.description", productName, context.getPresentationName(), StringUtil.join(
      Arrays.asList(Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER)),
      provider -> provider.getName(), ", "));
  }

  @Override
  @Nullable
  protected StepSequence createSteps(@NotNull final WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    final StepSequence stepSequence = new StepSequence();
    if (myProviders.length > 1) {
      stepSequence.addCommonStep(new ImportChooserStep(myProviders, stepSequence, context));
    }
    for (ProjectImportProvider provider : myProviders) {
      provider.addSteps(stepSequence, context, provider.getId());
    }
    if (myProviders.length == 1) {
      stepSequence.setType(myProviders[0].getId());
    }
    return stepSequence;
  }

  @Override
  public boolean isAvailable(WizardContext context) {
    return Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER).length > 0;
  }

  @Override
  @Nullable
  public ProjectBuilder getModuleBuilder() {
    return myBuilder;
  }

  @Override
  public void onChosen(final boolean enabled) {}

  @Override
  public String getShortName() {
    return "Import from External Model";
  }
}