// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newMetric;

public class JavaLanguageLevelCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "java.project.language.level";
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    Set<MetricEvent> usages = new HashSet<>();

    LanguageLevelProjectExtension instance = LanguageLevelProjectExtension.getInstance(project);
    if (instance != null) {
      Set<LanguageLevel> languageLevels = EnumSet.noneOf(LanguageLevel.class);
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        LanguageLevelModuleExtension moduleExtension = LanguageLevelModuleExtensionImpl.getInstance(module);
        LanguageLevel languageLevel = instance.getLanguageLevel();
        if (moduleExtension != null && moduleExtension.getLanguageLevel() != null) {
          languageLevel = moduleExtension.getLanguageLevel();
        }
        ContainerUtil.addIfNotNull(languageLevels, languageLevel);
      }
      for (LanguageLevel level : languageLevels) {
        FeatureUsageData data = new FeatureUsageData()
          .addData("version", level.toJavaVersion().feature)
          .addData("preview", level.isPreview());
        usages.add(newMetric("PROJECT_LANGUAGE_LEVEL", data));
      }
    }
    return usages;
  }
}
