// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CustomizeIdeaWizardStepsProvider implements CustomizeIDEWizardStepsProvider {
  @Override
  public void initSteps(@NotNull CustomizeIDEWizardDialog dialog, @NotNull List<AbstractCustomizeWizardStep> steps) {
    steps.add(new CustomizeUIThemeStepPanel());

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