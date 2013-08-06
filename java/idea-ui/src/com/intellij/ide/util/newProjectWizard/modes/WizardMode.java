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
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class WizardMode implements Disposable {
  public static final ExtensionPointName<WizardMode> MODES = ExtensionPointName.create("com.intellij.wizardMode");

  private StepSequence myStepSequence;

  @NotNull
  public abstract String getDisplayName(final WizardContext context);

  @NotNull
  public abstract String getDescription(final WizardContext context);

  public abstract boolean isAvailable(final WizardContext context);

  @Nullable
  public StepSequence getSteps(@NotNull WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    if (myStepSequence == null) {
      myStepSequence = createSteps(context, modulesProvider);
    }
    return myStepSequence;
  }

  @Nullable
  protected abstract StepSequence createSteps(@NotNull WizardContext context, @NotNull ModulesProvider modulesProvider);

  @Nullable
  public abstract ProjectBuilder getModuleBuilder();

  @Nullable
  public JComponent getAdditionalSettings(WizardContext wizardContext) {
    return null;
  }

  public abstract void onChosen(final boolean enabled);

  protected String getSelectedType() {
    return myStepSequence != null ? myStepSequence.getSelectedType() : null;
  }

  @Override
  public void dispose() {
    myStepSequence = null;
  }

  public String getShortName() {
    return "";
  }

  @Nullable
  public String getFootnote(final WizardContext wizardContext) {
    return null;
  }

  public boolean validate() throws ConfigurationException {
    return true;
  }
}