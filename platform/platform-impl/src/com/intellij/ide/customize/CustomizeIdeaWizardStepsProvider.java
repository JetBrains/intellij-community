// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

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
      protected void initFeaturedPlugins(@NotNull Map<String, String> featuredPlugins) {
        featuredPlugins.put("Scala", "Custom Languages:Scala language support:org.intellij.scala");
        featuredPlugins.put("IntelliJ Light Theme", "Themes:A new light theme for IntelliJ-based IDEs:com.jetbrains.lightThemePreview");
        addAwsPlugin(featuredPlugins);
        featuredPlugins.put("Kubernetes", "Cloud Support:Kubernetes resource files support:com.intellij.kubernetes");
        addBigDataToolsPlugin(featuredPlugins);
        featuredPlugins.put("EduTools", "Code tools:Learn and teach programming languages such as Kotlin, Java, and Python in the form of coding tasks and custom verification tests:com.jetbrains.edu");
        featuredPlugins.put("Key Promoter X", "Code tools:Learn the IntelliJ IDEA shortcuts:Key Promoter X");
        addTrainingPlugin(featuredPlugins);
        if (PlatformUtils.isIdeaCommunity()) {
          featuredPlugins.put("Gradianto", "Theme:Colorful and bright theme for IntelliJ-based IDEs:com.thvardhan.gradianto");
          featuredPlugins.put("File Watchers", "Build:Automatically run tasks when you change or save files in the IDE:com.intellij.plugins.watcher");
        }
      }
    };
    steps.add(new CustomizePluginsStepPanel(groups));
    steps.add(new CustomizeFeaturedPluginsStepPanel(groups));
  }
}