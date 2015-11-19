/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.customize;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CustomizeIdeaWizardStepsProvider implements CustomizeIDEWizardStepsProvider {
  @Override
  public void initSteps(@NotNull CustomizeIDEWizardDialog dialog, @NotNull List<AbstractCustomizeWizardStep> steps) {
    steps.add(new CustomizeUIThemeStepPanel());

    if (SystemInfo.isMac) {
      steps.add(new CustomizeMacKeyboardLayoutStep());
    }

    if (CustomizeDesktopEntryStep.isAvailable()) {
      steps.add(new CustomizeDesktopEntryStep("/UbuntuDesktopEntry.png"));
    }

    if (CustomizeLauncherScriptStep.isAvailable()) {
      steps.add(new CustomizeLauncherScriptStep());
    }

    PluginGroups groups = new PluginGroups();
    steps.add(new CustomizePluginsStepPanel(groups));
    steps.add(new CustomizeFeaturedPluginsStepPanel(groups));
  }
}