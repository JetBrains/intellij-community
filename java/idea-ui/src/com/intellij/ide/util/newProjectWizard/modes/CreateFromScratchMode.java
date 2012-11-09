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
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CreateFromScratchMode extends WizardMode {

  @NonNls private final Map<String, ModuleBuilder> myBuildersMap = new HashMap<String, ModuleBuilder>();

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.scratch.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.scratch.description", ApplicationNamesInfo.getInstance().getFullProductName(), context.getPresentationName());
  }

  @Nullable
  protected StepSequence createSteps(final WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    ModuleBuilder[] builders = context.getAllBuilders();
    for (ModuleBuilder builder : builders) {
      myBuildersMap.put(builder.getBuilderId(), builder);
    }
    myBuildersMap.put(ModuleType.EMPTY.getId(), new EmptyModuleBuilder());
    return addSteps(context, modulesProvider, new StepSequence(), builders);
  }

  static StepSequence addSteps(WizardContext context,
                               ModulesProvider modulesProvider,
                               StepSequence sequence, ModuleBuilder[] builders) {

    for (ModuleBuilder builder : builders) {
      CreateFromTemplateMode.addStepsForBuilder(builder, context, modulesProvider, sequence);
    }
    return sequence;
  }

  public boolean isAvailable(WizardContext context) {
    return true;
  }

  public ModuleBuilder getModuleBuilder() {
    return myBuildersMap.get(getSelectedType());
  }

  public void onChosen(final boolean enabled) {
    
  }

  @Override
  public String getShortName() {
    return "Create from Scratch";
  }

  public void dispose() {
    super.dispose();
    myBuildersMap.clear();
  }
}
