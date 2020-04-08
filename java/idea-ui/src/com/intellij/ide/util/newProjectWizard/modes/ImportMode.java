// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ImportChooserStep;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ImportMode extends WizardMode {
  private ProjectImportBuilder myBuilder;
  private final List<? extends ProjectImportProvider> myProviders;

  public ImportMode() {
    this(ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensionList());
  }
  public ImportMode(List<? extends ProjectImportProvider> providers) {
    myProviders = providers;
  }

  @Override
  @NotNull
  public String getDisplayName(final WizardContext context) {
    return JavaUiBundle.message("project.new.wizard.import.title", context.getPresentationName());
  }

  @Override
  @NotNull
  public String getDescription(final WizardContext context) {
    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    return JavaUiBundle.message("project.new.wizard.import.description", productName, context.getPresentationName(), StringUtil.join(
      ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensionList(),
      provider -> provider.getName(), ", "));
  }

  @Override
  @Nullable
  protected StepSequence createSteps(@NotNull final WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    final StepSequence stepSequence = new StepSequence();
    if (myProviders.size() > 1) {
      stepSequence.addCommonStep(new ImportChooserStep(myProviders, stepSequence, context));
    }
    for (ProjectImportProvider provider : myProviders) {
      provider.addSteps(stepSequence, context, provider.getId());
    }
    if (myProviders.size() == 1) {
      stepSequence.setType(myProviders.get(0).getId());
    }
    return stepSequence;
  }

  @Override
  public boolean isAvailable(WizardContext context) {
    return ProjectImportProvider.PROJECT_IMPORT_PROVIDER.hasAnyExtensions();
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