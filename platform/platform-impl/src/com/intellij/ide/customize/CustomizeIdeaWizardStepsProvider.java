// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CustomizeIdeaWizardStepsProvider implements CustomizeIDEWizardStepsProvider {
  @Override
  public void initSteps(@NotNull CustomizeIDEWizardDialog dialog, @NotNull List<? super AbstractCustomizeWizardStep> steps) {
    steps.add(new CustomizeUIThemeStepPanel());

    if (CustomizeDesktopEntryStep.isAvailable()) {
      steps.add(new CustomizeDesktopEntryStep("/UbuntuDesktopEntry.png"));
    }

    if (CustomizeLauncherScriptStep.isAvailable()) {
      steps.add(new CustomizeLauncherScriptStep());
    }

    PluginGroups groups = new PluginGroups() {

      @Override
      protected @NotNull List<? extends PluginGroupDescription> getInitialFeaturedPlugins() {
        List<PluginGroupDescription> result = new ArrayList<>(List.of(
          PluginGroupDescription.scala(),
          PluginGroupDescription.create("com.jetbrains.lightThemePreview",
                                        "IntelliJ Light Theme",
                                        "Themes",
                                        "A new light theme for IntelliJ-based IDEs"),
          PluginGroupDescription.aws(),
          PluginGroupDescription.create("com.intellij.kubernetes",
                                        "Kubernetes",
                                        "Cloud Support",
                                        "Kubernetes resource files support"),
          PluginGroupDescription.bigDataTools(),
          PluginGroupDescription.create("com.jetbrains.edu",
                                        "EduTools",
                                        "Code tools",
                                        "Learn and teach programming languages such as Kotlin, Java, and Python in the form of coding tasks and custom verification tests"),
          PluginGroupDescription.create("Key Promoter X",
                                        "Key Promoter X",
                                        "Code tools",
                                        "Learn the IntelliJ IDEA shortcuts"),
          PluginGroupDescription.featuresTrainer()));

        if (PlatformUtils.isIdeaCommunity()) {
          result.addAll(List.of(
            PluginGroupDescription.create("com.thvardhan.gradianto",
                                          "Gradianto",
                                          "Theme",
                                          "Colorful and bright theme for IntelliJ-based IDEs"),
            PluginGroupDescription.create("com.intellij.plugins.watcher",
                                          "File Watchers",
                                          "Build",
                                          "Automatically run tasks when you change or save files in the IDE")
          ));
        }
        return result;
      }
    };
    steps.add(new CustomizePluginsStepPanel(groups));
    steps.add(new CustomizeFeaturedPluginsStepPanel(groups));
  }
}