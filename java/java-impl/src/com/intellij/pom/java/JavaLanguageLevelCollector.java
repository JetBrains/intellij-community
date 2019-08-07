// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

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

    usages.add(newMetric("PROJECT_LANGUAGE_LEVEL", PsiUtil.getLanguageLevel(project)));

    return usages;
  }
}
