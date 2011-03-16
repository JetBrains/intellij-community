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
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
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

import javax.swing.*;
import java.util.Arrays;

public class ImportMode extends WizardMode {
  private ProjectImportBuilder myBuilder;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.import.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    return ProjectBundle.message("project.new.wizard.import.description", productName, context.getPresentationName(), StringUtil.join(
      Arrays.asList(Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER)),
      new Function<ProjectImportProvider, String>() {
        public String fun(final ProjectImportProvider provider) {
          return provider.getName();
        }
      }, ", "));
  }

  @Nullable
  protected StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    final StepSequence stepSequence = new StepSequence(null);
    final ProjectImportProvider[] providers = Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER);
    if (providers.length == 1) {
      myBuilder = providers[0].getBuilder();
      myBuilder.setUpdate(context.getProject() != null);

      final ModuleWizardStep[] steps = providers[0].createSteps(context);
      for (ModuleWizardStep step : steps) {
        stepSequence.addCommonStep(step);
      }
    }
    else {
      stepSequence.addCommonStep(new ImportChooserStep(providers, stepSequence, context));
      for (ProjectImportProvider provider : providers) {
        final ModuleWizardStep[] steps = provider.createSteps(context);
        final StepSequence sequence = new StepSequence(stepSequence);
        for (ModuleWizardStep step : steps) {
          sequence.addCommonStep(step);
        }
        stepSequence.addSpecificSteps(provider.getId(), sequence);
      }
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

  @Nullable
  public JComponent getAdditionalSettings() {
    return null;
  }

  public void onChosen(final boolean enabled) {}

}