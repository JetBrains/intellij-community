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

package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StepSequence {
  private final List<ModuleWizardStep> myCommonSteps;
  private final List<Pair<ModuleWizardStep, Set<String>>> myCommonFinishingSteps = new ArrayList<>();
  private final MultiMap<String, ModuleWizardStep> mySpecificSteps = new MultiMap<>();
  @NonNls private List<String> myTypes = new ArrayList<>();
  private List<ModuleWizardStep> mySelectedSteps;

  public StepSequence(ModuleWizardStep... commonSteps) {
    myCommonSteps = new SmartList<>(commonSteps);
  }

  public void addCommonStep(@NotNull ModuleWizardStep step){
    myCommonSteps.add(step);
  }

  public void addCommonFinishingStep(@NotNull ModuleWizardStep step, @Nullable Set<String> suitableTypes) {
    myCommonFinishingSteps.add(Pair.create(step, suitableTypes));
  }

  public void addStepsForBuilder(@NotNull AbstractModuleBuilder builder,
                                 @NotNull WizardContext wizardContext,
                                 @NotNull ModulesProvider modulesProvider) {
    String id = builder.getBuilderId();
    if (!mySpecificSteps.containsKey(id)) {
      mySpecificSteps.put(id, Arrays.asList(builder.createWizardSteps(wizardContext, modulesProvider)));
    }
  }

  public void addSpecificStep(String type, ModuleWizardStep step) {
    mySpecificSteps.putValue(type, step);
  }

  public List<ModuleWizardStep> getSelectedSteps() {
    if (mySelectedSteps == null) {
      mySelectedSteps = new ArrayList<>();
      mySelectedSteps.addAll(myCommonSteps);
      for (String type : myTypes) {
        Collection<ModuleWizardStep> steps = mySpecificSteps.get(type);
        mySelectedSteps.addAll(steps);
      }
      for (Pair<ModuleWizardStep, Set<String>> pair : myCommonFinishingSteps) {
        Set<String> types = pair.getSecond();
        if (types == null || ContainerUtil.intersects(myTypes, types)) {
          mySelectedSteps.add(pair.getFirst());
        }
      }
      ContainerUtil.removeDuplicates(mySelectedSteps);
    }

    return mySelectedSteps;
  }

  @Nullable
  public ModuleWizardStep getNextStep(ModuleWizardStep step) {
    final List<ModuleWizardStep> steps = getSelectedSteps();
    final int i = steps.indexOf(step);
    return i < steps.size() - 1 ? steps.get(i + 1) : null;
  }

  @Nullable
  public ModuleWizardStep getPreviousStep(ModuleWizardStep step) {
    final List<ModuleWizardStep> steps = getSelectedSteps();
    final int i = steps.indexOf(step);
    return i > 0 ? steps.get(i - 1) : null;
  }

  public void setTypes(Collection<String> types) {
    myTypes.clear();
    myTypes.addAll(types);
    mySelectedSteps = null;
  }

  public void setType(@Nullable @NonNls final String type) {
    setTypes(Collections.singletonList(type == null ? ModuleType.EMPTY.getId() : type));
  }

  public String getSelectedType() {
    return ContainerUtil.getFirstItem(myTypes);
  }

  public List<ModuleWizardStep> getAllSteps() {
    final List<ModuleWizardStep> result = new ArrayList<>();
    result.addAll(myCommonSteps);
    result.addAll(mySpecificSteps.values());
    for (Pair<ModuleWizardStep, Set<String>> pair : myCommonFinishingSteps) {
      result.add(pair.getFirst());
    }
    ContainerUtil.removeDuplicates(result);
    return result;
  }
}
