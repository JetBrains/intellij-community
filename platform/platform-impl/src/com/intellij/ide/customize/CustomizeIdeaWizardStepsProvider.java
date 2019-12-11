// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

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

    PluginGroups groups = new PluginGroups() {
      @Override
      protected void initFeaturedPlugins(@NotNull Map<String, String> featuredPlugins) {
        featuredPlugins.put("Scala", "Custom Languages:Plugin for Scala language support:org.intellij.scala");
        if (PlatformUtils.isIdeaUltimate()) {
          featuredPlugins.put("Node JS", "Web Development:Support for Node.js projects:NodeJS");
        }
        featuredPlugins.put("Grazie", "Spellcheck:Intelligent spelling and grammar checks:tanvd.grazi");
        addAwsPlugin(featuredPlugins);
        featuredPlugins.put("IntelliJ Light Theme", "Themes:IntelliJ Light is a new light theme for IntelliJ-based IDEs:com.jetbrains.lightThemePreview");
        featuredPlugins.put("Big Data Tools", "Tools Integration:Zeppelin and Spark support:com.intellij.bigdatatools");
        featuredPlugins.put("EduTools", "Code tools:Learn and teach programming languages such as Kotlin, Java, and Python in the form of coding tasks and custom verification tests:com.jetbrains.edu");
        featuredPlugins.put("Key Promoter X", "Code tools:Helps you to learn essential shortcuts while you are working:Key Promoter X");
        addTrainingPlugin(featuredPlugins);
      }
    };
    steps.add(new CustomizePluginsStepPanel(groups));
    steps.add(new CustomizeFeaturedPluginsStepPanel(groups));
  }
}