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
import java.util.List;
import java.util.Map;

public class CreateFromScratchMode extends WizardMode {

  @NonNls private final Map<String, ModuleBuilder> myBuildersMap = new HashMap<String, ModuleBuilder>();

  @Override
  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.scratch.title", context.getPresentationName());
  }

  @Override
  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.scratch.description", ApplicationNamesInfo.getInstance().getFullProductName(), context.getPresentationName());
  }

  @Override
  @Nullable
  protected StepSequence createSteps(@NotNull final WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    for (ModuleBuilder builder : builders) {
      myBuildersMap.put(builder.getBuilderId(), builder);
    }
    myBuildersMap.put(ModuleType.EMPTY.getId(), new EmptyModuleBuilder());

    StepSequence sequence = new StepSequence();
    for (ModuleBuilder builder : builders) {
      sequence.addStepsForBuilder(builder, context, modulesProvider);
    }
    return sequence;
  }

  @Override
  public boolean isAvailable(WizardContext context) {
    return true;
  }

  @Override
  public ModuleBuilder getModuleBuilder() {
    return myBuildersMap.get(getSelectedType());
  }

  @Override
  public void onChosen(final boolean enabled) {

  }

  @Override
  public String getShortName() {
    return "Create from Scratch";
  }

  @Override
  public void dispose() {
    super.dispose();
    myBuildersMap.clear();
  }
}
