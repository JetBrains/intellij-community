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
import com.intellij.util.Function;
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

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.import.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    return ProjectBundle.message("project.new.wizard.import.description", productName, context.getPresentationName(), StringUtil.join(
      Arrays.asList(Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER)),
      new Function<ProjectImportProvider, String>() {
        public String fun(final ProjectImportProvider provider) {
          return provider.getName();
        }
      }, ", "));
  }

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

  public boolean isAvailable(WizardContext context) {
    return Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER).length > 0;
  }

  @Nullable
  public ProjectBuilder getModuleBuilder() {
    return myBuilder;
  }

  public void onChosen(final boolean enabled) {}

  @Override
  public String getShortName() {
    return "Import from External Model";
  }
}