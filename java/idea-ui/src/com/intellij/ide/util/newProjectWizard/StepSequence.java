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
 * Date: 08-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StepSequence {
  private final List<ModuleWizardStep> myCommonSteps = new ArrayList<ModuleWizardStep>();
  private final Map<String, StepSequence> mySpecificSteps = new HashMap<String, StepSequence>();
  @NonNls private String myType;
  private StepSequence myParentSequence;

  public StepSequence() {
    this(null);
  }

  public StepSequence(final StepSequence stepSequence) {
    myParentSequence = stepSequence;
  }

  public void addCommonStep(@NotNull ModuleWizardStep step){
    myCommonSteps.add(step);
  }

  public void addSpecificSteps(String type, StepSequence sequence){
    mySpecificSteps.put(type, sequence);
  }

  public List<ModuleWizardStep> getCommonSteps() {
    return myCommonSteps;
  }

  public StepSequence getSpecificSteps(String type) {
    return mySpecificSteps.get(type);
  }

  public Set<String> getTypes() {
    return mySpecificSteps.keySet();
  }

  @Nullable
  public ModuleWizardStep getNextStep(ModuleWizardStep step) {
    final StepSequence stepSequence = mySpecificSteps.get(myType);
    if (myCommonSteps.contains(step)) {
      final int idx = myCommonSteps.indexOf(step);
      if (idx < myCommonSteps.size() - 1) {
        return myCommonSteps.get(idx + 1);
      }
      if (stepSequence != null && stepSequence.getCommonSteps().size() > 0) {
        return stepSequence.getCommonSteps().get(0);
      }
    }
    if (stepSequence != null) {
      return stepSequence.getNextStep(step);
    }
    return null;
  }

  @Nullable
  public ModuleWizardStep getPreviousStep(ModuleWizardStep step) {
    if (myCommonSteps.contains(step)) {
      final int idx = myCommonSteps.indexOf(step);
      if (idx > 0) {
        return myCommonSteps.get(idx - 1);
      }
      if (myParentSequence != null) {
        final List<ModuleWizardStep> commonSteps = myParentSequence.getCommonSteps();
        if (!commonSteps.isEmpty()) {
          return commonSteps.get(commonSteps.size() - 1);
        }
      }
    }
    final StepSequence stepSequence = mySpecificSteps.get(myType);
    return stepSequence != null ? stepSequence.getPreviousStep(step) : null;
  }

  public void setType(@NonNls final String type) {
    if (type == null) {
      myType = ModuleType.EMPTY.getId();
    } else {
      myType = type;
    }
  }

  public String getSelectedType() {
    return myType;
  }
}
