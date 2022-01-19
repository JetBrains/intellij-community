// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryJar;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.libraryJar.LibraryJarUtilKt.findJarVersion;

/**
 * @author Andrey Cheptsov
 */
public class FUSLibraryJarUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "javaLibraryJars";
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    LibraryJarDescriptor[] descriptors = LibraryJarStatisticsService.getInstance().getTechnologyDescriptors();
    Set<MetricEvent> result = new HashSet<>(descriptors.length);

    for (LibraryJarDescriptor descriptor : descriptors) {
      String className = descriptor.myClass;
      if (className == null) continue;

      MetricEvent event = ReadAction.nonBlocking(() -> {
        PsiClass[] psiClasses = JavaPsiFacade.getInstance(project).findClasses(className, ProjectScope.getLibrariesScope(project));
        for (PsiClass psiClass : psiClasses) {
          String version = findJarVersion(psiClass);
          if (StringUtil.isNotEmpty(version)) {
            final FeatureUsageData data = new FeatureUsageData().addVersionByString(version).addData("library", descriptor.myName);
            return new MetricEvent("used.library", data);
          }
        }
        return null;
      }).executeSynchronously();
      ContainerUtil.addIfNotNull(result, event);
    }

    return result;
  }
}